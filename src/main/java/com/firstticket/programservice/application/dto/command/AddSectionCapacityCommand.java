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
    public AddSectionCapacityCommand {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity는 1 이상이어야 합니다.");
        }
    }
}
