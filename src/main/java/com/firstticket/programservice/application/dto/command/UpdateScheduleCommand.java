package com.firstticket.programservice.application.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스케줄 수정 커맨드.
 * null이면 기존 값 유지 (부분 업데이트).
 * DRAFT: 전체 필드 수정 가능
 * ON_SALE·SOLD_OUT: eventStartAt, eventEndAt, totalCapacity만 수정 가능
 */
public record UpdateScheduleCommand(
    UUID programId,
    UUID scheduleId,
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    UUID venueId,
    int totalCapacity
) {
}
