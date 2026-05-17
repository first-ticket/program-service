package com.firstticket.programservice.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.programservice.application.dto.command.AddPriceGradeCommand;
import com.firstticket.programservice.application.dto.command.CreateProgramCommand;
import com.firstticket.programservice.application.dto.command.CreateScheduleCommand;
import com.firstticket.programservice.application.dto.query.ProgramSearchQuery;
import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.ScheduleRepository;
import com.firstticket.programservice.domain.event.ProgramEventPublisher;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.domain.query.ProgramQueryRepository;
import com.firstticket.programservice.domain.service.SeatProvider;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.SectionValidationData;
import com.firstticket.programservice.domain.service.dto.VenueValidationData;

/**
 * ProgramCommandService, ProgramQueryService 단위 테스트.
 *
 * [테스트 규칙]
 * - JUnit5 + Mockito 단위 테스트 (Spring 컨텍스트 없음)
 * - 외부 의존성(Repository, VenueProvider, SeatProvider, EventPublisher)은 Mock 처리
 * - Service 예외 케이스 반드시 작성
 * - 동일 결과의 중복 케이스 하나만 작성
 *
 * [도메인 테스트에서 발견된 주의 사항]
 * - DRAFT → CANCELLED 직접 전이 불가: cancel() 전 반드시 publish() 선행
 * - CANCELLED 상태에서 cancel() 재호출 시 INVALID_STATUS_TRANSITION 예외
 * - Schedule.id는 영속화 전 null → removeSchedule() NPE 방지를 위해
 *   반드시 리플렉션으로 id 주입 후 사용
 *
 * [실제 패키지 경로]
 * - VenueProvider   : com.firstticket.programservice.domain.service.VenueProvider
 * - SeatProvider    : com.firstticket.programservice.domain.service.SeatProvider
 * - VenueValidationData : domain.service.dto — 필드: (int totalCapacity, List<SectionInfo> sections)
 * - SectionValidationData : domain.service.dto — 필드: (String seatType, int capacity)
 * - ProgramSearchQuery : application.dto.query — 필드: (category, keyword, region, date, sortField, direction, pageNumber, pageSize)
 * - ProgramQueryRepository : domain.query.ProgramQueryRepository
 */
