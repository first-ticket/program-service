package com.firstticket.programservice.domain.query;

import java.time.LocalDate;

/**
 * 프로그램 목록 조회 조건 도메인 DTO.
 * 도메인 계층에 위치하므로 Spring Data Pageable 의존성을 갖지 않는다.
 * 페이지네이션은 pageNumber, pageSize 원시값으로 표현한다.
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
    int pageNumber,
    int pageSize
) {
    /**
     * compact constructor — 페이지네이션 값 검증.
     * pageNumber, pageSize가 음수이면 쿼리 결과가 깨지므로 생성 시점에 차단한다.
     */
    public ProgramSearchSpec {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber는 0 이상이어야 합니다.");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize는 1 이상이어야 합니다.");
        }
    }

    /** QueryDSL offset 계산용 헬퍼 */
    public long getOffset() {
        return (long)pageNumber * pageSize;
    }
}
