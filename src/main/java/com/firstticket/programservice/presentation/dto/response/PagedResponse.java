package com.firstticket.programservice.presentation.dto.response;

import java.util.List;
import java.util.function.Function;

import com.firstticket.programservice.domain.query.PagedResult;

/**
 * 페이지네이션 응답 DTO.
 * PagedResult를 Presentation 계층 응답으로 변환한다.
 * API 설계 문서: { content, totalElements, totalPages, size, number }
 */
public record PagedResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int size,       // pageSize
    int number      // pageNumber
) {
    public static <S, T> PagedResponse<T> from(PagedResult<S> pagedResult,
        Function<S, T> mapper) {
        return new PagedResponse<>(
            pagedResult.content().stream().map(mapper).toList(),
            pagedResult.totalElements(),
            pagedResult.totalPages(),
            pagedResult.pageSize(),
            pagedResult.pageNumber()
        );
    }
}
