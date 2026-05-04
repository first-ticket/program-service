package com.firstticket.programservice.presentation.dto.request;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.firstticket.programservice.application.dto.query.ProgramSearchQuery;

import jakarta.validation.constraints.Min;

/**
 * 프로그램 목록 조회 요청 DTO.
 * toQuery()로 Application 계층의 ProgramSearchQuery로 변환한다.
 */
public record SearchProgramRequest(
    String category,
    String keyword,
    String region,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate date,

    String sort,
    String direction,
    @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
    int page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    int size
) {
    public ProgramSearchQuery toQuery() {
        // 방어적 검증 — @Valid가 우회된 경우에도 차단
        if (page < 0)
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        if (size <= 0)
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다.");
        return new ProgramSearchQuery(
            category, keyword, region, date, sort, direction, page, size
        );
    }
}
