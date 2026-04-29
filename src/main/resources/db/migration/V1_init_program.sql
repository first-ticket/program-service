-- =====================================================
-- Program Service — V1 초기 스키마
-- schema: program
-- Spring 설정: spring.jpa.properties.hibernate.default_schema=program
-- =====================================================

-- ── btree_gist 확장 ──────────────────────────────────
-- tsrange exclusion constraint에 필요
-- exclusion constraint: (venue_id, event 범위 겹침) 방지 (V-04)
CREATE
EXTENSION IF NOT EXISTS btree_gist;


-- ── p_program ────────────────────────────────────────
CREATE TABLE p_program
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(100) NOT NULL,
    theme       VARCHAR(100) NOT NULL,

    -- ProgramType: SEATED | STANDING | FREE
    type        VARCHAR(20)  NOT NULL,

    -- ProgramStatus: DRAFT | ON_SALE | SOLD_OUT | CANCELLED | CLOSED
    -- 상태 전이 규칙은 ProgramStatus.ALLOWED 맵에서 관리
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    region      VARCHAR(100) NOT NULL,

    poster_url  TEXT,
    description TEXT,

    -- BaseUserEntity Auditing
    created_at  TIMESTAMP    NOT NULL,
    created_by  UUID         NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMP, -- soft delete
    deleted_by  UUID,

    CONSTRAINT pk_program PRIMARY KEY (id),
    CONSTRAINT chk_program_type
        CHECK (type IN ('SEATED', 'STANDING', 'FREE')),
    CONSTRAINT chk_program_status
        CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CANCELLED', 'CLOSED'))
);

