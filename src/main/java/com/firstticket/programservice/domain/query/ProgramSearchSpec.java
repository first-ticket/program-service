package com.firstticket.programservice.domain.query;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;

/**
 * 프로그램 목록 조회 조건 도메인 DTO.
 *
 * sortField 허용값: "title" | "category" | "saleEndAt" | "createdAt"
 * direction 허용값: "asc" | "desc"
 * 허용되지 않은 값은 QueryRepository에서 default 정렬로 fallback된다.
 */
public record ProgramSearchSpec(
    String category,
    String keyword,
    String region,
    LocalDate date,
    String sortField,
    String direction,
    Pageable pageable
) {
}
