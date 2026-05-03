package com.firstticket.programservice.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.firstticket.programservice.infrastructure.client.dto.SectionCapacityResponse;

@FeignClient(name = "venue-service", url = "${feign.venue-service.url}")
public interface VenueClient {

    @GetMapping("/api/venues/{venueId}/exists")
    void validateVenueExists(@PathVariable("venueId") UUID venueId);

    @GetMapping("/api/venues/sections/{sectionId}/capacity")
    SectionCapacityResponse getSectionCapacityResponse(@PathVariable("sectionId") UUID sectionId);

    // TODO: Kafka 도입 시 ScheduleCreatedEvent 생성에 필요
    // @GetMapping("/api/venues/sections/infos")
    // Map<UUID, SectionInfoResponse> getSectionInfos(@RequestParam List<UUID> sectionIds);
}
