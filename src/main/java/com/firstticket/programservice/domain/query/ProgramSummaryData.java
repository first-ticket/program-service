package com.firstticket.programservice.domain.query;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.querydsl.core.annotations.QueryProjection;

/**
 * 프로그램 목록 조회 결과 도메인 DTO.
 * QueryDSL Projection으로 필요한 필드만 조회한다.
 */
public record ProgramSummaryData(
    UUID id,
    String title,
    String category,
    String theme,
    ProgramType type,
    ProgramStatus status,
    String posterUrl,
    LocalDateTime saleStartAt  // 가장 빠른 스케줄의 판매 시작일시
) {
    @QueryProjection
    public ProgramSummaryData {
    }
}
