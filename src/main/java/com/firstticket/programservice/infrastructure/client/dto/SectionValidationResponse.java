// infrastructure/client/dto/SectionValidationResponse.java

package com.firstticket.programservice.infrastructure.client.dto;

/**
 * Venue Service section 검증 묶음 응답 DTO.
 * VenueClient.getSectionValidation() 응답을 역직렬화한다.
 * VenueProviderImpl에서 SectionValidationData로 변환된다.
 */
public record SectionValidationResponse(
    String seatType,
    int capacity
) {
}
