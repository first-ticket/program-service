package com.firstticket.programservice.application.dto.command;

import java.util.UUID;

/**
 * 구역별 인원 추가 커맨드.
 * STANDING·FREE 타입 스케줄에서만 사용한다.
 */
public record AddSectionCapacityCommand(
    UUID programId,
    UUID scheduleId,
    UUID sectionId,
    int capacity
) {
}
