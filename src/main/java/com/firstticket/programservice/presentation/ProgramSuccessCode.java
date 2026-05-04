package com.firstticket.programservice.presentation;

import org.springframework.http.HttpStatus;

import com.firstticket.common.response.SuccessCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProgramSuccessCode implements SuccessCode {

    // ---- Program -------------------------------------------------
    PROGRAM_CREATED(HttpStatus.CREATED, "프로그램이 등록되었습니다"),
    PROGRAM_UPDATED(HttpStatus.OK, "프로그램이 수정되었습니다"),
    PROGRAM_DELETED(HttpStatus.OK, "프로그램이 삭제되었습니다"),
    PROGRAM_FOUND(HttpStatus.OK, "프로그램을 조회했습니다"),
    PROGRAM_LIST_FOUND(HttpStatus.OK, "프로그램 목록을 조회했습니다"),
    PROGRAM_PUBLISHED(HttpStatus.OK, "프로그램이 판매 시작되었습니다"),
    PROGRAM_CANCELLED(HttpStatus.OK, "프로그램이 취소되었습니다"),
    PROGRAM_CLOSED(HttpStatus.OK, "프로그램이 종료되었습니다"),

    // ---- Schedule --------------------------------------------------------
    SCHEDULE_CREATED(HttpStatus.CREATED, "스케줄이 등록되었습니다"),
    SCHEDULE_UPDATED(HttpStatus.OK, "스케줄이 수정되었습니다"),
    SCHEDULE_DELETED(HttpStatus.OK, "스케줄이 삭제되었습니다"),
    SCHEDULE_FOUND(HttpStatus.OK, "스케줄을 조회했습니다"),
    SCHEDULE_LIST_FOUND(HttpStatus.OK, "스케줄 목록을 조회했습니다"),

    // ---- PriceGrade --------------------------------------------------------
    PRICE_GRADE_CREATED(HttpStatus.CREATED, "가격 등급이 추가되었습니다"),
    PRICE_GRADE_DELETED(HttpStatus.OK, "가격 등급이 삭제되었습니다"),
    PRICE_GRADE_LIST_FOUND(HttpStatus.OK, "가격 등급 목록을 조회했습니다"),

    SCHEDULE_BOOKING_INFO_FOUND(HttpStatus.OK, "스케줄 예매 정보를 조회했습니다");

    private final HttpStatus status;
    private final String message;
}
