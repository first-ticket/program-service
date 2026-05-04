package com.firstticket.programservice.presentation.dto.request;

import java.util.UUID;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.CommonErrorCode;
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

    /**
     * ON_SALE 상태 수정 커맨드 생성.
     * title, category, theme은 ON_SALE 상태에서 수정 불가.
     * 값이 전달된 경우 즉시 422를 반환한다.
     */
    public UpdateProgramOnSaleCommand toOnSaleCommand(UUID programId) {
        // ON_SALE에서 수정 불가 필드 감지 — 클라이언트 오류로 즉시 차단
        if (title != null || category != null || theme != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return new UpdateProgramOnSaleCommand(programId, posterUrl, description);
    }
}
