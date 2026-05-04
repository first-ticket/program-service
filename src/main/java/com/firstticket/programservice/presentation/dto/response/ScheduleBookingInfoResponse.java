package com.firstticket.programservice.presentation.dto.response;

import java.time.LocalDateTime;

import com.firstticket.programservice.application.dto.result.ScheduleBookingInfoResult;

/**
 * 예매 서비스 내부 API 응답 DTO.
 * 예매 서비스의 FeignClient가 수신하는 응답 구조.
 */
public record ScheduleBookingInfoResponse(
    String programTitle,
    String venueName,
    String venueAddress,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt
) {
    public static ScheduleBookingInfoResponse from(ScheduleBookingInfoResult result) {
        return new ScheduleBookingInfoResponse(
            result.programTitle(),
            result.venueName(),
            result.venueAddress(),
            result.eventStartAt(),
            result.eventEndAt(),
            result.saleStartAt(),
            result.saleEndAt()
        );
    }
}
