package com.firstticket.programservice.infrastructure.provider;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.firstticket.programservice.domain.service.SeatProvider;
import com.firstticket.programservice.domain.service.dto.ScheduleRemainingData;
import com.firstticket.programservice.infrastructure.client.SeatClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SeatProvider 구현체.
 * SeatClient(Feign)를 감싸서 raw 응답을 도메인 DTO(ScheduleRemainingData)로 변환한다.
 * Application 계층이 Feign Client의 세부사항에 의존하지 않도록 격리한다.
 *
 * 장애 격리:
 * 좌석 서비스 장애 시 빈 리스트로 fallback하여
 * 프로그램 조회 자체가 실패하지 않도록 보호한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatProviderImpl implements SeatProvider {

    private final SeatClient seatClient;

    /**
     * 프로그램의 회차별 잔여 좌석 수 조회.
     * 좌석 서비스 장애 시 빈 리스트로 fallback한다.
     * 잔여 좌석 조회 실패가 프로그램 조회 전체를 실패시키면 안 되기 때문이다.
     */
    @Override
    public List<ScheduleRemainingData> getRemainingCounts(UUID programId) {
        try {
            return seatClient.getRemainingCounts(programId).stream()
                .map(response -> new ScheduleRemainingData(
                    response.scheduleId(),
                    response.remainingCount()
                ))
                .toList();
        } catch (feign.FeignException e) {
            // 외부 서비스 통신 실패 — 빈 리스트로 fallback
            log.warn("[SeatProvider] 잔여 좌석 조회 실패 — programId: {}, error: {}",
                programId, e.getMessage());
            return List.of();
        }
        // 그 외 예외(매핑 오류 등)는 propagate — 내부 버그 은닉 방지
    }
}
