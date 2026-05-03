package com.firstticket.programservice.application.dto.command;

import java.util.UUID;

/**
 * ON_SALE 상태 프로그램 수정 커맨드.
 * posterUrl, description만 수정 가능하다.
 */
public record UpdateProgramOnSaleCommand(
    UUID programId,
    String posterUrl,
    String description
) {
}
