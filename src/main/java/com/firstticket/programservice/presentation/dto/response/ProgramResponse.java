package com.firstticket.programservice.presentation.dto.response;

import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;

/**
 * 프로그램 단건 조회 응답 DTO.
 * from(ProgramResult)으로 생성한다.
 */
public record ProgramResponse(
    UUID id,
    String title,
    String category,
    String theme,
    ProgramType type,
    ProgramStatus status,
    String region,
    String posterUrl,
    String description,
    List<ScheduleResponse> schedules
) {
    public static ProgramResponse from(ProgramResult result) {
        return new ProgramResponse(
            result.id(),
            result.title(),
            result.category(),
            result.theme(),
            result.type(),
            result.status(),
            result.region(),
            result.posterUrl(),
            result.description(),
            result.schedules().stream()
                .map(ScheduleResponse::from)
                .toList()
        );
    }
}
