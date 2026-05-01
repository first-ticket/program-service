package com.firstticket.programservice.infrastructure.provider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.firstticket.programservice.application.dto.SectionInfo;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.infrastructure.client.VenueClient;

import lombok.RequiredArgsConstructor;

/**
 * VenueProvider 구현체.
 * VenueClient(Feign)를 감싸서 raw 응답을 Application DTO로 변환한다.
 * Application 계층이 Feign Client의 세부사항에 의존하지 않도록 격리한다.
 */
@Component
@RequiredArgsConstructor
public class VenueProviderImpl implements VenueProvider {

    private final VenueClient venueClient;

    @Override
    public void validateVenueExists(UUID venueId) {
        venueClient.validateVenueExists(venueId);
    }

    // TODO: Kafka 도입 시 ScheduleCreatedEvent 생성에 필요
    // @Override
    // public Map<UUID, SectionInfo> getSectionInfos(List<UUID> sectionIds) {
    //     // VenueClient raw 응답 → SectionInfo 변환
    //     return venueClient.getSectionInfos(sectionIds).entrySet().stream()
    //         .collect(Collectors.toMap(
    //             Map.Entry::getKey,
    //             e -> new SectionInfo(
    //                 e.getKey(),
    //                 e.getValue().sectionName(),
    //                 e.getValue().rowCount(),
    //                 e.getValue().colCount(),
    //                 e.getValue().capacity()
    //             )
    //         ));
    // }

    @Override
    public int getSectionCapacity(UUID sectionId) {
        return venueClient.getSectionCapacityResponse(sectionId).capacity();
    }
}
