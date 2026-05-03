package com.firstticket.programservice.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.application.dto.result.ScheduleResult;

/**
 * 스케줄 조회 응답 DTO.
 */
public record ScheduleResponse(
    UUID id,
    UUID venueId,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    int totalCapacity,
    int remainingCount,
    List<PriceGradeResponse> priceGrades
) {
    public static ScheduleResponse from(ScheduleResult result) {
        return new ScheduleResponse(
            result.id(),
            result.venueId(),
            result.eventStartAt(),
            result.eventEndAt(),
            result.saleStartAt(),
            result.saleEndAt(),
            result.totalCapacity(),
            result.remainingCount(),
            result.priceGrades().stream()
                .map(PriceGradeResponse::from)
                .toList()
        );
    }
}
