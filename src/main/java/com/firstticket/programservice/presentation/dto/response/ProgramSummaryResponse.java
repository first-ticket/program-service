package com.firstticket.programservice.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.application.dto.result.ProgramSummaryResult;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;

/**
 * 프로그램 목록 조회 응답 DTO.
 */
public record ProgramSummaryResponse(
    UUID id,
    String title,
    String category,
    ProgramType type,
    ProgramStatus status,
    String posterUrl,
    LocalDateTime saleStartAt
) {
    public static ProgramSummaryResponse from(ProgramSummaryResult result) {
        return new ProgramSummaryResponse(
            result.id(),
            result.title(),
            result.category(),
            result.type(),
            result.status(),
            result.posterUrl(),
            result.saleStartAt()
        );
    }
}
