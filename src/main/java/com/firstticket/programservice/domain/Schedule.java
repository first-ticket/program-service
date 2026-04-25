package com.firstticket.programservice.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.firstticket.common.persistence.BaseUserEntity;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연 회차(Schedule) 도메인 엔티티
 * 하나의 프로그램(Program)에 속한 개별 공연 일정(날짜, 시간, 장소)을 관리합니다.
 * 티켓 판매 기간(Sale Period)과 실제 공연 시간(Event Period)을 제어하는 핵심 도메인입니다.
 */
@Entity
@Table(name = "p_schedule")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    /** 부모 엔티티: 소속된 공연 정보 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** 공연장 도메인의 장소 식별자 */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID venueId;

    /** 실제 공연 시작 일시 */
    @Column(nullable = false)
    private LocalDateTime eventStartAt;

    /** 실제 공연 종료 일시 */
    @Column(nullable = false)
    private LocalDateTime eventEndAt;

    /** 예매 시작 일시 */
    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    /** 예매 종료 일시 (일반적으로 공연 시작 전 마감) */
    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    @Column(nullable = false)
    private int totalCapacity;

    @OneToMany(mappedBy = "schedule",
        cascade = CascadeType.ALL,
        orphanRemoval = true)
    private List<PriceGrade> priceGrades;

    /**
     * 회차 생성 시 시간 정당성 검증을 수행합니다.
     * (날짜값 크기 비교)
     * 1. 행사 종료 > 행사 시작
     * 2. 판매 종료 > 판매 시작
     * 3. 행사 시작 > 판매 종료 (판매는 행사 시작 전에 마감되어야 함)
     */
    static Schedule create(Program program, UUID venueId,
        LocalDateTime eventStartAt, LocalDateTime eventEndAt,
        LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        int totalCapacity) {
        // 스케줄 필수 정보 검증
        validateScheduleInfo(venueId, totalCapacity);
        // 시간 정당성 검증
        validatePeriod(eventStartAt, eventEndAt, saleStartAt, saleEndAt);
        return new Schedule(
            null,
            program, venueId,
            eventStartAt, eventEndAt,
            saleStartAt, saleEndAt,
            totalCapacity,
            new ArrayList<>()
        );
    }


    /**
     * 해당 회차에 가격 등급(PriceGrade)을 추가합니다.
     * @param gradeLabel 등급명 (예: VIP, R, S) - 중복 불가
     */
    public void addPriceGrade(UUID sectionId, String gradeLabel, int price) {
        boolean isDuplicate = priceGrades.stream()
            .anyMatch(pg -> pg.getGradeLabel().equals(gradeLabel));
        if (isDuplicate) {
            throw new ProgramException(ProgramErrorCode.PRICE_GRADE_DUPLICATE);
        }
        priceGrades.add(PriceGrade.create(this, sectionId, gradeLabel, price));
    }

    public void removePriceGrade(String gradeLabel) {
        boolean removed = priceGrades.removeIf(
            pg -> pg.getGradeLabel().equals(gradeLabel)
        );
        if (!removed) {
            throw new ProgramException(ProgramErrorCode.PRICE_GRADE_NOT_FOUND);
        }
    }

    /**
     * 현재 시각 기준으로 티켓 판매 가능 여부를 확인합니다.
     */
    public boolean isWithinSalePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(saleStartAt) && now.isBefore(saleEndAt);
    }

    public List<PriceGrade> getPriceGrades() {
        return Collections.unmodifiableList(priceGrades);
    }

    /**
     * 기간 필드들에 대한 비즈니스 제약 조건을 검증합니다.
     */
    private static void validatePeriod(LocalDateTime eventStart, LocalDateTime eventEnd,
        LocalDateTime saleStart, LocalDateTime saleEnd) {
        // null 선검증 추가
        if (eventStart == null || eventEnd == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_EVENT_PERIOD);
        }
        if (saleStart == null || saleEnd == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SALE_PERIOD);
        }

        // 과거 시점 공연 등록 차단
        // Presentation 계층 @FutureOrPresent 어노테이션과 이중 방어
        if (eventStart.isBefore(LocalDateTime.now())) {
            throw new ProgramException(ProgramErrorCode.PAST_EVENT_START);
        }

        if (!eventStart.isBefore(eventEnd)) {
            throw new ProgramException(ProgramErrorCode.INVALID_EVENT_PERIOD);
        }
        if (!saleStart.isBefore(saleEnd)) {
            throw new ProgramException(ProgramErrorCode.INVALID_SALE_PERIOD);
        }
        if (!saleEnd.isBefore(eventStart)) {
            throw new ProgramException(ProgramErrorCode.SALE_END_AFTER_EVENT_START);
        }
    }

    /**
     * 스케줄 필수 입력 정보(venueId, totalCapacity)를 검증합니다.
     */
    private static void validateScheduleInfo(UUID venueId, int totalCapacity) {
        // venueId null 검증
        if (venueId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_VENUE_ID);
        }

        // 수용 인원 검증 — 0 이하 차단
        if (totalCapacity <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);
        }
    }
}
