// infrastructure/client/dto/VenueValidationResponse.java

package com.firstticket.programservice.infrastructure.client.dto;

/**
 * Venue Service venue 검증 묶음 응답 DTO.
 * VenueClient.getVenueValidation() 응답을 역직렬화한다.
 * VenueProviderImpl에서 VenueValidationData로 변환된다.
 */
public record VenueValidationResponse(
    int totalCapacity
) {
}
