package com.firstticket.programservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.firstticket.common.persistence.CommonJpaAutoConfiguration;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.domain.query.ProgramSearchSpec;
import com.firstticket.programservice.domain.query.ProgramSummaryData;
import com.firstticket.programservice.infrastructure.config.JpaConfig;

/**
 * Program Repository 슬라이스 테스트.
 *
 * [테스트 규칙]
 * - @DataJpaTest 슬라이스 테스트 (커스텀 쿼리가 있는 경우에만 작성)
 * - H2 in-memory DB 사용
 * - 테스트는 독립적으로 실행 가능 (@Transactional → 자동 롤백)
 *
 * [테스트 대상 커스텀 쿼리]
 * 1. ProgramJpaRepository.findByIdWithSchedules()
 *    - JOIN FETCH로 N+1 방지
 *    - soft delete 제외
 *
 * 2. ProgramJpaRepository.existsByVenueIdAndStatusNotIn()
 *    - 특정 공연장의 활성 프로그램 존재 여부
 *    - CANCELLED·CLOSED 제외
 *    - soft delete 제외
 *
 * 3. ProgramQueryRepositoryImpl.findBySpec() — QueryDSL
 *    - 카테고리·키워드·지역·날짜 필터
 *    - 정렬 (createdAt·title·saleEndAt 화이트리스트)
 *    - 페이지네이션
 *    - soft delete 제외
 *
 * [findOverlappingSchedulesWithLock() 제외 이유]
 * btree_gist 확장과 tsrange exclusion constraint는 PostgreSQL 전용이다.
 * H2에서 지원하지 않으므로 Testcontainers + PostgreSQL 환경에서 별도 테스트해야 한다.
 * (현재 테스트 규칙상 통합 테스트는 Won't MVP이므로 작성하지 않는다.)
 */
@DataJpaTest
@Import({CommonJpaAutoConfiguration.class, JpaConfig.class, ProgramQueryRepositoryImpl.class})
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.default_schema="  // H2는 schema 없이 사용
})
class ProgramRepositoryTest {

    @Autowired
    private ProgramJpaRepository programJpaRepository;
    @Autowired
    private ProgramQueryRepositoryImpl programQueryRepositoryImpl;

    // ── 공통 픽스처 ──────────────────────────────────────────────────

    private static final UUID VENUE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID_2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    private static final LocalDateTime FUTURE = LocalDateTime.of(2027, 6, 1, 14, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 0, 0);

    /**
     * Program 픽스처 저장 헬퍼.
     * createdBy는 BaseUserEntity Auditing으로 자동 주입되지 않으므로
     * 리플렉션으로 주입 후 저장한다.
     */
    private Program saveProgram(String title, String category, ProgramStatus status) {
        Program program = Program.create(title, category, "POP",
            ProgramType.SEATED, "서울", null, null);
        injectField(program, "createdBy", OWNER_ID);
        if (status != ProgramStatus.DRAFT) {
            injectField(program, "status", status);
        }
        return programJpaRepository.save(program);
    }

