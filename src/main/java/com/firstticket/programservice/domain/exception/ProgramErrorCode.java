package com.firstticket.programservice.domain.exception;

import org.springframework.http.HttpStatus;

import com.firstticket.common.response.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로그램 도메인 에러 코드 정의
 * HTTP 상태 코드와 사용자에게 보여줄 메시지를 관리합니다.
 * 공통 인터페이스인 ErrorCode를 구현하여 GlobalExceptionHandler에서 처리될 수 있도록 합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ProgramErrorCode implements ErrorCode {

    // --- 프로그램(Program) 관련 ---

    INVALID_PROGRAM_ID(HttpStatus.BAD_REQUEST,
        "프로그램 ID는 필수입니다"),

    /** 요청한 식별자의 프로그램을 찾을 수 없음 */
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND,
        "프로그램을 찾을 수 없습니다"),

    /**
     * 수정이 불가한 상태에서의 수정 시도.
     * ON_SALE 상태에서 제목·카테고리 수정, SOLD_OUT·CLOSED·CANCELLED 상태에서 수정 시도 등
     */
    PROGRAM_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY,
        "현재 상태에서는 수정할 수 없습니다"),

    /**
     * 삭제가 불가한 상태에서의 삭제 시도.
     * ON_SALE·CANCELLED 상태에서 프로그램 삭제 시도
     */
    PROGRAM_NOT_DELETABLE_IN_PROCESS(HttpStatus.UNPROCESSABLE_ENTITY,
        "예매 내역이 있는 프로그램은 삭제할 수 없습니다. CANCELLED 처리 후 환불 절차를 거쳐야 합니다"),

    /**
     * 삭제가 불가한 상태에서의 삭제 시도.
     * SOLD_OUT 상태에서 프로그램 삭제 시도
     */
    PROGRAM_NOT_DELETABLE(HttpStatus.UNPROCESSABLE_ENTITY,
        "판매가 시작된 이후 CLOSE 처리를 거치지 않은 프로그램은 삭제할 수 없습니다."),

    /**
     * ProgramStatus 전이 규칙에 위배되는 상태 변경 시도.
     * 허용되지 않은 전이 예: CANCELLED → ON_SALE, CLOSED → DRAFT 등
     * 전이 규칙은 ProgramStatus.ALLOWED 맵에서 관리한다.
     */
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY,
        "잘못된 상태 전이입니다"),

    /** 프로그램 제목이 null이거나 공백인 경우 */
    INVALID_TITLE(HttpStatus.BAD_REQUEST,
        "프로그램 제목은 필수입니다"),

    /** 카테고리가 null이거나 공백인 경우 */
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST,
        "카테고리는 필수입니다"),

    /** 테마가 null이거나 공백인 경우 */
    INVALID_THEME(HttpStatus.BAD_REQUEST,
        "테마는 필수입니다"),

    /** 프로그램 타입(SEATED·STANDING·FREE)이 null인 경우 */
    INVALID_PROGRAM_TYPE(HttpStatus.BAD_REQUEST,
        "프로그램 타입은 필수입니다"),

    INVALID_REGION(HttpStatus.BAD_REQUEST, "지역은 필수입니다"),

    // --- 스케줄(Schedule) 관련 ---

    INVALID_SCHEDULE_ID(HttpStatus.BAD_REQUEST,
        "유효한 스케줄 ID가 필요합니다"),  // ← 메시지 수정 — 입력 오류·미소속 양쪽에 적합

    SCHEDULE_DOES_NOT_BELONG_TO_PROGRAM(HttpStatus.BAD_REQUEST,
        "해당 스케줄은 이 프로그램에 속하지 않습니다"),
    /**
     * 스케줄 없이 판매 시작(ON_SALE) 전이를 시도한 경우.
     * 최소 한 개 이상의 스케줄이 등록되어야 publish() 호출 가능
     */
    SCHEDULE_REQUIRED(HttpStatus.BAD_REQUEST,
        "스케줄이 없는 프로그램은 판매 시작할 수 없습니다"),

    /** 요청한 식별자의 스케줄을 찾을 수 없음 */
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND,
        "스케줄을 찾을 수 없습니다"),

    /**
     * 동일 공연장, 동일 시간대에 이미 다른 스케줄이 등록되어 있는 경우 (V-04).
     * DB의 exclusion constraint(tsrange)와 Application 계층 비관적 잠금으로 이중 방어
     */
    VENUE_TIME_CONFLICT(HttpStatus.CONFLICT,
        "해당 공연장에 이미 예약된 일정이 있습니다"),

    /**
     * CANCELLED·CLOSED 상태 프로그램의 스케줄 수정 시도.
     * 종료된 프로그램의 스케줄은 어떤 필드도 수정할 수 없음
     */
    SCHEDULE_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY,
        "종료된 프로그램의 스케줄은 수정할 수 없습니다"),

    /**
     * ON_SALE·SOLD_OUT 상태에서 판매 기간·공연장 변경 시도.
     * 예매가 진행 중이므로 saleStartAt·saleEndAt·venueId는 변경 불가.
     * eventStartAt·eventEndAt·totalCapacity는 수정 가능
     */
    SCHEDULE_SALE_INFO_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY,
        "판매 중인 스케줄의 공연장·판매 기간은 수정할 수 없습니다"),

    /**
     * DRAFT 이외의 상태에서 스케줄 삭제 시도.
     * ON_SALE 이후에는 예매가 진행 중일 수 있으므로
     * 스케줄 단독 삭제 대신 Program 전체를 CANCELLED 처리해야 한다.
     */
    SCHEDULE_NOT_DELETABLE(HttpStatus.UNPROCESSABLE_ENTITY,
        "DRAFT 상태에서만 스케줄을 삭제할 수 있습니다"),

    /**
     * 수용 인원이 0 이하인 경우.
     * totalCapacity(스케줄 전체 인원) 및
     * ScheduleSectionCapacity.capacity(구역별 허용 인원) 모두 적용된다.
     */
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST,
        "수용 인원은 1명 이상이어야 합니다"),

    /** 공연장 ID(venueId)가 null인 경우 */
    INVALID_VENUE_ID(HttpStatus.BAD_REQUEST,
        "공연장 ID는 필수입니다"),

    // --- 일시/기간(Period) 관련 -------
    /**
     * 공연 시작 일시가 null이거나 종료 일시보다 같거나 늦은 경우.
     * eventStartAt < eventEndAt 조건을 만족해야 함
     */
    INVALID_EVENT_PERIOD(HttpStatus.BAD_REQUEST,
        "공연 시작 일시는 종료 일시 이전이어야 합니다"),

    /**
     * 판매 시작 일시가 null이거나 종료 일시보다 같거나 늦은 경우.
     * saleStartAt < saleEndAt 조건을 만족해야 한다.
     */
    INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST,
        "판매 시작 일시는 종료 일시 이전이어야 합니다"),

    /**
     * 판매 종료 일시가 공연 시작 일시 이후로 설정된 경우.
     * saleEndAt < eventStartAt 조건을 만족해야 함
     * 온라인 예매는 공연 시작 전에 마감되어야 하므로 이 제약이 필요함
     */
    SALE_END_AFTER_EVENT_START(HttpStatus.BAD_REQUEST,
        "판매 종료 일시는 공연 시작 일시 이전이어야 합니다"),

    /**
     * 공연 시작 일시가 현재 시각보다 이전인 경우.
     * 스케줄 생성(create()) 시점에만 검증
     * 수정(update()) 시에는 검증하지 않음
     * (기존 스케줄의 eventStartAt이 수정 없이 그대로 유지될 때 과거 판정 방지)
     * Presentation 계층의 @FutureOrPresent와 이중 방어
     */
    PAST_EVENT_START(HttpStatus.BAD_REQUEST,
        "공연 시작 일시는 현재 시각 이후여야 합니다"),

    // --- 가격 등급(PriceGrade) 관련 ---
    /**
     * 동일한 스케줄 내에 이미 같은 등급명(VIP·R석 등)이 존재하는 경우.
     * 중복 검증 기준: (scheduleId, gradeLabel) 조합
     */
    PRICE_GRADE_DUPLICATE(HttpStatus.CONFLICT,
        "이미 존재하는 등급명입니다"),

    /** 삭제·조회하려는 등급명이 해당 스케줄에 존재하지 않는 경우 */
    PRICE_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND,
        "존재하지 않는 등급명입니다"),

    /** 가격이 음수인 경우. 0원(무료 공연)은 허용한다. */
    INVALID_PRICE(HttpStatus.BAD_REQUEST,
        "가격은 0원 이상이어야 합니다"),

    /** 등급명이 null이거나 공백인 경우 */
    INVALID_GRADE_LABEL(HttpStatus.BAD_REQUEST,
        "등급명은 필수입니다"),

    // ---- 공연장 관련 ----
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND,
        "공연장을 찾을 수 없습니다"),

    /**
     * SEATED·STANDING 타입인데 sectionId가 null인 경우.
     * 두 타입은 구역별 등급 설정이 필수이므로 sectionId가 있어야 한다.
     */
    SECTION_ID_REQUIRED(HttpStatus.BAD_REQUEST,
        "SEATED/STANDING 타입은 sectionId가 필수입니다"),

    /**
     * FREE 타입인데 sectionId가 null이 아닌 경우.
     * FREE 타입은 구역 구분 없이 단일 가격으로 운영하므로
     * sectionId 설정이 의미 없고 데이터 모순을 야기한다.
     */
    SECTION_ID_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
        "FREE 타입은 sectionId를 설정할 수 없습니다"),

    // -- ScheduleSectionCapacity -----------
    /** 구역 ID(sectionId)가 null인 경우 */
    INVALID_SECTION_ID(HttpStatus.BAD_REQUEST,
        "구역 ID는 필수입니다"),

    /**
     * SEATED 타입 스케줄에서 구역별 인원 설정을 시도한 경우.
     * SEATED는 VenueSeat(물리 고정 좌석) 기반이므로
     * ScheduleSectionCapacity가 필요하지 않다.
     */
    SECTION_CAPACITY_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY,
        "SEATED 타입 스케줄에는 구역별 인원을 설정할 수 없습니다"),

    /**
     * 동일한 스케줄 내에 이미 같은 구역(sectionId)이 등록되어 있는 경우.
     * 한 스케줄 내에서 동일 구역은 하나만 존재해야 한다.
     */
    SECTION_CAPACITY_DUPLICATE(HttpStatus.CONFLICT,
        "이미 등록된 구역입니다"),

    /** 구역이 해당 스케줄에 등록되어 있지 않은 경우 */
    SECTION_CAPACITY_NOT_FOUND(HttpStatus.NOT_FOUND,
        "등록되지 않은 구역입니다"),

    /**
     * 한 구역의 수용 인원 < 한 구역에 대한 요청 인원알 경우
     */
    SECTION_CAPACITY_EXCEEDS_VENUE_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY,
        "요청 인원이 공연장 구역의 최대 수용 인원을 초과합니다"),

    /**
     *  모든 구역의 인원 > 회차 총 수용 인원인 경우.
     * sectionCapacities 합계는 totalCapacity를 넘을 수 없다.
     * 초과 시 예매 가능 수 계산이 깨진다.
     */
    SECTION_CAPACITY_EXCEEDS_TOTAL(HttpStatus.UNPROCESSABLE_ENTITY,
        "구역별 인원 합계가 회차 총 수용 인원을 초과할 수 없습니다"),

    EXTERNAL_SERVICE_FAILURE(HttpStatus.SERVICE_UNAVAILABLE,
        "외부 서비스 호출에 실패했습니다"),
    /**
     * totalCapacity 축소 시 기존 sectionCapacities 합계가
     * 새 totalCapacity보다 큰 경우.
     * 이 상태가 되면 예매 가능 수 계산이 깨지기 때문에,
     * 구역별 인원을 먼저 줄인 후 totalCapacity를 수정해야 함!!!!
     */
    TOTAL_CAPACITY_LESS_THAN_SECTION_SUM(HttpStatus.UNPROCESSABLE_ENTITY,
        "총 수용 인원이 구역별 인원 합계보다 작을 수 없습니다. 구역별 인원을 먼저 조정해주세요"),

    // ---- PageNation 관련 ------
    INVALID_SEARCH_QUERY(HttpStatus.BAD_REQUEST,
        "검색 조건은 필수입니다"),

    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST,
        "페이지 크기는 1 이상이어야 합니다"),

    INVALID_PAGE_NUMBER(HttpStatus.BAD_REQUEST,
        "페이지 번호는 0 이상이어야 합니다");

    private final HttpStatus status;
    private final String message;
}
