package com.firstticket.programservice.application.dto.result;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.domain.Schedule;

/**
 * 스케줄 단건 조회 결과 DTO.
 */
public record ScheduleResult(
    UUID id,
    UUID venueId,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    int totalCapacity,
    List<PriceGradeResult> priceGrades
) {
    public static ScheduleResult from(Schedule schedule) {
        return new ScheduleResult(
            schedule.getId(),
            schedule.getVenueId(),
            schedule.getEventStartAt(),
            schedule.getEventEndAt(),
            schedule.getSaleStartAt(),
            schedule.getSaleEndAt(),
            schedule.getTotalCapacity(),
            schedule.getPriceGrades().stream()
                .map(PriceGradeResult::from)
                .toList()
        );
    }
}
