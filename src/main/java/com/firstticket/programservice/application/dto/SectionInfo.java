package com.firstticket.programservice.application.dto;

import java.util.UUID;

/**
 * VenueProvider로 조회한 구역 정보.
 * Application 계층에서 ScheduleCreatedEvent 생성 시 사용한다.
 * SEATED: rowCount, colCount 사용 / capacity null
 * STANDING: capacity 사용 / rowCount, colCount null
 */
public record SectionInfo(
    UUID sectionId,
    String sectionName,  // ScheduleCreatedEvent.SeatTemplate에 포함
    Integer rowCount,    // SEATED 전용
    Integer colCount,    // SEATED 전용
    Integer capacity     // STANDING 전용
) {
}