-- 목록 조회 필터 인덱스 (P-04)
-- partial index: soft delete된 레코드 제외
CREATE INDEX idx_program_category ON p_program (category) WHERE deleted_at IS NULL;
CREATE INDEX idx_program_status ON p_program (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_program_type ON p_program (type) WHERE deleted_at IS NULL;
CREATE INDEX idx_program_region ON p_program (region) WHERE deleted_at IS NULL;

-- ── p_schedule ───────────────────────────────────────
CREATE TABLE p_schedule
(
    id             UUID      NOT NULL DEFAULT gen_random_uuid(),
    program_id     UUID      NOT NULL,

    -- MSA 경계 유지: Venue Service의 ID를 값으로만 참조 (FK 없음)
    venue_id       UUID      NOT NULL,

    -- 실제 공연 진행 시간 (V-04 공연장 중복 검증 기준)
    event_start_at TIMESTAMP NOT NULL,
    event_end_at   TIMESTAMP NOT NULL,

    -- 티켓 판매 기간 (saleEndAt < eventStartAt 보장)
    sale_start_at  TIMESTAMP NOT NULL,
    sale_end_at    TIMESTAMP NOT NULL,

    total_capacity INT       NOT NULL,

    -- 낙관적 락: addSectionCapacity() 동시 호출 시
    -- sectionCapacities 합계 ≤ totalCapacity 불변식 보호
    version        BIGINT    NOT NULL DEFAULT 0,

    -- BaseUserEntity Auditing
    created_at     TIMESTAMP NOT NULL,
    created_by     UUID      NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     UUID,
    deleted_at     TIMESTAMP, -- soft delete
    deleted_by     UUID,

    CONSTRAINT pk_schedule PRIMARY KEY (id),
    CONSTRAINT fk_schedule_program
        FOREIGN KEY (program_id) REFERENCES p_program (id),
    CONSTRAINT chk_schedule_event_period
        CHECK (event_start_at < event_end_at),
    CONSTRAINT chk_schedule_sale_period
        CHECK (sale_start_at < sale_end_at),
    CONSTRAINT chk_schedule_sale_before_event
        CHECK (sale_end_at < event_start_at),
    CONSTRAINT chk_schedule_capacity
        CHECK (total_capacity > 0),

    -- 공연장 시간 범위 겹침 방지 (V-04)
    -- uk_schedule_venue_time 단순 유니크 제약 대신 exclusion constraint 사용
    -- 이유: 완전히 동일한 (venue_id, start, end)만 차단하는 유니크와 달리
    --       10:00-12:00 / 11:00-13:00 처럼 겹치는 구간도 차단
    -- soft delete된 스케줄은 검증 제외
    CONSTRAINT     excl_schedule_venue_overlap EXCLUDE USING GIST (
            venue_id WITH =,
            tsrange(event_start_at, event_end_at, '[)') WITH &&
        ) WHERE (deleted_at IS NULL)
);

-- 프로그램별 스케줄 조회 인덱스
CREATE INDEX idx_schedule_program_id
    ON p_schedule (program_id) WHERE deleted_at IS NULL;

-- 공연장별 스케줄 조회 인덱스
-- V-04 중복 검증 비관적 락 쿼리(findOverlappingSchedulesWithLock)에서 사용
CREATE INDEX idx_schedule_venue_id
    ON p_schedule (venue_id) WHERE deleted_at IS NULL;


-- ── price_grade ──────────────────────────────────────
-- PriceGrade는 도메인 모델 상 VO이지만
-- gradeLabel 기준 개별 삭제 쿼리가 필요하여 별도 테이블로 관리
-- PriceGradeRepository를 별도로 두지 않음
-- id는 DB 매핑용 기술적 PK (도메인 식별자 아님)
CREATE TABLE price_grade
(
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    schedule_id UUID        NOT NULL,

    -- FREE 타입은 sectionId null 허용 (구역 구분 없이 단일 가격)
    -- SEATED·STANDING은 sectionId 필수 (구역별 등급 설정)
    section_id  UUID,

    grade_label VARCHAR(50) NOT NULL,
    price       INT         NOT NULL,

    -- BaseEntity Auditing (생성자 정보 불필요)
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP,

    CONSTRAINT pk_price_grade PRIMARY KEY (id),
    CONSTRAINT fk_price_grade_schedule
        FOREIGN KEY (schedule_id) REFERENCES p_schedule (id),
    CONSTRAINT chk_price_grade_price
        CHECK (price >= 0) -- 0원(무료 공연) 허용
);

-- partial unique index로 대체: 삭제되지 않은 행에만 유니크 보장
CREATE UNIQUE INDEX uk_price_grade_active
    ON price_grade (schedule_id, grade_label) WHERE deleted_at IS NULL;

CREATE INDEX idx_price_grade_schedule_id
    ON price_grade (schedule_id);


-- ── schedule_section_capacity ────────────────────────
-- STANDING·FREE 프로그램 전용: 구역별 허용 인원 목록
-- ScheduleSectionCapacity VO → @ElementCollection으로 관리
-- SEATED는 VenueSeat 기반이므로 이 테이블을 사용하지 않음
--
-- 불변식: SUM(capacity) ≤ p_schedule.total_capacity
-- 위반 시 예매 가능 수 계산이 깨짐
-- → addSectionCapacity() 도메인 메서드에서 합계 검증
-- → @Version 낙관적 락으로 동시 요청 보호
CREATE TABLE schedule_section_capacity
(
    schedule_id UUID NOT NULL,

    -- MSA 경계 유지: Venue Service의 Section ID를 값으로만 참조 (FK 없음)
    section_id  UUID NOT NULL,

    -- 이 회차에서 해당 구역에 허용하는 인원
    -- Venue Section.capacity(공연장 상한선)를 초과 불가
    -- 초과 여부는 Application 계층(CreateScheduleUseCase)에서 VenueClient로 검증
    capacity    INT  NOT NULL,

    CONSTRAINT fk_ssc_schedule
        FOREIGN KEY (schedule_id) REFERENCES p_schedule (id),
    CONSTRAINT chk_ssc_capacity
        CHECK (capacity > 0),

    -- 동시 요청 시 중복 행 원천 차단
    -- addSectionCapacity()의 메모리 검사와 이중 방어
    CONSTRAINT uk_schedule_section_capacity
        UNIQUE (schedule_id, section_id)
);

CREATE INDEX idx_ssc_schedule_id
    ON schedule_section_capacity (schedule_id);
