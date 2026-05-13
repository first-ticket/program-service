package com.firstticket.programservice.domain.event;

import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.domain.service.dto.VenueValidationData;

/**
 * schedule.created 이벤트의 seatTemplates 구성 데이터.
 * 도메인 계층에서 사용하는 이벤트 데이터 DTO.
 * infrastructure/event/ProgramEventPublisherImpl에서
 * ScheduleCreatedPayload.SeatTemplate으로 변환된다.
 */
public record ScheduleCreatedEventData(
    UUID scheduleId,
    UUID programId,
    List<SeatTemplate> seatTemplates
) {

    /**
     * 구역별 좌석 템플릿.
     * VenueValidationData.SectionInfo + price로 구성된다.
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
