package com.firstticket.programservice.infrastructure.client.dto;

import java.util.UUID;

/**
 * 좌석 서비스 회차별 잔여 좌석 수 응답 DTO.
 * 좌석 서비스의 SeatRemainingResponse와 동일한 구조.
 * SeatClient의 raw 응답을 역직렬화한다.
 * SeatProviderImpl에서 ScheduleRemainingData로 변환된다.
 */
public record SeatRemainingResponse(
    UUID scheduleId,
    int remainingCount
) {
}
