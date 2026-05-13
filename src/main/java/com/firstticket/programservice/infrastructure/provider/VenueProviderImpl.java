package com.firstticket.programservice.infrastructure.provider;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.SectionValidationData;
import com.firstticket.programservice.domain.service.dto.VenueInfo;
import com.firstticket.programservice.domain.service.dto.VenueValidationData;
import com.firstticket.programservice.infrastructure.client.VenueClient;
import com.firstticket.programservice.infrastructure.client.dto.VenueInfoResponse;

import lombok.RequiredArgsConstructor;

/**
 * VenueProvider 구현체.
 * VenueClient(Feign)를 감싸서 raw 응답을 도메인 DTO로 변환한다.
 * Application 계층이 Feign Client의 세부사항에 의존하지 않도록 격리한다.
 */
@Component
@RequiredArgsConstructor
public class VenueProviderImpl implements VenueProvider {

    private final VenueClient venueClient;

    /**
     * venue 검증 묶음.
     * ProgramType → SeatType 문자열로 변환 후 Venue Service에 단일 요청한다.
     * venue가 없으면 VENUE_NOT_FOUND, 통신 실패 시 인프라 예외로 변환한다.
     */
    @Override
    public VenueValidationData validateVenue(UUID venueId, ProgramType programType) {
        try {
            // ProgramType과 SeatType은 동일한 이름을 사용하므로 .name()으로 변환
            String seatType = programType.name();
            var response = venueClient.getVenueValidation(venueId, seatType);

            // SectionInfo 변환
            List<VenueValidationData.SectionInfo> sections = response.sections().stream()
                .map(s -> new VenueValidationData.SectionInfo(
                    s.sectionId(), s.sectionName(), s.seatType(),
                    s.rowCount(), s.colCount(), s.capacity()
                ))
                .toList();

            return new VenueValidationData(response.totalCapacity(), sections);
        } catch (feign.FeignException.NotFound e) {
            throw new ProgramException(ProgramErrorCode.VENUE_NOT_FOUND);
        } catch (feign.FeignException e) {
            throw translateFeignException(e);
        }
    }

    /**
     * section 검증 묶음.
     * sectionId 소속 확인 + seatType + capacity를 단일 요청으로 처리한다.
     * sectionId가 venueId 소속이 아니면 SECTION_NOT_FOUND_IN_VENUE로 변환한다.
     */
    @Override
    public SectionValidationData validateSection(UUID venueId, UUID sectionId) {
        try {
            var response = venueClient.getSectionValidation(venueId, sectionId);
            return new SectionValidationData(response.seatType(), response.capacity());
        } catch (feign.FeignException.NotFound e) {
            throw new ProgramException(ProgramErrorCode.SECTION_NOT_FOUND_IN_VENUE);
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

    /**
     * FeignException → ProgramException 변환.
     * 4xx: 클라이언트 측 오류 (잘못된 요청 등)
     * 5xx: 서버 측 오류 (Venue Service 장애 등)
     */
    private ProgramException translateFeignException(feign.FeignException e) {
        int status = e.status();
        if (status >= 400 && status < 500) {
            return new ProgramException(ProgramErrorCode.EXTERNAL_SERVICE_CLIENT_ERROR);
        }
        return new ProgramException(ProgramErrorCode.EXTERNAL_SERVICE_FAILURE);
    }
}
