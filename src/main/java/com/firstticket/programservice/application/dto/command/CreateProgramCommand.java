package com.firstticket.programservice.application.dto.command;

import com.firstticket.programservice.domain.ProgramType;

/**
 * 프로그램 생성 커맨드.
 * Presentation 계층의 CreateProgramRequest.toCommand()로 생성된다.
 */
public record CreateProgramCommand(
    String title,
    String category,
    String theme,
    String type,
    String region,
    String posterUrl,
    String description
) {
}
