package com.firstticket.programservice.presentation.dto.response;

import java.util.UUID;

import com.firstticket.programservice.domain.ProgramStatus;

/**
 * 프로그램 상태 전이 응답 DTO.
 * publish·cancel·close 응답에 사용한다.
 * API 설계 문서: { id, status }
 */
public record ProgramStatusResponse(
    UUID id,
    ProgramStatus status
) {
    public static ProgramStatusResponse from(UUID programId, ProgramStatus status) {
        return new ProgramStatusResponse(programId, status);
    }
}
