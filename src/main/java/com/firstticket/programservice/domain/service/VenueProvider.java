package com.firstticket.programservice.domain.service;

import java.util.UUID;

import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.service.dto.SectionValidationData;
import com.firstticket.programservice.domain.service.dto.VenueInfo;
import com.firstticket.programservice.domain.service.dto.VenueValidationData;

/**
 * Venue 서비스로부터 공연장·구역 정보를 조회하는 도메인 서비스 인터페이스.
 * 구현체는 infrastructure/provider/VenueProviderImpl에 위치한다.
 * Application 계층이 Feign Client 등 인프라 세부사항에 의존하지 않도록 격리한다.
 *
 * 기존 개별 메서드(validateVenueExists, getSectionCapacity 등)를
 * 검증 묶음 메서드 2개로 통합하여 Feign 호출 수를 줄인다.
 */
public interface VenueProvider {

    /**
     * venue 검증 묶음.
     * venue 존재 확인 + 해당 programType에 대응하는 구역 타입의 전체 수용량을 반환한다.
     * venue가 존재하지 않으면 VENUE_NOT_FOUND 예외를 던진다.
     *
     * 사용처: ProgramCommandService.createSchedule()
     */
    VenueValidationData validateVenue(UUID venueId, ProgramType programType);

    /**
     * section 검증 묶음.
     * sectionId가 venueId 소속인지 확인 + seatType + capacity를 반환한다.
     * 소속이 아니면 SECTION_NOT_FOUND_IN_VENUE 예외를 던진다.
     *
     * 사용처: ProgramCommandService.addPriceGrade() / addSectionCapacity()
     */
    SectionValidationData validateSection(UUID venueId, UUID sectionId);

    /**
     * 공연장 기본 정보 조회.
     * 예매 서비스 내부 API 응답 구성 시 name, address 조회에 사용한다.
     */
    VenueInfo getVenueInfo(UUID venueId);
}
