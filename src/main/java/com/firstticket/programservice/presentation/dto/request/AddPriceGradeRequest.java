package com.firstticket.programservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.programservice.application.dto.command.AddPriceGradeCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 가격 등급 추가 요청 DTO.
 * FREE 타입은 sectionId null 허용.
 */
public record AddPriceGradeRequest(

    @NotBlank(message = "등급명은 필수입니다")
    String gradeLabel,

    UUID sectionId,  // FREE 타입은 null 허용

    @NotNull(message = "가격은 필수입니다")
    @PositiveOrZero(message = "가격은 0원 이상이어야 합니다")
    Integer price

) {
    public AddPriceGradeCommand toCommand(UUID programId, UUID scheduleId) {
        return new AddPriceGradeCommand(
            programId, scheduleId, sectionId, gradeLabel, price
        );
    }
}
