package com.firstticket.programservice.domain.query;

import org.springframework.data.domain.Page;

/**
 * 프로그램 목록 조회 전용 Repository 인터페이스.
 * Command Repository와 분리하여 조회 최적화 쿼리를 별도로 관리한다.
 * 구현체는 QueryDSL을 사용한다.
 */
public interface ProgramQueryRepository {

    /**
     * 조건 기반 프로그램 목록 조회 (P-04).
     * 카테고리·키워드·지역·날짜 필터, 정렬, 페이지네이션 지원.
     */
    Page<ProgramSummaryData> findBySpec(ProgramSearchSpec spec);
}
