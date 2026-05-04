package com.firstticket.programservice.domain.service.dto;

/**
 * VenueProvider로 조회한 공연장 기본 정보.
 * 예매 서비스 내부 API 응답 구성 시 사용한다.
 */
public record VenueInfo(
    String name,
    String address
) {
}
