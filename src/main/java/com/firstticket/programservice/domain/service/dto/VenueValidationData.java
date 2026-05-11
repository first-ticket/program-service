package com.firstticket.programservice.domain.service.dto;

/**
 * venue 검증 묶음 도메인 DTO.
 * VenueProviderImpl이 VenueClient 응답을 이 DTO로 변환하여 Application 계층에 전달한다.
 *
 * totalCapacity: 해당 programType에 대응하는 구역 타입의 전체 수용량 합산
 */
public record VenueValidationData(
    int totalCapacity
) {
}
