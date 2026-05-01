package com.firstticket.programservice.domain.query;

import java.util.List;

/**
 * 순수 도메인 페이지네이션 VO.
 * Spring Data의 Page 의존성을 도메인 계층에서 제거하기 위해 도입한다.
 * infrastructure 계층에서 QueryDSL 결과 → PagedResult 변환을 담당한다.
 */
public record PagedResult<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize
) {
    /**
     * compact constructor — 불변식 검증 및 방어적 복사.
     *
     * - content: null 불가, 불변 리스트로 복사 (외부 수정 방지)
     * - totalElements: 0 이상
     * - pageNumber: 0 이상
     * - pageSize: 1 이상 (페이지 메타데이터 보호)
     * - totalPages: 0 이상 (of() 팩토리에서 계산된 값 검증)
     */
    public PagedResult {
        if (content == null) {
            throw new IllegalArgumentException("content는 null일 수 없습니다.");
        }
        content = List.copyOf(content);  // 방어적 불변 복사

        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements는 0 이상이어야 합니다.");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber는 0 이상이어야 합니다.");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize는 1 이상이어야 합니다.");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages는 0 이상이어야 합니다.");
        }
    }

    /**
     * 정적 팩토리 메서드.
     * totalPages를 자동 계산하여 생성한다.
     */
    public static <T> PagedResult<T> of(List<T> content, long totalElements,
        int pageNumber, int pageSize) {
        // pageSize가 1 이상임이 보장되므로 나눗셈이 안전
        int totalPages = (int)Math.ceil((double)totalElements / pageSize);
        return new PagedResult<>(content, totalElements, totalPages, pageNumber, pageSize);
    }
}
