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
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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

    /**
     * 낙관적 락 버전 필드.
     * addSectionCapacity() 동시 호출 시 합계 불변식(sum ≤ totalCapacity) 위반 방지.
     * 같은 Schedule을 동시에 수정하면 나중 커밋 측에서 OptimisticLockException 발생.
     * &#064;Lock(LockModeType.PESSIMISTIC_WRITE)
     *     &#064;Query("SELECT  s FROM Schedule s WHERE s.id = :id")
     *     Optional<Schedule> findByIdWithLock(@Param("id") UUID id);
     *  같은 비관적 락도 고려
     */
    @Version
    private Long version;

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

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceGrade> priceGrades;

    /**
     * STANDING·FREE 프로그램 전용: 구역별 허용 인원 목록.
     * SEATED 프로그램은 VenueSeat 기반이므로 이 컬렉션이 비어 있다.
     *
     * &#064;ElementCollection:  VO 컬렉션이므로 별도 Repository 없음
     * CollectionTable: schedule_section_capacity 테이블에 저장
     */
    @ElementCollection
    @CollectionTable(name = "schedule_section_capacity", joinColumns = @JoinColumn(name = "schedule_id"), uniqueConstraints = @UniqueConstraint(name = "uk_schedule_section_capacity", columnNames = {
        "schedule_id", "section_id"}))
    private List<ScheduleSectionCapacity> sectionCapacities;

    /**
     * 회차 생성 시 시간 정당성 검증을 수행합니다.
     * (날짜값 크기 비교)
     * 1. 행사 종료 > 행사 시작
     * 2. 판매 종료 > 판매 시작
     * 3. 행사 시작 > 판매 종료 (판매는 행사 시작 전에 마감되어야 함)
     */
    static Schedule create(Program program, UUID venueId, LocalDateTime eventStartAt, LocalDateTime eventEndAt,
        LocalDateTime saleStartAt, LocalDateTime saleEndAt, int totalCapacity) {
        // 스케줄 필수 정보 검증
        validateScheduleInfo(venueId, totalCapacity);

        // 시간 정당성 검증
        validatePeriod(eventStartAt, eventEndAt, saleStartAt, saleEndAt);

        // 과거 시점 공연 등록 차단
        // Presentation 계층 @FutureOrPresent 어노테이션과 이중 방어
        if (eventStartAt.isBefore(LocalDateTime.now())) {
            throw new ProgramException(ProgramErrorCode.PAST_EVENT_START);
        }

        return new Schedule(null,
            null,       // version ← JPA가 INSERT 시 0으로 자동 설정,
            program, venueId, eventStartAt, eventEndAt, saleStartAt, saleEndAt, totalCapacity,
            new ArrayList<>(),   // priceGrades
            new ArrayList<>()    // sectionCapacities
        );
    }

    /**
     * 스케줄 정보를 수정합니다.
     * - DRAFT: 전체 필드 수정 가능
     * - ON_SALE/SOLD_OUT: eventStartAt, eventEndAt, totalCapacity만 수정 가능
     *   saleStartAt, saleEndAt, venueId는 예매 진행 중이므로 변경 불가
     * - CANCELLED/CLOSED: 수정 불가
     */
    public void update(LocalDateTime eventStartAt, LocalDateTime eventEndAt, LocalDateTime saleStartAt,
        LocalDateTime saleEndAt, UUID venueId, int totalCapacity) {

        ProgramStatus programStatus = this.program.getStatus();

        if (programStatus == ProgramStatus.CANCELLED || programStatus == ProgramStatus.CLOSED) {
            throw new ProgramException(ProgramErrorCode.SCHEDULE_NOT_EDITABLE);
        }

        if (programStatus != ProgramStatus.DRAFT) {
            if (saleStartAt != null || saleEndAt != null || venueId != null) {
                throw new ProgramException(ProgramErrorCode.SCHEDULE_SALE_INFO_NOT_EDITABLE);
            }
        }

        // 부분 업데이트: null이면 기존 값 유지
        LocalDateTime newEventStart = (eventStartAt != null) ? eventStartAt : this.eventStartAt;
        LocalDateTime newEventEnd = (eventEndAt != null) ? eventEndAt : this.eventEndAt;
        LocalDateTime newSaleStart = (saleStartAt != null) ? saleStartAt : this.saleStartAt;
        LocalDateTime newSaleEnd = (saleEndAt != null) ? saleEndAt : this.saleEndAt;
        int newTotalCapacity = this.totalCapacity;
        if (totalCapacity < 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);

        }
        if (totalCapacity > 0) {
            newTotalCapacity = totalCapacity;

        }

        // 검증 통과 후 반영
        // 검증 전 필드 변경 시 실패일 경우의 객체 상태 오염 방지
        validatePeriod(newEventStart, newEventEnd, newSaleStart, newSaleEnd);

        // 이미 등록된 스케줄의 eventStartAt이 현재 시각보다 이전일 수 있으므로
        // 변경하지 않는다면 과거 시점 판정을 하지 않음
        if (eventStartAt != null && newEventStart.isBefore(LocalDateTime.now())) {
            throw new ProgramException(ProgramErrorCode.PAST_EVENT_START);
        }

        // totalCapacity 축소 시 sectionCapacities 합계 불변식 검증
        // sum(sectionCapacities) > newTotalCapacity 상태가 되면
        // 예매 가능 수 계산이 깨지므로 차단한다.
        // int → long: 오버플로우 방지
        if (!sectionCapacities.isEmpty()) {
            long sectionTotal = sectionCapacities.stream().mapToLong(ScheduleSectionCapacity::getCapacity).sum();
            if (sectionTotal > newTotalCapacity) {
                throw new ProgramException(ProgramErrorCode.TOTAL_CAPACITY_LESS_THAN_SECTION_SUM);
            }
        }

        this.eventStartAt = newEventStart;
        this.eventEndAt = newEventEnd;
        this.saleStartAt = newSaleStart;
        this.saleEndAt = newSaleEnd;
        if (venueId != null)
            this.venueId = venueId;
        this.totalCapacity = newTotalCapacity;
    }

    // ---- priceGrade 관련 ----------------------------------------

    /**
     * 해당 회차에 가격 등급(PriceGrade)을 추가합니다.
     * @param gradeLabel 등급명 (예: VIP, R, S) - 중복 불가
     */
    public void addPriceGrade(UUID sectionId, String gradeLabel, int price) {
        // gradeLabel null/blank 선검증
        if (gradeLabel == null || gradeLabel.isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_GRADE_LABEL);
        }

        boolean isDuplicate = priceGrades.stream().anyMatch(pg -> pg.getGradeLabel().equals(gradeLabel));
        if (isDuplicate) {
            throw new ProgramException(ProgramErrorCode.PRICE_GRADE_DUPLICATE);
        }
        priceGrades.add(PriceGrade.of(this, sectionId, gradeLabel, price));
    }

    public void removePriceGrade(String gradeLabel) {
        // 입력값 검증 — null/blank는 INVALID_GRADE_LABEL로 분리
        if (gradeLabel == null || gradeLabel.isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_GRADE_LABEL);
        }

        boolean removed = priceGrades.removeIf(pg -> pg.getGradeLabel().equals(gradeLabel));
        if (!removed) {
            throw new ProgramException(ProgramErrorCode.PRICE_GRADE_NOT_FOUND);
        }
    }

    public List<PriceGrade> getPriceGrades() {
        return Collections.unmodifiableList(priceGrades);
    }

    // ---- sectionCapacity 관련 ------------------------------

    /**
     * 구역별 허용 인원 추가.
     *
     * STANDING·FREE 타입 스케줄에서만 호출
     * SEATED 타입은 VenueSeat 기반이므로 이 메서드 호출 불가.
     *
     * 호출 전 Application 계층(CreateScheduleUseCase)에서
     * VenueClient를 통해 Section.capacity 상한 초과 여부를 사전 검증해야 함!!!!!!
     *
     * @throws ProgramException SEATED 타입에서 호출 시 SECTION_CAPACITY_NOT_ALLOWED
     * @throws ProgramException 동일 구역 중복 등록 시 SECTION_CAPACITY_DUPLICATE
     */
    public void addSectionCapacity(UUID sectionId, int capacity) {
        // 1. 타입 검증 — SEATED는 이 메서드 호출 자체가 불가
        if (this.program.getType() == ProgramType.SEATED) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_NOT_ALLOWED);
        }
        // 2. null 선검증
        if (sectionId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SECTION_ID);
        }
        // 3. 중복 검증
        boolean isDuplicate = sectionCapacities.stream().anyMatch(sc -> sc.getSectionId().equals(sectionId));
        if (isDuplicate) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_DUPLICATE);
        }

        // 4. 구역별 인원 합계 검증
        // int → long으로 변환하여 오버플로우 방지
        // currentTotal + capacity가 int 범위(약 21억)를 넘으면
        // 음수로 오버플로우되어 검증이 통과되는 문제를 차단
        long currentTotal = sectionCapacities.stream().mapToLong(ScheduleSectionCapacity::getCapacity).sum();
        long nextTotal = currentTotal + (long)capacity;
        if (nextTotal > this.totalCapacity) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_EXCEEDS_TOTAL);
        }

        sectionCapacities.add(ScheduleSectionCapacity.of(sectionId, capacity));
    }

    /**
     * 구역별 허용 인원 제거.
     * sectionId 기준으로 삭제
     * 수정 없음: 삭제 후 재등록 방식으로 처리
     *
     * @throws ProgramException sectionId가 null인 경우 INVALID_SECTION_ID
     * @throws ProgramException 존재하지 않는 구역인 경우 SECTION_CAPACITY_NOT_FOUND
     */
    public void removeSectionCapacity(UUID sectionId) {
        // 입력값 검증 — null이면 SECTION_CAPACITY_NOT_FOUND가 아닌 INVALID_SECTION_ID로 분리
        if (sectionId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SECTION_ID);
        }
        boolean removed = sectionCapacities.removeIf(sc -> sc.getSectionId().equals(sectionId));
        if (!removed) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_NOT_FOUND);
        }
    }

    /**
     * 외부에서 컬렉션을 직접 수정하지 못하도록 UnmodifiableList로 감싸서 반환
     */
    public List<ScheduleSectionCapacity> getSectionCapacities() {
        return Collections.unmodifiableList(sectionCapacities);
    }

    // ---- 판매 시각 여부 확인 편의 메서드 --------------------------

    /**
     * 현재 시각 기준으로 티켓 판매 가능 여부를 확인합니다.
     */
    public boolean isWithinSalePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(saleStartAt) && now.isBefore(saleEndAt);
    }

    // ----- 검증 메서드 -------------------------------------------

    /**
     * 기간 필드들에 대한 비즈니스 제약 조건을 검증합니다.
     */
    private static void validatePeriod(LocalDateTime eventStart, LocalDateTime eventEnd, LocalDateTime saleStart,
        LocalDateTime saleEnd) {
        // null 선검증 추가
        if (eventStart == null || eventEnd == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_EVENT_PERIOD);
        }
        if (saleStart == null || saleEnd == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SALE_PERIOD);
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
