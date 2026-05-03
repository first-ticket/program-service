package com.firstticket.programservice.infrastructure.client.dto;

public record SectionCapacityResponse(Integer capacity) {
    public SectionCapacityResponse {
        if (capacity == null) {
            throw new IllegalStateException(
                "SectionCapacityResponse.capacity는 null일 수 없습니다.");
        }
    }
}
