package com.firstticket.programservice.domain.query;

import java.util.List;

/**
 * 순수 도메인 페이지네이션 VO.
 * Spring Data의 Page 의존성을 도메인 계층에서 제거하기 위해 도입한다.
 * infrastructure 계층에서 Spring Page → PagedResult 변환을 담당한다.
 */
public record PagedResult<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize
) {
    public static <T> PagedResult<T> of(List<T> content, long totalElements,
        int pageNumber, int pageSize) {
        int totalPages = pageSize == 0 ? 0 : (int)Math.ceil((double)totalElements / pageSize);
        return new PagedResult<>(content, totalElements, totalPages, pageNumber, pageSize);
    }
}
