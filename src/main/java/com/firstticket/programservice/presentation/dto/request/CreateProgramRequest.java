package com.firstticket.programservice.presentation.dto.request;

import com.firstticket.programservice.application.dto.command.CreateProgramCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 프로그램 등록 요청 DTO.
 * toCommand()로 Application 계층의 CreateProgramCommand로 변환한다.
 */
public record CreateProgramRequest(

    @NotBlank(message = "프로그램 제목은 필수입니다")
    String title,

    @NotBlank(message = "카테고리는 필수입니다")
    String category,

    @NotBlank(message = "테마는 필수입니다")
    String theme,

    @NotNull(message = "프로그램 타입은 필수입니다")
    String type,

    @NotBlank(message = "지역은 필수입니다")
    String region,

    String posterUrl,
    String description

) {
    public CreateProgramCommand toCommand() {
        return new CreateProgramCommand(
            title, category, theme, type, region, posterUrl, description
        );
    }
}
