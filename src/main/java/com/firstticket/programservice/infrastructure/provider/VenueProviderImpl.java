package com.firstticket.programservice.infrastructure.provider;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.VenueInfo;
import com.firstticket.programservice.infrastructure.client.VenueClient;
import com.firstticket.programservice.infrastructure.client.dto.SectionCapacityResponse;
import com.firstticket.programservice.infrastructure.client.dto.VenueInfoResponse;

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
        try {
            venueClient.validateVenueExists(venueId);
        } catch (feign.FeignException.NotFound e) {
            // 404 → 도메인 예외로 변환
            throw new ProgramException(ProgramErrorCode.VENUE_NOT_FOUND);
        } catch (feign.FeignException e) {
            // 그 외 Feign 오류 → 인프라 예외 propagate
            throw translateFeignException(e);
        }
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
        try {
            SectionCapacityResponse response =
                venueClient.getSectionCapacityResponse(sectionId);
            // SectionCapacityResponse compact constructor에서 null 방어됨
            return response.capacity();
        } catch (feign.FeignException.NotFound e) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_NOT_FOUND);
        } catch (feign.FeignException e) {
            throw translateFeignException(e);
        }
    }

    @Override
    public VenueInfo getVenueInfo(UUID venueId) {
        try {
            VenueInfoResponse response = venueClient.getVenueInfoResponse(venueId);
            return new VenueInfo(response.name(), response.address());
        } catch (feign.FeignException.NotFound e) {
            throw new ProgramException(ProgramErrorCode.VENUE_NOT_FOUND);
        } catch (feign.FeignException e) {
            throw translateFeignException(e);
        }

    }

    private ProgramException translateFeignException(feign.FeignException e) {
        int status = e.status();
        if (status >= 400 && status < 500) {
            return new ProgramException(ProgramErrorCode.EXTERNAL_SERVICE_CLIENT_ERROR);
        }
        return new ProgramException(ProgramErrorCode.EXTERNAL_SERVICE_FAILURE);
    }
}
