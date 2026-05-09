package com.firstticket.programservice.infrastructure.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.firstticket.programservice.infrastructure.client.dto.SectionValidationResponse;
import com.firstticket.programservice.infrastructure.client.dto.VenueInfoResponse;
import com.firstticket.programservice.infrastructure.client.dto.VenueValidationResponse;

/**
 * Venue Service Feign Client.
 * 도메인 계층에 직접 노출하지 않는다.
 * VenueProviderImpl을 통해서만 접근한다.
 *
 * 기존 개별 엔드포인트 호출을 검증 묶음 2개로 통합한다.
 */
@FeignClient(name = "venue-service", url = "${feign.venue-service.url}")
public interface VenueClient {

    /**
     * venue 검증 묶음 호출.
     * venue 존재 확인 + 해당 seatType 구역 전체 수용량을 한 번에 조회한다.
     * venue가 존재하지 않으면 Venue Service가 404를 반환한다.
     *
     * @param venueId  공연장 ID
     * @param seatType 프로그램 타입에 대응하는 구역 타입 문자열 (SEATED·STANDING·FREE)
     */
    @GetMapping("/internal/v1/venues/{venueId}/validation")
    VenueValidationResponse getVenueValidation(
        @PathVariable("venueId") UUID venueId,
        @RequestParam("seatType") String seatType);

    /**
     * section 검증 묶음 호출.
     * sectionId 소속 확인 + seatType + capacity를 한 번에 조회한다.
     * sectionId가 venueId 소속이 아니면 Venue Service가 404를 반환한다.
     *
     * @param venueId   소속 공연장 ID
     * @param sectionId 검증할 구역 ID
     */
    @GetMapping("/internal/v1/venues/{venueId}/sections/{sectionId}/validation")
    SectionValidationResponse getSectionValidation(
        @PathVariable("venueId") UUID venueId,
        @PathVariable("sectionId") UUID sectionId);

    /**
     * 공연장 기본 정보 조회.
     * 예매 서비스 내부 API 응답 구성 시 name, address 조회에 사용한다.
     */
    @GetMapping("/internal/v1/venues/{venueId}/info")
    VenueInfoResponse getVenueInfoResponse(@PathVariable("venueId") UUID venueId);
}
