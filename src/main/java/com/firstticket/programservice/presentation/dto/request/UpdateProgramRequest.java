package com.firstticket.programservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.programservice.application.dto.command.UpdateProgramDraftCommand;
import com.firstticket.programservice.application.dto.command.UpdateProgramOnSaleCommand;

/**
 * 프로그램 수정 요청 DTO.
 * 상태별 수정 가능 필드 제한 (P-02):
 * - DRAFT    : 전체 필드 수정 가능
 * - ON_SALE  : posterUrl, description만 수정 가능
 * - 그 외    : 수정 불가 (도메인에서 422 반환)
 * null이면 기존 값 유지 (부분 업데이트).
 */
public record UpdateProgramRequest(
    String title,
    String category,
    String theme,
    String posterUrl,
    String description
) {
    public UpdateProgramDraftCommand toDraftCommand(UUID programId) {
        return new UpdateProgramDraftCommand(
            programId, title, category, theme, posterUrl, description
        );
    }

    public UpdateProgramOnSaleCommand toOnSaleCommand(UUID programId) {
        return new UpdateProgramOnSaleCommand(programId, posterUrl, description);
    }
}
