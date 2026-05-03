package com.firstticket.programservice.application.dto.command;

import java.util.UUID;

/**
 * 가격 등급 추가 커맨드.
 * SEATED·STANDING: sectionId 필수
 * FREE: sectionId null
 */
public record AddPriceGradeCommand(
    UUID programId,
    UUID scheduleId,
    UUID sectionId,
    String gradeLabel,
    int price
) {
}
