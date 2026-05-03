package com.firstticket.programservice.application.dto.command;

import java.util.UUID;

/**
 * DRAFT 상태 프로그램 수정 커맨드.
 * null이면 기존 값 유지 (부분 업데이트).
 */
public record UpdateProgramDraftCommand(
    UUID programId,
    String title,
    String category,
    String theme,
    String posterUrl,
    String description
) {
}
