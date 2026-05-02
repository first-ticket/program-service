package com.firstticket.programservice.domain.service.dto;

import java.util.UUID;

/**
 * 좌석 서비스에서 조회한 회차별 잔여 좌석 수 데이터.
 * SeatProvider를 통해 조회된 결과를 담는다.
 * infrastructure 계층의 Feign Client 응답을 이 DTO로 변환하여 도메인에 전달한다.
 */
public record ScheduleRemainingData(
    UUID scheduleId,
    int remainingCount
) {
}
