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
    /** 요청한 식별자의 프로그램을 찾을 수 없음 */
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "프로그램을 찾을 수 없습니다"),
    /** 판매 중이거나 종료된 공연 등 수정이 불가한 상태에서의 수정 시도 */
    PROGRAM_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY, "현재 상태에서는 수정할 수 없습니다"),
    /** 도메인 상태 관리(ProgramStatus) 규칙에 위배되는 상태 변경 시도 */
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "잘못된 상태 전이입니다"),
    INVALID_TITLE(HttpStatus.BAD_REQUEST, "프로그램 제목은 필수입니다"),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리는 필수입니다"),
    INVALID_THEME(HttpStatus.BAD_REQUEST, "테마는 필수입니다"),
    INVALID_PROGRAM_TYPE(HttpStatus.BAD_REQUEST, "프로그램 타입은 필수입니다"),

    // --- 스케줄(Schedule) 관련 ---
    /** 최소 한 개 이상의 회차가 등록되어야 판매 가능(ON_SALE)으로 변경 가능 */
    SCHEDULE_REQUIRED(HttpStatus.BAD_REQUEST, "스케줄이 없는 프로그램은 판매 시작할 수 없습니다"),
    /** 요청한 식별자의 회차 정보를 찾을 수 없음 */
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "스케줄을 찾을 수 없습니다"),
    /** 동일 공연장, 동일 시간에 이미 다른 공연 회차가 등록되어 있는 경우 */
    VENUE_TIME_CONFLICT(HttpStatus.CONFLICT, "해당 공연장에 이미 예약된 일정이 있습니다"),
    SCHEDULE_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY, "종료된 프로그램의 스케줄은 수정할 수 없습니다"),
    SCHEDULE_SALE_INFO_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY, "판매 중인 스케줄의 공연장·판매 기간은 수정할 수 없습니다"),
    SCHEDULE_NOT_DELETABLE(HttpStatus.UNPROCESSABLE_ENTITY, "DRAFT 상태에서만 스케줄을 삭제할 수 있습니다"),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "수용 인원은 1명 이상이어야 합니다"),
    INVALID_VENUE_ID(HttpStatus.BAD_REQUEST, "공연장 ID는 필수입니다"),

    // --- 일시/기간(Period) 관련 ---
    /** 공연 종료 시각이 시작 시각보다 빠르거나 같은 경우 */
    INVALID_EVENT_PERIOD(HttpStatus.BAD_REQUEST, "공연 시작 일시는 종료 일시 이전이어야 합니다"),
    /** 판매 종료 시각이 시작 시각보다 빠르거나 같은 경우 */
    INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST, "판매 시작 일시는 종료 일시 이전이어야 합니다"),
    /** 티켓 판매 마감이 공연 시작 이후로 설정된 경우 (온라인 예매 마감 기준) */
    SALE_END_AFTER_EVENT_START(HttpStatus.BAD_REQUEST, "판매 종료 일시는 공연 시작 일시 이전이어야 합니다"),
    PAST_EVENT_START(HttpStatus.BAD_REQUEST, "공연 시작 일시는 현재 시각 이후여야 합니다"),

    // --- 가격 등급(PriceGrade) 관련 ---
    /** 동일한 공연 회차 내에 이미 같은 이름(VIP, R석 등)의 등급이 존재하는 경우 */
    PRICE_GRADE_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 등급명입니다"),
    /** 삭제하거나 조회하려는 등급명이 존재하지 않는 경우 */
    PRICE_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 등급명입니다"),
    /** 입력된 가격이 음수인 경우 */
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "가격은 0원 이상이어야 합니다"),
    /** 입력된 등급명이 존재하지 않거나 공백인 경우 */
    INVALID_GRADE_LABEL(HttpStatus.BAD_REQUEST, "등급명은 필수입니다"),

    /** 프로그램 타입에 맞지 않는 sectionId 설정을 시도할 경우 */
    SECTION_ID_REQUIRED(HttpStatus.BAD_REQUEST, "SEATED/STANDING 타입은 sectionId가 필수입니다"),
    SECTION_ID_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FREE 타입은 sectionId를 설정할 수 없습니다"),

    // -- ScheduleSectionCapacity -----------
    INVALID_SECTION_ID(HttpStatus.BAD_REQUEST, "구역 ID는 필수입니다"),
    SECTION_CAPACITY_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "SEATED 타입 스케줄에는 구역별 인원을 설정할 수 없습니다"),
    SECTION_CAPACITY_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 구역입니다"),
    SECTION_CAPACITY_NOT_FOUND(HttpStatus.NOT_FOUND, "등록되지 않은 구역입니다"),
    /**
     * 구역별 인원 합계가 회차 총 수용 인원을 초과하는 경우.
     * sectionCapacities 합계는 totalCapacity를 넘을 수 없음
     * 초과 시 예매 가능 수 계산이 깨짐
     */
    SECTION_CAPACITY_EXCEEDS_TOTAL(HttpStatus.UNPROCESSABLE_ENTITY, "구역별 인원 합계가 회차 총 수용 인원을 초과할 수 없습니다");

    private final HttpStatus status;
    private final String message;
}