@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID PROGRAM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID VENUE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID SECTION_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID OWNER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID OTHER_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

    // 충분히 먼 미래 — @FutureOrPresent 및 Schedule 시간 검증 통과
    private static final LocalDateTime FUTURE = LocalDateTime.of(2027, 6, 1, 14, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 0, 0);

    // ══════════════════════════════════════════════════════════════════
    // ProgramCommandService
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ProgramCommandService")
    class CommandServiceTest {

        @Mock
        private ProgramRepository programRepository;
        @Mock
        private ScheduleRepository scheduleRepository;
        @Mock
        private VenueProvider venueProvider;
        @Mock
        private ProgramEventPublisher programEventPublisher;

        @InjectMocks
        private ProgramCommandService programCommandService;

        // ── 픽스처 빌더 ────────────────────────────────────────────

        /** DRAFT 상태 Program 픽스처 — id, createdBy 주입 */
        private Program draftProgram() {
            Program program = Program.create(
                "테스트 공연", "CONCERT", "POP", ProgramType.SEATED, "서울", null, null);
            injectField(program, "id", PROGRAM_ID);
            injectField(program, "createdBy", OWNER_ID);
            return program;
        }

        /** Schedule을 program에 추가하고 id를 주입하여 반환 */
        private Schedule addSchedule(Program program) {
            Schedule schedule = program.addSchedule(
                VENUE_ID,
                FUTURE, FUTURE.plusHours(2),
                FUTURE.minusDays(30), FUTURE.minusDays(1),
                500, NOW
            );
            injectField(schedule, "id", SCHEDULE_ID);
            return schedule;
        }

        /** ON_SALE 상태 Program 픽스처 — DRAFT → 가격등급 추가 → publish */
        private Program onSaleProgram() {
            Program program = draftProgram();
            Schedule schedule = addSchedule(program);
            schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);
            program.publish();
            return program;
        }

        /**
         * CANCELLED 상태 Program 픽스처.
         * 도메인 규칙: DRAFT → CANCELLED 직접 전이 불가.
         * 반드시 ON_SALE 경유 후 cancel() 호출.
         */
        private Program cancelledProgram() {
            Program program = onSaleProgram();
            program.cancel();
            return program;
        }

        /**
         * VenueValidationData 픽스처.
         * 실제 필드: (int totalCapacity, List<SectionInfo> sections)
         * VENUE_ID 인자 없음 — sections는 빈 리스트로 픽스처 구성
         */
        private VenueValidationData venueValidation(int totalCapacity) {
            return new VenueValidationData(totalCapacity, List.of());
        }

        /**
         * SectionValidationData 픽스처.
         * 실제 필드: (String seatType, int capacity)
         * SECTION_ID 인자 없음
         */
        private SectionValidationData sectionValidation(String seatType, int capacity) {
            return new SectionValidationData(seatType, capacity);
        }

        // ── createProgram ───────────────────────────────────────────

        @Nested
        @DisplayName("createProgram() — 프로그램 생성")
        class CreateProgram {

            @Test
            @DisplayName("정상 생성 — DRAFT 상태, ProgramCreatedEvent 발행")
            void success() {
                Program program = draftProgram();
                given(programRepository.save(any())).willReturn(program);
                willDoNothing().given(programEventPublisher).publishProgramCreated(any());

                CreateProgramCommand command = new CreateProgramCommand(
                    "테스트 공연", "CONCERT", "POP", "SEATED", "서울", null, null);

                ProgramResult result = programCommandService.createProgram(command);

                assertThat(result.status()).isEqualTo(ProgramStatus.DRAFT);
                then(programEventPublisher).should().publishProgramCreated(any());
            }

            @Test
            @DisplayName("잘못된 type 값 시 INVALID_PROGRAM_TYPE 예외 — 입력값 유효성 오류")
            void fail_invalidType() {
                CreateProgramCommand command = new CreateProgramCommand(
                    "공연", "CONCERT", "POP", "WRONG_TYPE", "서울", null, null);

                assertThatThrownBy(() -> programCommandService.createProgram(command))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_PROGRAM_TYPE);
            }

            @Test
            @DisplayName("title null 시 INVALID_TITLE 예외 — 입력값 유효성 오류")
            void fail_nullTitle() {
                CreateProgramCommand command = new CreateProgramCommand(
                    null, "CONCERT", "POP", "SEATED", "서울", null, null);

                assertThatThrownBy(() -> programCommandService.createProgram(command))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_TITLE);
            }
        }

        // ── deleteProgram ───────────────────────────────────────────

        @Nested
        @DisplayName("deleteProgram() — 프로그램 삭제")
        class DeleteProgram {

            @Test
            @DisplayName("DRAFT 상태 삭제 성공")
            void success_draft() {
                Program program = draftProgram();
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.of(program));
                willDoNothing().given(programRepository).delete(program);

                assertThatCode(() -> programCommandService.deleteProgram(OWNER_ID, PROGRAM_ID))
                    .doesNotThrowAnyException();

                then(programRepository).should().delete(program);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.empty());

                assertThatThrownBy(() -> programCommandService.deleteProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 삭제 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                Program program = draftProgram();
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.deleteProgram(OTHER_ID, PROGRAM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }

            @Test
            @DisplayName("ON_SALE 상태 삭제 시 PROGRAM_NOT_DELETABLE_IN_PROCESS 예외 — 도메인 규칙 위반")
            void fail_onSale() {
                Program program = onSaleProgram();
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.deleteProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_DELETABLE_IN_PROCESS);
            }

            @Test
            @DisplayName("SOLD_OUT 상태 삭제 시 PROGRAM_NOT_DELETABLE 예외 — 도메인 규칙 위반")
            void fail_soldOut() {
                Program program = draftProgram();
                injectField(program, "status", ProgramStatus.SOLD_OUT);
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.deleteProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_DELETABLE);
            }
        }

        // ── publishProgram ──────────────────────────────────────────

        @Nested
        @DisplayName("publishProgram() — DRAFT → ON_SALE")
        class PublishProgram {

            @Test
            @DisplayName("정상 publish — ScheduleCreated, ProgramTimeUpdated 이벤트 발행")
            void success() {
                Program program = draftProgram();
                Schedule schedule = addSchedule(program);
                schedule.addPriceGrade(SECTION_ID, "VIP", 50_000, NOW);

                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(venueProvider.validateVenue(VENUE_ID, ProgramType.SEATED))
                    .willReturn(venueValidation(500));
                willDoNothing().given(programEventPublisher)
                    .publishScheduleCreated(any(), any(), any());
                willDoNothing().given(programEventPublisher)
                    .publishProgramTimeUpdated(any(), any());

                programCommandService.publishProgram(OWNER_ID, PROGRAM_ID);

                assertThat(program.getStatus()).isEqualTo(ProgramStatus.ON_SALE);
                then(programEventPublisher).should().publishScheduleCreated(any(), any(), any());
                then(programEventPublisher).should().publishProgramTimeUpdated(any(), any());
            }

            @Test
            @DisplayName("스케줄 없이 publish 시 SCHEDULE_REQUIRED 예외 — 도메인 규칙 위반")
            void fail_noSchedule() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.publishProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.SCHEDULE_REQUIRED);
            }

            @Test
            @DisplayName("가격등급 없는 스케줄로 publish 시 PRICE_GRADE_REQUIRED 예외 — 도메인 규칙 위반")
            void fail_noPriceGrade() {
                Program program = draftProgram();
                addSchedule(program); // 가격등급 추가 없음

                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.publishProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PRICE_GRADE_REQUIRED);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> programCommandService.publishProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }

            @Test
            @DisplayName("소유자가 아닌 사용자 publish 시 FORBIDDEN 예외 — 권한 오류")
            void fail_notOwner() {
                Program program = draftProgram();
                addSchedule(program);
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.publishProgram(OTHER_ID, PROGRAM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(CommonErrorCode.FORBIDDEN);
            }
        }

        // ── cancelProgram ───────────────────────────────────────────

        @Nested
        @DisplayName("cancelProgram() — → CANCELLED")
        class CancelProgram {

            @Test
            @DisplayName("ON_SALE → CANCELLED 성공 — ProgramCancelledEvent 발행")
            void success_fromOnSale() {
                // 도메인 규칙: DRAFT → CANCELLED 직접 전이 불가, ON_SALE 경유 필수
                Program program = onSaleProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                willDoNothing().given(programEventPublisher).publishProgramCancelled(any());

                programCommandService.cancelProgram(OWNER_ID, PROGRAM_ID);

                assertThat(program.getStatus()).isEqualTo(ProgramStatus.CANCELLED);
                then(programEventPublisher).should().publishProgramCancelled(any());
            }

            @Test
            @DisplayName("CANCELLED 상태에서 재취소 시 INVALID_STATUS_TRANSITION 예외 — 복구 불가 상태")
            void fail_alreadyCancelled() {
                // cancelledProgram()은 반드시 ON_SALE 경유 — DRAFT에서 직접 cancel() 호출 불가
                Program program = cancelledProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.cancelProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> programCommandService.cancelProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }
        }

        // ── closeProgram ────────────────────────────────────────────

        @Nested
        @DisplayName("closeProgram() — → CLOSED")
        class CloseProgram {

            @Test
            @DisplayName("종료되지 않은 스케줄 있을 때 PROGRAM_NOT_ENDED_YET 예외 — 도메인 규칙 위반")
            void fail_notEnded() {
                // onSaleProgram()의 스케줄은 FUTURE 시점 → LocalDateTime.now() 기준 아직 미종료
                Program program = onSaleProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.closeProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_ENDED_YET);
            }

            @Test
            @DisplayName("DRAFT 상태에서 close 시 INVALID_STATUS_TRANSITION 예외 — 잘못된 상태 전이")
            void fail_fromDraft() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.closeProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_STATUS_TRANSITION);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> programCommandService.closeProgram(OWNER_ID, PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }
        }

        // ── createSchedule ──────────────────────────────────────────

        @Nested
        @DisplayName("createSchedule() — 스케줄 등록")
        class CreateSchedule {

            private CreateScheduleCommand validCommand() {
                return new CreateScheduleCommand(
                    PROGRAM_ID, VENUE_ID,
                    FUTURE, FUTURE.plusHours(2),
                    FUTURE.minusDays(30), FUTURE.minusDays(1),
                    500
                );
            }

            @Test
            @DisplayName("정상 등록 성공")
            void success() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(venueProvider.validateVenue(VENUE_ID, ProgramType.SEATED))
                    .willReturn(venueValidation(500));
                given(scheduleRepository.findOverlappingSchedulesWithLock(any(), any(), any()))
                    .willReturn(List.of());
                given(programRepository.save(any())).willReturn(program);

                ProgramResult result = programCommandService.createSchedule(OWNER_ID, validCommand());

                assertThat(result.schedules()).hasSize(1);
            }

            @Test
            @DisplayName("totalCapacity가 공연장 수용량 초과 시 TOTAL_CAPACITY_EXCEEDS_VENUE_LIMIT 예외 — 도메인 규칙 위반")
            void fail_capacityExceedsVenueLimit() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(venueProvider.validateVenue(VENUE_ID, ProgramType.SEATED))
                    .willReturn(venueValidation(499)); // command.totalCapacity=500 > 499

                assertThatThrownBy(() -> programCommandService.createSchedule(OWNER_ID, validCommand()))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.TOTAL_CAPACITY_EXCEEDS_VENUE_LIMIT);
            }

            @Test
            @DisplayName("공연장 시간 겹침 시 VENUE_TIME_CONFLICT 예외 — 도메인 규칙 위반 (V-04)")
            void fail_venueTimeConflict() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(venueProvider.validateVenue(VENUE_ID, ProgramType.SEATED))
                    .willReturn(venueValidation(500));
                given(scheduleRepository.findOverlappingSchedulesWithLock(any(), any(), any()))
                    .willReturn(List.of(mock(Schedule.class))); // 겹치는 스케줄 존재

                assertThatThrownBy(() -> programCommandService.createSchedule(OWNER_ID, validCommand()))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.VENUE_TIME_CONFLICT);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> programCommandService.createSchedule(OWNER_ID, validCommand()))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }
        }

        // ── addPriceGrade ───────────────────────────────────────────

        @Nested
        @DisplayName("addPriceGrade() — 가격 등급 추가")
        class AddPriceGrade {

            private AddPriceGradeCommand validCommand(UUID sectionId) {
                return new AddPriceGradeCommand(PROGRAM_ID, SCHEDULE_ID, sectionId, "VIP", 50_000);
            }

            @Test
            @DisplayName("정상 추가 성공")
            void success() {
                Program program = draftProgram();
                addSchedule(program);
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                // SectionValidationData 필드: (String seatType, int capacity) — SECTION_ID 인자 없음
                given(venueProvider.validateSection(VENUE_ID, SECTION_ID))
                    .willReturn(sectionValidation("SEATED", 120));

                ProgramResult result = programCommandService.addPriceGrade(
                    OWNER_ID, validCommand(SECTION_ID));

                assertThat(result.schedules().get(0).priceGrades()).hasSize(1);
            }

            @Test
            @DisplayName("구역 seatType과 program type 불일치 시 SECTION_TYPE_MISMATCH 예외 — 도메인 규칙 위반")
            void fail_sectionTypeMismatch() {
                Program program = draftProgram(); // SEATED 타입
                addSchedule(program);
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(venueProvider.validateSection(VENUE_ID, SECTION_ID))
                    .willReturn(sectionValidation("STANDING", 300)); // 타입 불일치

                assertThatThrownBy(() -> programCommandService.addPriceGrade(
                    OWNER_ID, validCommand(SECTION_ID)))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.SECTION_TYPE_MISMATCH);
            }

            @Test
            @DisplayName("schedules가 비어있어 SCHEDULE_ID 매칭 실패 시 INVALID_SCHEDULE_ID 예외 — 외부 의존성 실패")
            void fail_scheduleNotFound() {
                Program program = draftProgram(); // schedules 비어있음
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                assertThatThrownBy(() -> programCommandService.addPriceGrade(
                    OWNER_ID, validCommand(SECTION_ID)))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_SCHEDULE_ID);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramQueryService
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ProgramQueryService")
    class QueryServiceTest {

        @Mock
        private ProgramRepository programRepository;
        @Mock
        private ProgramQueryRepository programQueryRepository;
        @Mock
        private SeatProvider seatProvider;
        @Mock
        private ScheduleRepository scheduleRepository;
        @Mock
        private VenueProvider venueProvider;

        @InjectMocks
        private ProgramQueryService programQueryService;

        private Program draftProgram() {
            Program program = Program.create(
                "테스트 공연", "CONCERT", "POP", ProgramType.SEATED, "서울", null, null);
            injectField(program, "id", PROGRAM_ID);
            injectField(program, "createdBy", OWNER_ID);
            return program;
        }

        // ── getProgram ──────────────────────────────────────────────

        @Nested
        @DisplayName("getProgram() — 상세 조회")
        class GetProgram {

            @Test
            @DisplayName("정상 조회 — SeatProvider 호출 (장애 시 빈 리스트 fallback)")
            void success() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));
                given(seatProvider.getRemainingCounts(PROGRAM_ID)).willReturn(List.of());

                ProgramResult result = programQueryService.getProgram(PROGRAM_ID);

                assertThat(result.id()).isEqualTo(PROGRAM_ID);
                then(seatProvider).should().getRemainingCounts(PROGRAM_ID);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외 — 외부 의존성 실패")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() -> programQueryService.getProgram(PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }

            @Test
            @DisplayName("programId null 시 INVALID_PROGRAM_ID 예외 — 입력값 유효성 오류")
            void fail_nullProgramId() {
                assertThatThrownBy(() -> programQueryService.getProgram(null))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_PROGRAM_ID);
            }
        }

        // ── getProgramWithoutRemainingCount ─────────────────────────

        @Nested
        @DisplayName("getProgramWithoutRemainingCount() — SeatProvider 미호출 조회")
        class GetProgramWithoutRemainingCount {

            @Test
            @DisplayName("정상 조회 — SeatProvider 호출 없음")
            void success_noSeatProviderCall() {
                Program program = draftProgram();
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.of(program));

                programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID);

                then(seatProvider).shouldHaveNoInteractions();
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findByIdWithSchedules(PROGRAM_ID))
                    .willReturn(Optional.empty());

                assertThatThrownBy(() ->
                    programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }
        }

        // ── getProgramStatus ────────────────────────────────────────

        @Nested
        @DisplayName("getProgramStatus() — 상태만 조회")
        class GetProgramStatus {

            @Test
            @DisplayName("DRAFT 상태 반환")
            void success_returnsDraft() {
                Program program = draftProgram();
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.of(program));

                ProgramStatus status = programQueryService.getProgramStatus(PROGRAM_ID);

                assertThat(status).isEqualTo(ProgramStatus.DRAFT);
            }

            @Test
            @DisplayName("programId null 시 INVALID_PROGRAM_ID 예외")
            void fail_nullProgramId() {
                assertThatThrownBy(() -> programQueryService.getProgramStatus(null))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_PROGRAM_ID);
            }

            @Test
            @DisplayName("존재하지 않는 programId 시 PROGRAM_NOT_FOUND 예외")
            void fail_notFound() {
                given(programRepository.findById(PROGRAM_ID)).willReturn(Optional.empty());

                assertThatThrownBy(() -> programQueryService.getProgramStatus(PROGRAM_ID))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_NOT_FOUND);
            }
        }

        // ── searchPrograms ──────────────────────────────────────────

        @Nested
        @DisplayName("searchPrograms() — 목록 조회")
        class SearchPrograms {

            @Test
            @DisplayName("정상 조회")
            void success() {
                given(programQueryRepository.findBySpec(any()))
                    .willReturn(new PagedResult<>(List.of(), 0L, 0, 0, 20));

                // ProgramSearchQuery 필드: (category, keyword, region, date, sortField, direction, pageNumber, pageSize)
                ProgramSearchQuery query = new ProgramSearchQuery(
                    null, null, null, null, null, null, 0, 20);

                assertThatCode(() -> programQueryService.searchPrograms(query))
                    .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("pageSize 0 이하 시 INVALID_PAGE_SIZE 예외 — 경계값")
            void fail_zeroPageSize() {
                ProgramSearchQuery query = new ProgramSearchQuery(
                    null, null, null, null, null, null, 0, 0);

                assertThatThrownBy(() -> programQueryService.searchPrograms(query))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_PAGE_SIZE);
            }

            @Test
            @DisplayName("pageNumber 음수 시 INVALID_PAGE_NUMBER 예외 — 경계값")
            void fail_negativePageNumber() {
                ProgramSearchQuery query = new ProgramSearchQuery(
                    null, null, null, null, null, null, -1, 20);

                assertThatThrownBy(() -> programQueryService.searchPrograms(query))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_PAGE_NUMBER);
            }

            @Test
            @DisplayName("query null 시 INVALID_SEARCH_QUERY 예외")
            void fail_nullQuery() {
                assertThatThrownBy(() -> programQueryService.searchPrograms(null))
                    .isInstanceOf(ProgramException.class)
                    .extracting(e -> ((ProgramException)e).getErrorCode())
                    .isEqualTo(ProgramErrorCode.INVALID_SEARCH_QUERY);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 공통 리플렉션 헬퍼
    // ══════════════════════════════════════════════════════════════════

    /**
     * 리플렉션으로 객체 필드에 값을 주입한다.
     * 사용처:
     * - Program.id          : @GeneratedValue(UUID), 영속화 전 null
     * - Program.createdBy   : BaseUserEntity 필드, 영속화 전 null → checkOwner() NPE 방지
     * - Program.status      : SOLD_OUT 등 직접 설정이 필요한 상태 강제 주입
     * - Schedule.id         : removeSchedule()의 s.getId().equals() NPE 방지
     * 프로덕션 코드 변경 없이 단위 테스트 픽스처 구성에만 사용한다.
     */
    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("필드 주입 실패: " + fieldName, e);
        }
    }

    /**
     * 상속 계층을 포함하여 필드를 탐색한다.
     * BaseUserEntity.createdBy, BaseEntity.id 등 부모 클래스 필드 대응.
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("필드를 찾을 수 없습니다: " + fieldName);
    }
}
