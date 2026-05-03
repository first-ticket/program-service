package com.firstticket.programservice.infrastructure.client.dto;

public record SectionInfoResponse(
    String sectionName,  // ← 추가
    Integer rowCount,
    Integer colCount,
    Integer capacity
) {
}
