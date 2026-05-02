package com.firstticket.programservice.infrastructure.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.firstticket.programservice.infrastructure.client.dto.SeatRemainingResponse;

/**
 * Seat Service Feign Client.
 * 좌석 서비스의 내부 API를 호출한다.
 * 도메인 계층에 직접 노출하지 않는다.
 * SeatProviderImpl을 통해서만 접근한다.
 */
@FeignClient(
    name = "seat-service",
    url = "${feign.seat-service.url}"
)
public interface SeatClient {

    /**
     * 프로그램의 회차별 잔여 좌석 수 조회 (P-05).
     * 좌석 서비스의 /internal/v1/seats/remaining/{programId} 호출.
     */
    @GetMapping("/internal/v1/seats/remaining/{programId}")
    List<SeatRemainingResponse> getRemainingCounts(@PathVariable UUID programId);
}
