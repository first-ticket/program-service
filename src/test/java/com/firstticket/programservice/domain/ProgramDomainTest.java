package com.firstticket.programservice.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.domain.query.ProgramSearchSpec;

/**
 * Program 도메인 단위 테스트.
 *
 * [테스트 규칙]
 * - JUnit5 단위 테스트 (Spring 컨텍스트 없음)
 * - 비즈니스 핵심 로직 검증
 * - 도메인 규칙 위반 케이스 반드시 작성
 * - 동일 결과의 중복 케이스 하나만 작성
 *
 * [엔티티 생성 전략]
 * Program, Schedule은 @NoArgsConstructor(PROTECTED), @AllArgsConstructor(PRIVATE) 구조.
 * 테스트에서는 반드시 정적 팩토리 메서드(Program.create(), program.addSchedule())를 사용한다.
 * Schedule.create()는 package-private이므로 program.addSchedule()을 통해 간접 생성한다.
 */
class ProgramDomainTest {

    // ── 공통 픽스처 ──────────────────────────────────────────────────

    private static final UUID VENUE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SECTION_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    // 충분히 먼 미래 — Schedule 과거 시점 차단 검증 통과용
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 0, 0);
    private static final LocalDateTime FUTURE = LocalDateTime.of(2027, 6, 1, 14, 0);

    /** DRAFT 상태 기본 Program 픽스처 */
    private Program draftProgram() {
        return Program.create("테스트 공연", "CONCERT", "POP",
            ProgramType.SEATED, "서울", null, null);
    }

    /**
     * Schedule 픽스처.
     * program.addSchedule()을 통해서만 생성 가능하므로
     * 인자로 받은 program에 스케줄을 추가하고 반환한다.
     *
     * Schedule.id는 @GeneratedValue(UUID)로 JPA 영속화 시점에 부여된다.
     * 단위 테스트에서는 DB가 없으므로 id가 null인 채로 생성된다.
     * removeSchedule()이 s.getId().equals(scheduleId)로 비교하므로
     * 리플렉션으로 고정 UUID를 주입하여 NPE를 방지한다.
     */
    private static final UUID FIXED_SCHEDULE_ID =
        UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private Schedule addSchedule(Program program) {
        Schedule schedule = program.addSchedule(
            VENUE_ID,
            FUTURE, FUTURE.plusHours(2),
            FUTURE.minusDays(30), FUTURE.minusDays(1),
            500, NOW
        );
        injectScheduleId(schedule);
        return schedule;
    }

    /**
     * 리플렉션으로 Schedule.id에 UUID를 주입한다.
     * 프로덕션 코드 변경 없이 단위 테스트에서 getId() NPE를 방지하기 위한 헬퍼.
     */
    private static void injectScheduleId(Schedule schedule) {
        try {
            java.lang.reflect.Field field = Schedule.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(schedule, ProgramDomainTest.FIXED_SCHEDULE_ID);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Schedule.id 주입 실패", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.create()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.create() — 프로그램 생성")
    class CreateProgram {

        @Test
        @DisplayName("정상 생성 — 초기 상태는 DRAFT")
        void success_initialStatusIsDraft() {
            Program program = draftProgram();

            assertThat(program.getStatus()).isEqualTo(ProgramStatus.DRAFT);
            assertThat(program.getTitle()).isEqualTo("테스트 공연");
            assertThat(program.getSchedules()).isEmpty();
        }

        @Test
        @DisplayName("region 앞뒤 공백은 trim되어 저장된다")
        void success_regionTrimmed() {
            Program program = Program.create("공연", "CONCERT", "POP",
                ProgramType.SEATED, "  서울  ", null, null);

            assertThat(program.getRegion()).isEqualTo("서울");
        }

        @Test
        @DisplayName("제목 null 시 INVALID_TITLE 예외 — 입력값 유효성 오류")
        void fail_nullTitle() {
            assertThatThrownBy(() ->
                Program.create(null, "CONCERT", "POP", ProgramType.SEATED, "서울", null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_TITLE);
        }

        @Test
        @DisplayName("제목 빈 문자열 시 INVALID_TITLE 예외 — null과 동일 결과이므로 대표 케이스만")
        void fail_blankTitle() {
            assertThatThrownBy(() ->
                Program.create("  ", "CONCERT", "POP", ProgramType.SEATED, "서울", null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_TITLE);
        }

        @Test
        @DisplayName("카테고리 null 시 INVALID_CATEGORY 예외")
        void fail_nullCategory() {
            assertThatThrownBy(() ->
                Program.create("공연", null, "POP", ProgramType.SEATED, "서울", null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_CATEGORY);
        }

        @Test
        @DisplayName("테마 null 시 INVALID_THEME 예외")
        void fail_nullTheme() {
            assertThatThrownBy(() ->
                Program.create("공연", "CONCERT", null, ProgramType.SEATED, "서울", null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_THEME);
        }

        @Test
        @DisplayName("타입 null 시 INVALID_PROGRAM_TYPE 예외")
        void fail_nullType() {
            assertThatThrownBy(() ->
                Program.create("공연", "CONCERT", "POP", null, "서울", null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_PROGRAM_TYPE);
        }

        @Test
        @DisplayName("지역 null 시 INVALID_REGION 예외")
        void fail_nullRegion() {
            assertThatThrownBy(() ->
                Program.create("공연", "CONCERT", "POP", ProgramType.SEATED, null, null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_REGION);
        }

        @Test
        @DisplayName("지역 100자 초과 시 INVALID_REGION 예외 — 경계값")
        void fail_regionTooLong() {
            String longRegion = "a".repeat(101);

            assertThatThrownBy(() ->
                Program.create("공연", "CONCERT", "POP", ProgramType.SEATED, longRegion, null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_REGION);
        }

        @Test
        @DisplayName("지역 정확히 100자 시 정상 생성 — 경계값")
        void success_region100Chars() {
            String region100 = "a".repeat(100);

            assertThatCode(() ->
                Program.create("공연", "CONCERT", "POP", ProgramType.SEATED, region100, null, null))
                .doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.addSchedule()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.addSchedule() — 스케줄 추가")
    class AddSchedule {

        @Test
        @DisplayName("DRAFT 상태에서 스케줄 추가 성공")
        void success_draft() {
            Program program = draftProgram();
            addSchedule(program);

            assertThat(program.getSchedules()).hasSize(1);
        }

        @Test
        @DisplayName("ON_SALE 상태에서도 스케줄 추가 가능")
        void success_onSale() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            // ON_SALE 상태에서 추가 스케줄 등록 가능
            addSchedule(program);

            assertThat(program.getSchedules()).hasSize(2);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 스케줄 추가 시 PROGRAM_NOT_EDITABLE 예외 — 도메인 규칙 위반")
        void fail_cancelled() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();
            program.cancel();

            assertThatThrownBy(() -> addSchedule(program))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }

        @Test
        @DisplayName("CLOSED 상태에서 스케줄 추가 시 PROGRAM_NOT_EDITABLE 예외 — 도메인 규칙 위반")
        void fail_closed() {
            Program program = draftProgram();
            // 과거 시점 스케줄 등록 후 강제 close 테스트
            // close()는 모든 스케줄 eventEndAt < currentTime을 요구하므로
            // currentTime을 충분히 미래로 지정하여 검증
            LocalDateTime past = LocalDateTime.of(2020, 1, 1, 14, 0);
            LocalDateTime pastNow = LocalDateTime.of(2019, 1, 1, 0, 0);
            program.addSchedule(VENUE_ID,
                past, past.plusHours(2),
                past.minusDays(30), past.minusDays(1),
                500, pastNow);
            // schedules에 가격등급 없이 publish 불가 → DRAFT에서 close 우회하여 직접 상태 변경 불가
            // 대신 CANCELLED 케이스로 이미 검증됨 — CLOSED 케이스는 cancel()로 대체 확인
            // close()를 직접 호출하려면 past 시점 스케줄 + 현재가 과거보다 이후여야 함
            LocalDateTime futureNow = LocalDateTime.of(2025, 1, 1, 0, 0);

            // publish를 위해 priceGrade 추가
            Schedule s = program.getSchedules().get(0);
            s.addPriceGrade(SECTION_ID, "VIP", 0, pastNow);
            program.publish();
            program.close(futureNow);

            assertThatThrownBy(() -> addSchedule(program))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }

        @Test
        @DisplayName("공연 종료 시각이 시작 시각보다 이전이면 INVALID_EVENT_PERIOD 예외 — 도메인 규칙 위반")
        void fail_eventEndBeforeStart() {
            Program program = draftProgram();

            assertThatThrownBy(() ->
                program.addSchedule(VENUE_ID,
                    FUTURE, FUTURE.minusHours(1),          // end < start
                    FUTURE.minusDays(30), FUTURE.minusDays(1),
                    500, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_EVENT_PERIOD);
        }

        @Test
        @DisplayName("판매 종료가 공연 시작보다 이후이면 SALE_END_AFTER_EVENT_START 예외 — 도메인 규칙 위반")
        void fail_saleEndAfterEventStart() {
            Program program = draftProgram();

            assertThatThrownBy(() ->
                program.addSchedule(VENUE_ID,
                    FUTURE, FUTURE.plusHours(2),
                    FUTURE.minusDays(1), FUTURE.plusHours(1), // saleEnd > eventStart
                    500, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SALE_END_AFTER_EVENT_START);
        }

        @Test
        @DisplayName("공연 시작이 현재 시각보다 이전이면 PAST_EVENT_START 예외 — 도메인 규칙 위반")
        void fail_pastEventStart() {
            Program program = draftProgram();
            LocalDateTime pastEvent = LocalDateTime.of(2020, 1, 1, 14, 0);

            assertThatThrownBy(() ->
                program.addSchedule(VENUE_ID,
                    pastEvent, pastEvent.plusHours(2),
                    pastEvent.minusDays(30), pastEvent.minusDays(1),
                    500, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PAST_EVENT_START);
        }

        @Test
        @DisplayName("venueId null 시 INVALID_VENUE_ID 예외")
        void fail_nullVenueId() {
            Program program = draftProgram();

            assertThatThrownBy(() ->
                program.addSchedule(null,
                    FUTURE, FUTURE.plusHours(2),
                    FUTURE.minusDays(30), FUTURE.minusDays(1),
                    500, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_VENUE_ID);
        }

        @Test
        @DisplayName("totalCapacity 0 이하 시 INVALID_CAPACITY 예외 — 경계값")
        void fail_zeroCapacity() {
            Program program = draftProgram();

            assertThatThrownBy(() ->
                program.addSchedule(VENUE_ID,
                    FUTURE, FUTURE.plusHours(2),
                    FUTURE.minusDays(30), FUTURE.minusDays(1),
                    0, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_CAPACITY);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.removeSchedule()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.removeSchedule() — 스케줄 삭제")
    class RemoveSchedule {

        @Test
        @DisplayName("DRAFT 상태에서 스케줄 삭제 성공")
        void success_draft() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            UUID scheduleId = schedule.getId();

            program.removeSchedule(scheduleId);

            assertThat(program.getSchedules()).isEmpty();
        }

        @Test
        @DisplayName("ON_SALE 상태에서 스케줄 삭제 시 SCHEDULE_NOT_DELETABLE 예외 — 도메인 규칙 위반")
        void fail_onSale() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();
            UUID scheduleId = schedule.getId();

            assertThatThrownBy(() -> program.removeSchedule(scheduleId))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SCHEDULE_NOT_DELETABLE);
        }

        @Test
        @DisplayName("존재하지 않는 scheduleId 삭제 시 SCHEDULE_NOT_FOUND 예외 — 외부 의존성 실패")
        void fail_scheduleNotFound() {
            Program program = draftProgram();
            addSchedule(program);
            UUID unknownId = UUID.randomUUID();

            assertThatThrownBy(() -> program.removeSchedule(unknownId))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.publish()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.publish() — DRAFT → ON_SALE 전이")
    class Publish {

        @Test
        @DisplayName("스케줄·가격등급 모두 있을 때 publish 성공")
        void success() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);

            program.publish();

            assertThat(program.getStatus()).isEqualTo(ProgramStatus.ON_SALE);
        }

        @Test
        @DisplayName("스케줄 없이 publish 시 SCHEDULE_REQUIRED 예외 — 도메인 규칙 위반")
        void fail_noSchedule() {
            Program program = draftProgram();

            assertThatThrownBy(program::publish)
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SCHEDULE_REQUIRED);
        }

        @Test
        @DisplayName("가격등급 없는 스케줄이 있을 때 publish 시 PRICE_GRADE_REQUIRED 예외 — 도메인 규칙 위반")
        void fail_scheduleWithoutPriceGrade() {
            Program program = draftProgram();
            addSchedule(program); // 가격등급 추가 없음

            assertThatThrownBy(program::publish)
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PRICE_GRADE_REQUIRED);
        }

        @Test
        @DisplayName("ON_SALE 상태에서 publish 시 INVALID_STATUS_TRANSITION 예외 — 잘못된 상태 전이")
        void fail_alreadyOnSale() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            assertThatThrownBy(program::publish)
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.cancel()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.cancel() — → CANCELLED 전이")
    class Cancel {

        @Test
        @DisplayName("ON_SALE → CANCELLED 전이 성공")
        void success_fromOnSale() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            program.cancel();

            assertThat(program.getStatus()).isEqualTo(ProgramStatus.CANCELLED);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 cancel 시 INVALID_STATUS_TRANSITION 예외 — 복구 불가 상태")
        void fail_alreadyCancelled() {
            Program program = draftProgram(); // DRAFT
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();                // DRAFT → ON_SALE 성공
            program.cancel();

            assertThatThrownBy(program::cancel)
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.close()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.close() — → CLOSED 전이")
    class Close {

        @Test
        @DisplayName("모든 스케줄 종료 후 close 성공")
        void success_allSchedulesEnded() {
            Program program = draftProgram();
            LocalDateTime pastEvent = LocalDateTime.of(2020, 6, 1, 14, 0);
            LocalDateTime pastNow = LocalDateTime.of(2019, 1, 1, 0, 0);

            program.addSchedule(VENUE_ID,
                pastEvent, pastEvent.plusHours(2),
                pastEvent.minusDays(30), pastEvent.minusDays(1),
                500, pastNow);

            Schedule s = program.getSchedules().get(0);
            s.addPriceGrade(SECTION_ID, "VIP", 0, pastNow);
            program.publish();

            LocalDateTime afterEvent = LocalDateTime.of(2025, 1, 1, 0, 0);
            program.close(afterEvent);

            assertThat(program.getStatus()).isEqualTo(ProgramStatus.CLOSED);
        }

        @Test
        @DisplayName("종료되지 않은 스케줄이 있으면 PROGRAM_NOT_ENDED_YET 예외 — 도메인 규칙 위반")
        void fail_scheduleNotEnded() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            // FUTURE 스케줄이 아직 끝나지 않음 — NOW 시점에서 close 시도
            assertThatThrownBy(() -> program.close(NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_NOT_ENDED_YET);
        }

        @Test
        @DisplayName("DRAFT 상태에서 close 시 INVALID_STATUS_TRANSITION 예외 — 잘못된 상태 전이")
        void fail_fromDraft() {
            Program program = draftProgram();

            assertThatThrownBy(() -> program.close(NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.updateDraft()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.updateDraft() — DRAFT 상태 수정")
    class UpdateDraft {

        @Test
        @DisplayName("null 필드는 기존 값을 유지한다")
        void success_nullFieldKeepsOriginal() {
            Program program = draftProgram();

            program.updateDraft(null, null, null, null, "새 설명");

            assertThat(program.getTitle()).isEqualTo("테스트 공연");
            assertThat(program.getDescription()).isEqualTo("새 설명");
        }

        @Test
        @DisplayName("전체 필드 수정 성공")
        void success_allFields() {
            Program program = draftProgram();

            program.updateDraft("새 제목", "MUSICAL", "CLASSIC",
                "https://new.jpg", "새 설명");

            assertThat(program.getTitle()).isEqualTo("새 제목");
            assertThat(program.getCategory()).isEqualTo("MUSICAL");
            assertThat(program.getTheme()).isEqualTo("CLASSIC");
        }

        @Test
        @DisplayName("ON_SALE 상태에서 updateDraft 호출 시 PROGRAM_NOT_EDITABLE 예외 — 도메인 규칙 위반")
        void fail_onSale() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            assertThatThrownBy(() -> program.updateDraft("새 제목", null, null, null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }

        @Test
        @DisplayName("수정 결과 title이 blank가 되면 INVALID_TITLE 예외 — 도메인 규칙 위반")
        void fail_titleBecomesBlank() {
            Program program = draftProgram();

            assertThatThrownBy(() -> program.updateDraft("  ", null, null, null, null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_TITLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Program.updateOnSale()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Program.updateOnSale() — ON_SALE 상태 수정")
    class UpdateOnSale {

        @Test
        @DisplayName("posterUrl, description만 수정 가능")
        void success() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();

            program.updateOnSale("https://new.jpg", "새 설명");

            assertThat(program.getPosterUrl()).isEqualTo("https://new.jpg");
            assertThat(program.getDescription()).isEqualTo("새 설명");
        }

        @Test
        @DisplayName("DRAFT 상태에서 updateOnSale 호출 시 PROGRAM_NOT_EDITABLE 예외 — 도메인 규칙 위반")
        void fail_draft() {
            Program program = draftProgram();

            assertThatThrownBy(() -> program.updateOnSale("https://new.jpg", null))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Schedule.addPriceGrade()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Schedule.addPriceGrade() — 가격 등급 추가")
    class AddPriceGrade {

        @Test
        @DisplayName("SEATED 타입에 sectionId 포함 가격 등급 추가 성공")
        void success_seated() {
            Program program = draftProgram(); // SEATED 타입
            Schedule schedule = addSchedule(program);

            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);

            assertThat(schedule.getPriceGrades()).hasSize(1);
            assertThat(schedule.getPriceGrades().get(0).getGradeLabel()).isEqualTo("VIP");
        }

        @Test
        @DisplayName("FREE 타입에 sectionId null로 가격 등급 추가 성공")
        void success_free() {
            Program program = Program.create("공연", "CONCERT", "POP",
                ProgramType.FREE, "서울", null, null);
            Schedule schedule = addSchedule(program);

            schedule.addPriceGrade(null, "일반", 10_000, NOW);

            assertThat(schedule.getPriceGrades()).hasSize(1);
        }

        @Test
        @DisplayName("음수 가격 시 INVALID_PRICE 예외 — 도메인 규칙 위반")
        void fail_negativePrice() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.addPriceGrade(SECTION_ID, "VIP", -1, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_PRICE);
        }

        @Test
        @DisplayName("0원은 허용 — 무료 공연 가격 — 경계값")
        void success_zeroPrice() {
            Program program = Program.create("공연", "CONCERT", "POP",
                ProgramType.FREE, "서울", null, null);
            Schedule schedule = addSchedule(program);

            assertThatCode(() -> schedule.addPriceGrade(null, "무료", 0, NOW))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("등급명 null 시 INVALID_GRADE_LABEL 예외")
        void fail_nullGradeLabel() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.addPriceGrade(SECTION_ID, null, 50_000, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_GRADE_LABEL);
        }

        @Test
        @DisplayName("SEATED 타입에 sectionId null 시 SECTION_ID_REQUIRED 예외 — 도메인 규칙 위반")
        void fail_seated_nullSectionId() {
            Program program = draftProgram(); // SEATED
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.addPriceGrade(null, "VIP", 50_000, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SECTION_ID_REQUIRED);
        }

        @Test
        @DisplayName("FREE 타입에 sectionId 전달 시 SECTION_ID_NOT_ALLOWED 예외 — 도메인 규칙 위반")
        void fail_free_sectionIdNotAllowed() {
            Program program = Program.create("공연", "CONCERT", "POP",
                ProgramType.FREE, "서울", null, null);
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.addPriceGrade(SECTION_ID, "일반", 10_000, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SECTION_ID_NOT_ALLOWED);
        }

        @Test
        @DisplayName("판매 시작 이후 가격 등급 추가 시 CANNOT_MODIFY_AFTER_SALE_START 예외 — 도메인 규칙 위반")
        void fail_afterSaleStart() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);

            // saleStartAt = FUTURE.minusDays(30) 이후 시점으로 currentTime 설정
            LocalDateTime afterSaleStart = FUTURE.minusDays(10);

            assertThatThrownBy(() -> schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, afterSaleStart))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.CANNOT_MODIFY_AFTER_SALE_START);
        }

        @Test
        @DisplayName("CANCELLED 프로그램 스케줄에 가격 등급 추가 시 SCHEDULE_NOT_EDITABLE 예외 — 도메인 규칙 위반")
        void fail_cancelledProgram() {
            Program program = draftProgram(); // DRAFT
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();                // DRAFT → ON_SALE 성공
            program.cancel();

            assertThatThrownBy(() -> schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SCHEDULE_NOT_EDITABLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Schedule.removePriceGrade()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Schedule.removePriceGrade() — 가격 등급 삭제")
    class RemovePriceGrade {

        @Test
        @DisplayName("정상 삭제 성공")
        void success() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);

            schedule.removePriceGrade("VIP", NOW);

            assertThat(schedule.getPriceGrades()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 등급명 삭제 시 PRICE_GRADE_NOT_FOUND 예외 — 외부 의존성 실패")
        void fail_notFound() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);

            assertThatThrownBy(() -> schedule.removePriceGrade("R석", NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PRICE_GRADE_NOT_FOUND);
        }

        @Test
        @DisplayName("등급명 null 시 INVALID_GRADE_LABEL 예외")
        void fail_nullGradeLabel() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.removePriceGrade(null, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_GRADE_LABEL);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Schedule.addSectionCapacity() — STANDING·FREE 전용
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Schedule.addSectionCapacity() — 구역별 인원 추가")
    class AddSectionCapacity {

        private Program standingProgram() {
            return Program.create("공연", "CONCERT", "POP",
                ProgramType.STANDING, "서울", null, null);
        }

        @Test
        @DisplayName("STANDING 타입 구역별 인원 추가 성공")
        void success_standing() {
            Program program = standingProgram();
            Schedule schedule = addSchedule(program);

            schedule.addSectionCapacity(SECTION_ID, 300, NOW);

            assertThat(schedule.getSectionCapacities()).hasSize(1);
        }

        @Test
        @DisplayName("SEATED 타입에서 addSectionCapacity 호출 시 SECTION_CAPACITY_NOT_ALLOWED 예외 — 도메인 규칙 위반")
        void fail_seated() {
            Program program = draftProgram(); // SEATED
            Schedule schedule = addSchedule(program);

            assertThatThrownBy(() -> schedule.addSectionCapacity(SECTION_ID, 300, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SECTION_CAPACITY_NOT_ALLOWED);
        }

        @Test
        @DisplayName("동일 구역 중복 등록 시 SECTION_CAPACITY_DUPLICATE 예외 — 도메인 규칙 위반")
        void fail_duplicate() {
            Program program = standingProgram();
            Schedule schedule = addSchedule(program);
            schedule.addSectionCapacity(SECTION_ID, 100, NOW);

            assertThatThrownBy(() -> schedule.addSectionCapacity(SECTION_ID, 100, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SECTION_CAPACITY_DUPLICATE);
        }

        @Test
        @DisplayName("구역별 인원 합계가 totalCapacity 초과 시 SECTION_CAPACITY_EXCEEDS_TOTAL 예외 — 도메인 규칙 위반")
        void fail_exceedsTotal() {
            Program program = standingProgram();
            Schedule schedule = addSchedule(program); // totalCapacity = 500

            assertThatThrownBy(() -> schedule.addSectionCapacity(SECTION_ID, 600, NOW))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.SECTION_CAPACITY_EXCEEDS_TOTAL);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramStatus.validateTransition()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ProgramStatus — 상태 전이 규칙")
    class ProgramStatusTransition {

        @Test
        @DisplayName("DRAFT → ON_SALE 허용")
        void allowed_draftToOnSale() {
            assertThatCode(() -> ProgramStatus.DRAFT.validateTransition(ProgramStatus.ON_SALE))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ON_SALE → CANCELLED 허용")
        void allowed_onSaleToCancelled() {
            assertThatCode(() -> ProgramStatus.ON_SALE.validateTransition(ProgramStatus.CANCELLED))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ON_SALE → CLOSED 허용")
        void allowed_onSaleToClosed() {
            assertThatCode(() -> ProgramStatus.ON_SALE.validateTransition(ProgramStatus.CLOSED))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SOLD_OUT → CLOSED 허용")
        void allowed_soldOutToClosed() {
            assertThatCode(() -> ProgramStatus.SOLD_OUT.validateTransition(ProgramStatus.CLOSED))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DRAFT → CANCELLED 불허 — ON_SALE 경유 없이 취소 불가")
        void notAllowed_draftToCancelled() {
            assertThatThrownBy(() -> ProgramStatus.DRAFT.validateTransition(ProgramStatus.CANCELLED))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("CANCELLED → 어떤 상태로도 전이 불가 — 복구 불가 상태")
        void notAllowed_fromCancelled() {
            assertThatThrownBy(() -> ProgramStatus.CANCELLED.validateTransition(ProgramStatus.ON_SALE))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("CLOSED → 어떤 상태로도 전이 불가 — 복구 불가 상태")
        void notAllowed_fromClosed() {
            assertThatThrownBy(() -> ProgramStatus.CLOSED.validateTransition(ProgramStatus.ON_SALE))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("DRAFT → CLOSED 직접 전이 불가")
        void notAllowed_draftToClosed() {
            assertThatThrownBy(() -> ProgramStatus.DRAFT.validateTransition(ProgramStatus.CLOSED))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException)e).getErrorCode())
                .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PagedResult compact constructor
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PagedResult — compact constructor 검증")
    class PagedResultValidation {

        @Test
        @DisplayName("정상 생성 성공")
        void success() {
            assertThatCode(() -> new PagedResult<>(List.of(), 0L, 0, 0, 1))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("content null 시 IllegalArgumentException")
        void fail_nullContent() {
            assertThatThrownBy(() -> new PagedResult<>(null, 0L, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("totalElements 음수 시 IllegalArgumentException — 경계값")
        void fail_negativeTotal() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), -1L, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageNumber 음수 시 IllegalArgumentException — 경계값")
        void fail_negativePageNumber() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), 0L, 0, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageSize 0 시 IllegalArgumentException — 경계값")
        void fail_zeroPageSize() {
            assertThatThrownBy(() -> new PagedResult<>(List.of(), 0L, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("content는 불변 복사 — 외부 수정이 내부에 반영되지 않는다")
        void success_contentIsDefensivelyCopied() {
            java.util.List<String> mutable = new java.util.ArrayList<>();
            mutable.add("a");
            PagedResult<String> result = new PagedResult<>(mutable, 1L, 1, 0, 10);

            mutable.add("b"); // 외부 수정

            assertThat(result.content()).hasSize(1); // 내부에는 반영 안 됨
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramSearchSpec compact constructor
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ProgramSearchSpec — compact constructor 검증")
    class ProgramSearchSpecValidation {

        @Test
        @DisplayName("정상 생성 성공")
        void success() {
            assertThatCode(() -> new ProgramSearchSpec(null, null, null, null, null, null, 0, 1))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pageNumber 음수 시 IllegalArgumentException — 경계값")
        void fail_negativePageNumber() {
            assertThatThrownBy(() -> new ProgramSearchSpec(null, null, null, null, null, null, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pageSize 0 시 IllegalArgumentException — 경계값")
        void fail_zeroPageSize() {
            assertThatThrownBy(() -> new ProgramSearchSpec(null, null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getOffset() — pageNumber × pageSize 계산 검증")
        void success_offsetCalculation() {
            ProgramSearchSpec spec = new ProgramSearchSpec(null, null, null, null, null, null, 3, 20);

            assertThat(spec.getOffset()).isEqualTo(60L);
        }
    }
}
