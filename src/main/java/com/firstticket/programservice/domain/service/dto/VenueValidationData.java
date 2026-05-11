package com.firstticket.programservice.domain.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * venue 검증 묶음 도메인 DTO.
 * sections 필드 추가 — ScheduleCreatedEvent seatTemplates 구성에 사용한다.
 */
public record VenueValidationData(
    int totalCapacity,
    List<SectionInfo> sections  // 추가
) {

    /**
     * 구역 정보 도메인 DTO.
     * Application 계층에서 ScheduleCreatedPayload.SeatTemplate으로 변환된다.
     */
    public record SectionInfo(
        UUID sectionId,
        String sectionName,
        String seatType,
        Integer rowCount,
        Integer colCount,
        Integer capacity
    ) {
    }
}
