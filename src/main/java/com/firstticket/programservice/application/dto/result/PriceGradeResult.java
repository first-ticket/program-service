package com.firstticket.programservice.application.dto.result;

import java.util.UUID;

import com.firstticket.programservice.domain.PriceGrade;

/**
 * 가격 등급 조회 결과 DTO.
 */
public record PriceGradeResult(
    UUID sectionId,
    String gradeLabel,
    int price
) {
    public static PriceGradeResult from(PriceGrade priceGrade) {
        return new PriceGradeResult(
            priceGrade.getSectionId(),
            priceGrade.getGradeLabel(),
            priceGrade.getPrice()
        );
    }
}
