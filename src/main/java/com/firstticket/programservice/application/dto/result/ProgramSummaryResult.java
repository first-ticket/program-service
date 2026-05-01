package com.firstticket.programservice.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.query.ProgramSummaryData;

/**
 * 프로그램 목록 조회 결과 DTO.
 * Application 계층에서 도메인 Projection(ProgramSummaryData)을 이 DTO로 변환한다.
 * Presentation 계층은 이 DTO를 받아 ProgramSummaryResponse로 변환한다.
 */
public record ProgramSummaryResult(
    UUID id,
    String title,
    String category,
    String theme,
    ProgramType type,
    ProgramStatus status,
    String posterUrl,
    LocalDateTime saleStartAt
) {
    /**
     * 도메인 Projection → 결과 DTO 변환.
     * Application 계층에서 호출한다.
     */
    public static ProgramSummaryResult from(ProgramSummaryData data) {
        return new ProgramSummaryResult(
            data.id(),
            data.title(),
            data.category(),
            data.theme(),
            data.type(),
            data.status(),
            data.posterUrl(),
            data.saleStartAt()
        );
    }
}
