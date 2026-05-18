package com.firstticket.programservice.presentation.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.application.dto.command.CreateScheduleCommand;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

/**
 * 스케줄 등록 요청 DTO.
 * Presentation 계층에서 @FutureOrPresent로 과거 시점 1차 방어.
 * 도메인에서 이중 방어한다.
 */
public record CreateScheduleRequest(

    @NotNull(message = "공연장 ID는 필수입니다")
    UUID venueId,

    @NotNull(message = "공연 시작 일시는 필수입니다")
    @FutureOrPresent(message = "공연 시작 일시는 현재 시각 이후여야 합니다")
    LocalDateTime eventStartAt,

    @NotNull(message = "공연 종료 일시는 필수입니다")
    @FutureOrPresent(message = "공연 종료 일시는 현재 시각 이후여야 합니다")
    LocalDateTime eventEndAt,

    @NotNull(message = "판매 시작 일시는 필수입니다")
    @FutureOrPresent(message = "판매 시작 일시는 현재 시각 이후여야 합니다")
    LocalDateTime saleStartAt,

    @NotNull(message = "판매 종료 일시는 필수입니다")
    @FutureOrPresent(message = "판매 종료 일시는 현재 시각 이후여야 합니다")
    LocalDateTime saleEndAt

) {
    public CreateScheduleCommand toCommand(UUID programId) {
        return new CreateScheduleCommand(
            programId, venueId,
            eventStartAt, eventEndAt,
            saleStartAt, saleEndAt
        );
    }
}
