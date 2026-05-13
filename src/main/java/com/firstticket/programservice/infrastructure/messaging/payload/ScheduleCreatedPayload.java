package com.firstticket.programservice.infrastructure.messaging.payload;

import java.util.List;
import java.util.UUID;

/**
 * schedule.created 토픽 Kafka 페이로드.
 * 좌석 서비스가 수신하여 BookingSeat을 생성한다.
 * publishProgram() 시점에 모든 PriceGrade가 확정된 후 발행한다.
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
    }
}
