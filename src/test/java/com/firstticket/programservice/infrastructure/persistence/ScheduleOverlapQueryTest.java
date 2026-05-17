package com.firstticket.programservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.firstticket.common.persistence.CommonJpaAutoConfiguration;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.infrastructure.config.JpaConfig;

/**
 * ScheduleJpaRepository.findOverlappingSchedulesWithLock() 슬라이스 테스트.
 *
 * [H2 대신 Testcontainers + PostgreSQL을 사용하는 이유]
 * btree_gist 확장과 tsrange exclusion constraint는 PostgreSQL 전용이다.
 * V1 마이그레이션 SQL에 포함된 아래 제약조건은 H2에서 실행되지 않는다.
 *   CONSTRAINT excl_schedule_venue_overlap EXCLUDE USING GIST (
 *     venue_id WITH =,
 *     tsrange(event_start_at, event_end_at, '[)') WITH &&
 *   ) WHERE (deleted_at IS NULL)
 *
 * 이 테스트는 실제 PostgreSQL 환경에서 쿼리 정확성을 검증한다.
 * Flyway로 V1, V2 마이그레이션을 적용하여 스키마를 초기화한다.
 *
 * [겹침 조건 — 반열린 구간 [start, end)]
 * 기존 스케줄의 시작이 새 종료보다 이전 AND 기존 종료가 새 시작보다 이후
 * 즉: existing.eventStartAt < newEventEndAt
 *     AND existing.eventEndAt > newEventStartAt
 *
 * [제외 조건]
 * - soft delete된 스케줄 (s.deletedAt IS NOT NULL)
 * - CANCELLED 상태의 프로그램 스케줄
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CommonJpaAutoConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.schemas=program_schema",
    "spring.flyway.create-schemas=true",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.properties.hibernate.default_schema=program_schema"
})
class ScheduleOverlapQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
            () -> "org.postgresql.Driver");
    }

    @Autowired
    private ProgramJpaRepository programJpaRepository;
    @Autowired
    private ScheduleJpaRepository scheduleJpaRepository;

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID VENUE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID VENUE_ID_2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 0, 0);

    // ── 픽스처 빌더 ──────────────────────────────────────────────────

    /**
     * Program + Schedule 픽스처 저장 헬퍼.
     * 지정한 venueId, eventStart~End로 스케줄을 가진 Program을 저장한다.
     */
    private Schedule saveSchedule(UUID venueId,
        LocalDateTime eventStart, LocalDateTime eventEnd,
        ProgramStatus programStatus) {

        Program program = Program.create(
            "테스트 공연", "CONCERT", "POP", ProgramType.SEATED, "서울", null, null);
        injectField(program, "createdBy", OWNER_ID);

        // saleStart < saleEnd < eventStart 조건 만족
        Schedule schedule = program.addSchedule(
            venueId,
            eventStart, eventEnd,
            eventStart.minusDays(30), eventStart.minusDays(1),
            500, NOW
        );
        injectField(schedule, "createdBy", OWNER_ID);

        if (programStatus != ProgramStatus.DRAFT) {
            injectField(program, "status", programStatus);
        }

        programJpaRepository.saveAndFlush(program);
        return schedule;
    }

    // ══════════════════════════════════════════════════════════════════
    // findOverlappingSchedulesWithLock()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findOverlappingSchedulesWithLock() — 시간 범위 겹침 검증")
    class FindOverlappingSchedulesWithLock {

        // 기준 스케줄: [10:00, 12:00)
        private static final LocalDateTime BASE_START =
            LocalDateTime.of(2027, 6, 1, 10, 0);
        private static final LocalDateTime BASE_END =
            LocalDateTime.of(2027, 6, 1, 12, 0);

        @Test
        @DisplayName("앞부분 겹침 — [09:00, 11:00) vs 기준 [10:00, 12:00)")
        void overlap_leadingOverlap() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 9, 0),
                LocalDateTime.of(2027, 6, 1, 11, 0));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("뒷부분 겹침 — [11:00, 13:00) vs 기준 [10:00, 12:00)")
        void overlap_trailingOverlap() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 11, 0),
                LocalDateTime.of(2027, 6, 1, 13, 0));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("완전 포함 — [09:00, 13:00) vs 기준 [10:00, 12:00)")
        void overlap_fullyContains() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 9, 0),
                LocalDateTime.of(2027, 6, 1, 13, 0));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("완전 동일 — [10:00, 12:00) vs 기준 [10:00, 12:00)")
        void overlap_exactSame() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID, BASE_START, BASE_END);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("경계값 — 새 종료 = 기존 시작 [08:00, 10:00) vs 기준 [10:00, 12:00) — 미겹침")
        void noOverlap_newEndEqualsExistingStart() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 8, 0),
                LocalDateTime.of(2027, 6, 1, 10, 0)); // 새 종료 = 기존 시작

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("경계값 — 새 시작 = 기존 종료 [12:00, 14:00) vs 기준 [10:00, 12:00) — 미겹침")
        void noOverlap_newStartEqualsExistingEnd() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 12, 0), // 새 시작 = 기존 종료
                LocalDateTime.of(2027, 6, 1, 14, 0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("완전 이전 — [07:00, 09:00) vs 기준 [10:00, 12:00) — 미겹침")
        void noOverlap_completelyBefore() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 7, 0),
                LocalDateTime.of(2027, 6, 1, 9, 0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("완전 이후 — [13:00, 15:00) vs 기준 [10:00, 12:00) — 미겹침")
        void noOverlap_completelyAfter() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 13, 0),
                LocalDateTime.of(2027, 6, 1, 15, 0));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 공연장 스케줄은 포함되지 않음 — venueId 격리")
        void noOverlap_differentVenue() {
            saveSchedule(VENUE_ID_2, BASE_START, BASE_END, ProgramStatus.ON_SALE);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID, BASE_START, BASE_END);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CANCELLED 프로그램 스케줄은 포함되지 않음 — 취소된 프로그램 제외")
        void noOverlap_cancelledProgram() {
            // 도메인 규칙: DRAFT → CANCELLED 직접 전이 불가이므로
            // 리플렉션으로 CANCELLED 상태 직접 주입
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.CANCELLED);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID, BASE_START, BASE_END);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("soft delete된 스케줄은 포함되지 않음")
        void noOverlap_softDeletedSchedule() {
            Schedule schedule = saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.ON_SALE);
            injectField(schedule, "deletedAt", LocalDateTime.now());
            scheduleJpaRepository.saveAndFlush(schedule);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID, BASE_START, BASE_END);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DRAFT 상태 프로그램 스케줄은 포함됨 — CANCELLED 외 모든 상태 대상")
        void overlap_draftProgram() {
            saveSchedule(VENUE_ID, BASE_START, BASE_END, ProgramStatus.DRAFT);

            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID, BASE_START, BASE_END);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("겹치는 스케줄 여러 개 — 모두 반환")
        void overlap_multiple() {
            // 같은 공연장이지만 서로 시간대가 전혀 겹치지 않는 스케줄 2개를 저장
            saveSchedule(VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 10, 0),
                LocalDateTime.of(2027, 6, 1, 12, 0),
                ProgramStatus.ON_SALE); // 스케줄 A

            saveSchedule(VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 13, 0),
                LocalDateTime.of(2027, 6, 1, 15, 0),
                ProgramStatus.ON_SALE); // 스케줄 B (A와 안 겹침)

            // 이제 새롭게 들어오려는 스케줄(또는 예약 요청) 범위를 [09:00, 16:00)으로 크게 잡으면
            // 기존에 존재하던 스케줄 A[10~12]와 스케줄 B[13~15]가 모두 이 범위 안에 '포함(겹침)'
            List<Schedule> result = scheduleJpaRepository.findOverlappingSchedulesWithLock(
                VENUE_ID,
                LocalDateTime.of(2027, 6, 1, 9, 0),
                LocalDateTime.of(2027, 6, 1, 16, 0));

            assertThat(result).hasSize(2);
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
