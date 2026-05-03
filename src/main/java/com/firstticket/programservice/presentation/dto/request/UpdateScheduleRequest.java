package com.firstticket.programservice.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.application.dto.command.UpdateScheduleCommand;

/**
 * 스케줄 수정 요청 DTO.
 * null이면 기존 값 유지 (부분 업데이트).
 * 상태별 수정 가능 필드 제한은 도메인에서 처리한다.
 */
public record UpdateScheduleRequest(
    LocalDateTime eventStartAt,
    LocalDateTime eventEndAt,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    UUID venueId,
    int totalCapacity
) {
    public UpdateScheduleCommand toCommand(UUID programId, UUID scheduleId) {
        return new UpdateScheduleCommand(
            programId, scheduleId,
            eventStartAt, eventEndAt,
            saleStartAt, saleEndAt,
            venueId, totalCapacity
        );
    }
}
