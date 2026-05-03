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
    int remainingCount,     // 좌석 서비스에서 조회한 잔여 수
    List<PriceGradeResult> priceGrades
) {
    // remainingCount 포함 버전 — getProgram() 단건 조회 시 사용
    public static ScheduleResult from(Schedule schedule, int remainingCount) {
        return new ScheduleResult(
            schedule.getId(),
            schedule.getVenueId(),
            schedule.getEventStartAt(),
            schedule.getEventEndAt(),
            schedule.getSaleStartAt(),
            schedule.getSaleEndAt(),
            schedule.getTotalCapacity(),
            remainingCount,
            schedule.getPriceGrades().stream()
                .map(PriceGradeResult::from)
                .toList()
        );
    }

    // remainingCount 미포함 버전 — Command 결과 반환 시 사용 (잔여 좌석 불필요)
    public static ScheduleResult from(Schedule schedule) {
        return from(schedule, 0);
    }
}
