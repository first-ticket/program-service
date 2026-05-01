package com.firstticket.programservice.application.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스케줄 생성 커맨드.
 * Presentation 계층의 CreateScheduleRequest.toCommand()로 생성된다.
 */
public record CreateScheduleCommand(
    UUID programId,
    UUID venueId,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    int totalCapacity
) {
}
