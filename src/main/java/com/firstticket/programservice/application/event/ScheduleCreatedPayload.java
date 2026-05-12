package com.firstticket.programservice.application.event;

import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.domain.service.dto.VenueValidationData;

/**
 * 스케줄 생성 이벤트 페이로드 (토픽: schedule.created).
 * 좌석 서비스가 수신하여 BookingSeat을 생성한다.
 */
public record ScheduleCreatedPayload(
    UUID scheduleId,
    UUID programId,
    List<SeatTemplate> seatTemplates
) {

    /**
     * 구역별 좌석 템플릿.
     * SEATED  : rowCount × colCount 개 BookingSeat 생성
     * STANDING·FREE: capacity 기반 재고 관리
     */
    public record SeatTemplate(
        UUID sectionId,
        String sectionName,
        String seatType,
        Integer rowCount,
        Integer colCount,
        Integer capacity,
        int price
    ) {
        public static SeatTemplate from(VenueValidationData.SectionInfo section, int price) {
            return new SeatTemplate(
                section.sectionId(),
                section.sectionName(),
                section.seatType(),
                section.rowCount(),
                section.colCount(),
                section.capacity(),
                price
            );
        }
    }
}
