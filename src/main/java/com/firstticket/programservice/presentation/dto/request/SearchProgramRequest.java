package com.firstticket.programservice.presentation.dto.request;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.firstticket.programservice.application.dto.query.ProgramSearchQuery;

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
    int page,
    int size
) {
    public ProgramSearchQuery toQuery() {
        return new ProgramSearchQuery(
            category, keyword, region, date,
            sort, direction, page, size
        );
    }
}
