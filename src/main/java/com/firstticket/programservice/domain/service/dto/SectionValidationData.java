package com.firstticket.programservice.domain.service.dto;

/**
 * section 검증 묶음 도메인 DTO.
 * VenueProviderImpl이 VenueClient 응답을 이 DTO로 변환하여 Application 계층에 전달한다.
 *
 * seatType : section의 타입 문자열 (SEATED·STANDING·FREE)
 * capacity : SEATED → rowCount × colCount / STANDING·FREE → Section.capacity
 */
public record SectionValidationData(
    String seatType,
    int capacity
) {
}
