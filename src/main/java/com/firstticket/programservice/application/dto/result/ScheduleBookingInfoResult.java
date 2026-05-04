package com.firstticket.programservice.application.dto.result;

import java.time.LocalDateTime;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.service.dto.VenueInfo;

/**
 * 예매 서비스 내부 API 응답 DTO.
 * 예매 서비스가 FeignClient로 조회하는 스케줄 예매 정보.
 *
 * venueName, venueAddress: VenueProvider를 통해 조회한 공연장 정보
 */
public record ScheduleBookingInfoResult(
    String programTitle,
    String venueName,
    String venueAddress,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt
) {
    public static ScheduleBookingInfoResult of(Program program,
        Schedule schedule,
        VenueInfo venueInfo) {
        return new ScheduleBookingInfoResult(
            program.getTitle(),
            venueInfo.name(),
            venueInfo.address(),
            schedule.getEventStartAt(),
            schedule.getEventEndAt(),
            schedule.getSaleStartAt(),
            schedule.getSaleEndAt()
        );
    }
}
