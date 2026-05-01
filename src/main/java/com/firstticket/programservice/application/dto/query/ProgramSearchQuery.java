package com.firstticket.programservice.application.dto.query;

import java.time.LocalDate;

import com.firstticket.programservice.domain.query.ProgramSearchSpec;

/**
 * 프로그램 목록 조회 쿼리 DTO.
 * Presentation 계층의 SearchProgramRequest.toQuery()로 생성된다.
 * toSpec()으로 도메인 계층의 ProgramSearchSpec으로 변환한다.
 */
public record ProgramSearchQuery(
    String category,
    String keyword,
    String region,
    LocalDate date,
    String sortField,
    String direction,
    int pageNumber,
    int pageSize
) {
    /**
     * 도메인 계층의 조회 조건 DTO로 변환한다.
     * Application → Domain 변환은 Application 계층에서 처리한다.
     */
    public ProgramSearchSpec toSpec() {
        return new ProgramSearchSpec(
            category, keyword, region, date,
            sortField, direction, pageNumber, pageSize
        );
    }
}
