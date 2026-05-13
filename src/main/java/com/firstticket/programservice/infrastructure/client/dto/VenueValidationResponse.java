package com.firstticket.programservice.infrastructure.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * Venue Service venue 검증 묶음 응답 DTO.
 * sections 필드 추가 — ScheduleCreatedEvent seatTemplates 구성에 사용한다.
 */
public record VenueValidationResponse(
    int totalCapacity,
    List<SectionInfo> sections  // 추가
) {

    /**
     * Venue Service VenueValidationResult.SectionInfo와 동일한 구조.
     * JSON 역직렬화 대상이므로 필드명을 일치시킨다.
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