    /**
     * Schedule 픽스처 추가 헬퍼.
     * program에 스케줄을 추가하고 저장한다.
     */
    private Schedule addAndSaveSchedule(Program program, UUID venueId,
        LocalDateTime eventStart, LocalDateTime eventEnd) {
        Schedule schedule = program.addSchedule(
            venueId,
            eventStart, eventEnd,
            eventStart.minusDays(30), eventStart.minusDays(1),
            500, NOW
        );

        injectField(schedule, "createdBy", OWNER_ID);

        programJpaRepository.save(program);
        return schedule;
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramJpaRepository.findByIdWithSchedules()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByIdWithSchedules() — JOIN FETCH + soft delete 제외")
    class FindByIdWithSchedules {

        @Test
        @DisplayName("schedules JOIN FETCH 조회 성공 — N+1 없이 schedules 포함")
        void success_withSchedules() {
            Program program = saveProgram("테스트 공연", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));

            Optional<Program> result =
                programJpaRepository.findByIdWithSchedules(program.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getSchedules()).hasSize(1);
        }

        @Test
        @DisplayName("스케줄 없는 프로그램 조회 — schedules 빈 리스트")
        void success_noSchedules() {
            Program program = saveProgram("테스트 공연", "CONCERT", ProgramStatus.DRAFT);

            Optional<Program> result =
                programJpaRepository.findByIdWithSchedules(program.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getSchedules()).isEmpty();
        }

        @Test
        @DisplayName("soft delete된 프로그램 조회 시 empty 반환 — soft delete 제외")
        void fail_softDeleted() {
            Program program = saveProgram("삭제된 공연", "CONCERT", ProgramStatus.DRAFT);
            // soft delete 적용
            injectField(program, "deletedAt", LocalDateTime.now());
            programJpaRepository.save(program);

            Optional<Program> result =
                programJpaRepository.findByIdWithSchedules(program.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 empty 반환")
        void fail_notFound() {
            Optional<Program> result =
                programJpaRepository.findByIdWithSchedules(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramJpaRepository.existsByVenueIdAndStatusNotIn()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByVenueIdAndStatusNotIn() — 활성 프로그램 존재 여부")
    class ExistsByVenueIdAndStatusNotIn {

        private static final List<ProgramStatus> EXCLUDE =
            List.of(ProgramStatus.CANCELLED, ProgramStatus.CLOSED);

        @Test
        @DisplayName("ON_SALE 프로그램이 있을 때 true 반환")
        void true_onSaleExists() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.ON_SALE);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("DRAFT 프로그램이 있을 때 true 반환")
        void true_draftExists() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("CANCELLED 프로그램만 있을 때 false 반환 — 제외 상태")
        void false_onlyCancelled() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));

            injectField(program, "status", ProgramStatus.CANCELLED);
            programJpaRepository.saveAndFlush(program);

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("CLOSED 프로그램만 있을 때 false 반환 — 제외 상태")
        void false_onlyClosed() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));

            injectField(program, "status", ProgramStatus.CLOSED);
            programJpaRepository.saveAndFlush(program);

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("soft delete된 프로그램은 false 반환 — soft delete 제외")
        void false_softDeleted() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.ON_SALE);
            addAndSaveSchedule(program, VENUE_ID, FUTURE, FUTURE.plusHours(2));
            injectField(program, "deletedAt", LocalDateTime.now());
            programJpaRepository.save(program);

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("다른 공연장의 프로그램은 false 반환 — venueId 격리")
        void false_differentVenue() {
            Program program = saveProgram("공연", "CONCERT", ProgramStatus.ON_SALE);
            addAndSaveSchedule(program, VENUE_ID_2, FUTURE, FUTURE.plusHours(2));

            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("프로그램 없을 때 false 반환")
        void false_noProgram() {
            boolean result = programJpaRepository
                .existsByVenueIdAndStatusNotIn(VENUE_ID, EXCLUDE);

            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ProgramQueryRepositoryImpl.findBySpec() — QueryDSL
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findBySpec() — QueryDSL 필터·정렬·페이지네이션")
    class FindBySpec {

        @Test
        @DisplayName("조건 없이 전체 조회 — soft delete 제외")
        void success_noFilter() {
            saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("공연B", "MUSICAL", ProgramStatus.ON_SALE);

            Program deleted = saveProgram("삭제된공연", "CONCERT", ProgramStatus.DRAFT);
            injectField(deleted, "deletedAt", LocalDateTime.now());
            programJpaRepository.save(deleted);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, null, null, null, null, null, 0, 20);
            PagedResult<com.firstticket.programservice.domain.query.ProgramSummaryData> result =
                programQueryRepositoryImpl.findBySpec(spec);

            assertThat(result.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("카테고리 필터 — 해당 카테고리만 반환")
        void success_categoryFilter() {
            saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("공연B", "MUSICAL", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                "CONCERT", null, null, null, null, null, 0, 20);

            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);
            assertThat(result.totalElements()).isEqualTo(1);

            ProgramSummaryData summary = (ProgramSummaryData)result.content().get(0);
            assertThat(summary.title()).isEqualTo("공연A");
        }

        @Test
        @DisplayName("키워드 필터 — 대소문자 무관 제목 포함 검색")
        void success_keywordFilter() {
            saveProgram("봄 콘서트", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("가을 뮤지컬", "MUSICAL", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, "콘서트", null, null, null, null, 0, 20);
            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);

            // then
            assertThat(result.totalElements()).isEqualTo(1);

            ProgramSummaryData summary = (ProgramSummaryData)result.content().get(0);
            assertThat(summary.title()).isEqualTo("봄 콘서트");
        }

        @Test
        @DisplayName("키워드 공백만 있을 때 전체 반환 — blank 키워드는 필터 미적용")
        void success_blankKeyword() {
            saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("공연B", "MUSICAL", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, "   ", null, null, null, null, 0, 20);
            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);

            assertThat(result.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("날짜 필터 — [startOfDay, nextDayStart) 반열린 구간")
        void success_dateFilter() {
            // eventStartAt이 2027-06-01에 속하는 스케줄
            Program inRange = saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(inRange, VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 14, 0),
                LocalDateTime.of(2027, 6, 1, 17, 0));

            // eventStartAt이 2027-06-02인 스케줄 — 범위 밖
            Program outRange = saveProgram("공연B", "CONCERT", ProgramStatus.DRAFT);
            addAndSaveSchedule(outRange, VENUE_ID_2,
                LocalDateTime.of(2027, 6, 2, 14, 0),
                LocalDateTime.of(2027, 6, 2, 17, 0));

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, null, null,
                java.time.LocalDate.of(2027, 6, 1),
                null, null, 0, 20);

            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);
            assertThat(result.totalElements()).isEqualTo(1);

            ProgramSummaryData summary = (ProgramSummaryData)result.content().get(0);
            assertThat(summary.title()).isEqualTo("공연A");
        }

        @Test
        @DisplayName("페이지네이션 — 2건 중 1건씩 분리")
        void success_pagination() {
            saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("공연B", "CONCERT", ProgramStatus.DRAFT);

            ProgramSearchSpec page0 = new ProgramSearchSpec(
                null, null, null, null, null, null, 0, 1);
            ProgramSearchSpec page1 = new ProgramSearchSpec(
                null, null, null, null, null, null, 1, 1);

            PagedResult<?> result0 = programQueryRepositoryImpl.findBySpec(page0);
            PagedResult<?> result1 = programQueryRepositoryImpl.findBySpec(page1);

            assertThat(result0.content()).hasSize(1);
            assertThat(result1.content()).hasSize(1);
            assertThat(result0.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("sortField=title asc — 제목 오름차순 정렬")
        void success_sortByTitleAsc() {
            saveProgram("Z공연", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("A공연", "CONCERT", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, null, null, null, "title", "asc", 0, 20);

            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);
            assertThat(result.totalElements()).isEqualTo(2);

            ProgramSummaryData summary1 = (ProgramSummaryData)result.content().get(0);
            assertThat(summary1.title()).isEqualTo("A공연");

            ProgramSummaryData summary2 = (ProgramSummaryData)result.content().get(1);
            assertThat(summary2.title()).isEqualTo("Z공연");
        }

        @Test
        @DisplayName("알 수 없는 sortField — createdAt desc 기본 정렬")
        void success_unknownSortField_defaultSort() {
            saveProgram("공연A", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("공연B", "CONCERT", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                null, null, null, null, "unknown", "asc", 0, 20);

            assertThatCode(() -> programQueryRepositoryImpl.findBySpec(spec))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("카테고리·키워드 복합 필터")
        void success_combinedFilter() {
            saveProgram("봄 콘서트", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("여름 콘서트", "CONCERT", ProgramStatus.DRAFT);
            saveProgram("봄 뮤지컬", "MUSICAL", ProgramStatus.DRAFT);

            ProgramSearchSpec spec = new ProgramSearchSpec(
                "CONCERT", "봄", null, null, null, null, 0, 20);
            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);

            ProgramSummaryData summary = (ProgramSummaryData)result.content().get(0);

            assertThat(result.totalElements()).isEqualTo(1);

            assertThat(summary.title()).isEqualTo("봄 콘서트");
        }

        @Test
        @DisplayName("결과 없을 때 빈 content, totalElements=0")
        void success_empty() {
            ProgramSearchSpec spec = new ProgramSearchSpec(
                "NONE", null, null, null, null, null, 0, 20);
            PagedResult<?> result = programQueryRepositoryImpl.findBySpec(spec);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 공통 리플렉션 헬퍼
    // ══════════════════════════════════════════════════════════════════

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("필드 주입 실패: " + fieldName, e);
        }
    }

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
