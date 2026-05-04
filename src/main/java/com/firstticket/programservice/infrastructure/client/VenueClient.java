package com.firstticket.programservice.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.firstticket.programservice.infrastructure.client.dto.SectionCapacityResponse;
import com.firstticket.programservice.infrastructure.client.dto.VenueInfoResponse;

@FeignClient(name = "venue-service", url = "${feign.venue-service.url}")
public interface VenueClient {

    @GetMapping("/api/venues/{venueId}/exists")
    void validateVenueExists(@PathVariable("venueId") UUID venueId);

    @GetMapping("/api/venues/sections/{sectionId}/capacity")
    SectionCapacityResponse getSectionCapacityResponse(@PathVariable("sectionId") UUID sectionId);

    /**
     * 공연장 기본 정보 조회.
     * 예매 서비스 내부 API 응답 구성 시 name, address 조회에 사용한다.
     */
    @GetMapping("/api/venues/{venueId}/info")
    VenueInfoResponse getVenueInfoResponse(
        @PathVariable("venueId") UUID venueId);

    // TODO: Kafka 도입 시 ScheduleCreatedEvent 생성에 필요
    // @GetMapping("/api/venues/sections/infos")
    // Map<UUID, SectionInfoResponse> getSectionInfos(@RequestParam List<UUID> sectionIds);
}
