package com.firstticket.programservice.presentation.dto.response;

import java.util.UUID;

import com.firstticket.programservice.application.dto.result.PriceGradeResult;

/**
 * 가격 등급 조회 응답 DTO.
 */
public record PriceGradeResponse(
    UUID sectionId,
    String gradeLabel,
    int price
) {
    public static PriceGradeResponse from(PriceGradeResult result) {
        return new PriceGradeResponse(
            result.sectionId(),
            result.gradeLabel(),
            result.price()
        );
    }
}
