-- =====================================================
-- Program Service — H2 테스트용 스키마
-- PostgreSQL 전용 문법을 H2 호환 문법으로 대체
--
-- 주요 차이점:
-- gen_random_uuid() → RANDOM_UUID()
-- CREATE EXTENSION  → 제거 (H2 미지원)
-- EXCLUDE USING GIST → 단순 UNIQUE로 대체 (H2 미지원)
--   범위 겹침 검증은 Application 계층에서 처리
-- partial index (WHERE절) → 일반 인덱스로 대체 (H2 미지원)
-- =====================================================

CREATE TABLE p_program
(
    id          UUID         NOT NULL DEFAULT RANDOM_UUID(),
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(100) NOT NULL,
    theme       VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL
        CHECK (type IN ('SEATED', 'STANDING', 'FREE')),
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CANCELLED', 'CLOSED')),
    poster_url  VARCHAR(2000),
    description VARCHAR(4000),

    created_at  TIMESTAMP    NOT NULL,
    created_by  UUID         NOT NULL,
    updated_at  TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMP,
    deleted_by  UUID,

    CONSTRAINT pk_program PRIMARY KEY (id)
);

CREATE INDEX idx_program_category ON p_program (category);
CREATE INDEX idx_program_status ON p_program (status);
CREATE INDEX idx_program_type ON p_program (type);


CREATE TABLE p_schedule
(
    id             UUID      NOT NULL DEFAULT RANDOM_UUID(),
    program_id     UUID      NOT NULL,
    venue_id       UUID      NOT NULL,
    event_start_at TIMESTAMP NOT NULL,
    event_end_at   TIMESTAMP NOT NULL,
    sale_start_at  TIMESTAMP NOT NULL,
    sale_end_at    TIMESTAMP NOT NULL,
    total_capacity INT       NOT NULL CHECK (total_capacity > 0),
    version        BIGINT    NOT NULL DEFAULT 0,

    created_at     TIMESTAMP NOT NULL,
    created_by     UUID      NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     UUID,
    deleted_at     TIMESTAMP,
    deleted_by     UUID,

    CONSTRAINT pk_schedule PRIMARY KEY (id),
    CONSTRAINT fk_schedule_program
        FOREIGN KEY (program_id) REFERENCES p_program (id),
    CONSTRAINT chk_schedule_event_period
        CHECK (event_start_at < event_end_at),
    CONSTRAINT chk_schedule_sale_period
        CHECK (sale_start_at < sale_end_at),
    CONSTRAINT chk_schedule_sale_before_event
        CHECK (sale_end_at <= event_start_at),

    -- exclusion constraint 대체: 완전히 동일한 (venue_id, 시작, 종료)만 차단
    -- 범위 겹침은 Application 계층에서 검증
    -- 동시성 시나리오는 실 PostgreSQL(Testcontainers)에서 검증
    CONSTRAINT uk_schedule_venue_time
        UNIQUE (venue_id, event_start_at, event_end_at)
);

CREATE INDEX idx_schedule_program_id ON p_schedule (program_id);
CREATE INDEX idx_schedule_venue_id ON p_schedule (venue_id);


CREATE TABLE price_grade
(
    id          UUID        NOT NULL DEFAULT RANDOM_UUID(),
    schedule_id UUID        NOT NULL,
    section_id  UUID,
    grade_label VARCHAR(50) NOT NULL,
    price       INT         NOT NULL CHECK (price >= 0),

    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP,

    CONSTRAINT pk_price_grade PRIMARY KEY (id),
    CONSTRAINT fk_price_grade_schedule
        FOREIGN KEY (schedule_id) REFERENCES p_schedule (id),
    CONSTRAINT uk_price_grade_schedule_label
        UNIQUE (schedule_id, grade_label)
);

CREATE INDEX idx_price_grade_schedule_id ON price_grade (schedule_id);


CREATE TABLE schedule_section_capacity
(
    schedule_id UUID NOT NULL,
    section_id  UUID NOT NULL,
    capacity    INT  NOT NULL CHECK (capacity > 0),

    CONSTRAINT fk_ssc_schedule
        FOREIGN KEY (schedule_id) REFERENCES p_schedule (id),
    CONSTRAINT uk_schedule_section_capacity
        UNIQUE (schedule_id, section_id)
);

CREATE INDEX idx_ssc_schedule_id ON schedule_section_capacity (schedule_id);
