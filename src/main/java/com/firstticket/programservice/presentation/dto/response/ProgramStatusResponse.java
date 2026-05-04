package com.firstticket.programservice.presentation.dto.response;

import java.util.UUID;

/**
 * 프로그램 상태 전이 응답 DTO.
 * publish·cancel·close 응답에 사용한다.
 * API 설계 문서: { id, status }
 */
public record ProgramStatusResponse(
    UUID id,
    String status
) {
    public static ProgramStatusResponse from(UUID programId, String status) {
        return new ProgramStatusResponse(programId, status);
    }
}
