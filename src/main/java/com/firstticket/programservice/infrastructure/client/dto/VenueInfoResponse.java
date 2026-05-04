package com.firstticket.programservice.infrastructure.client.dto;

/**
 * Venue Service 공연장 기본 정보 응답 DTO.
 * VenueClient.getVenueInfoResponse() 응답을 역직렬화한다.
 * VenueProviderImpl에서 VenueInfo로 변환된다.
 */
public record VenueInfoResponse(
    String name,
    String address
) {
}
