package com.firstticket.programservice.domain.query;

/**
 * 프로그램 목록 조회 전용 Repository 인터페이스.
 * 도메인 계층에 위치하므로 Spring Data 의존성을 갖지 않는다.
 * Page 대신 순수 도메인 페이지네이션 VO(PagedResult)를 반환한다.
 */
public interface ProgramQueryRepository {

    /**
     * 조건 기반 프로그램 목록 조회 (P-04).
     * 카테고리·키워드·지역·날짜 필터, 정렬, 페이지네이션 지원.
     */
    PagedResult<ProgramSummaryData> findBySpec(ProgramSearchSpec spec);
}
