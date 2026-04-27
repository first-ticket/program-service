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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_program")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program extends BaseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String theme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgramType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgramStatus status;

    @Column(columnDefinition = "TEXT")
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Program 애그리거트 루트가 Schedule 컬렉션을 직접 관리
     * Schedule은 Program을 통해서만 생성·삭제되어야 하며,
     * 외부에서 ScheduleRepository로 직접 저장하지 않습니다.
     */
    @OneToMany(mappedBy = "program",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
    private List<Schedule> schedules;

    // --------- 프로그램 생성 정적 메서드 -----------------------------------

    /**
     * 신규 공연 생성 (Static Factory Method) 정적 팩토리 메서드.
     * 초기 생성 시 상태는 항상 DRAFT(초안)로 설정됩니다.
     */
    public static Program create(String title, String category, String theme,
        ProgramType type, String posterUrl, String description) {
        // 프로그램 주요 정보 검증
        validateProgramInfo(title, category, theme, type);
        return new Program(
            null,
            title, category, theme,
            type, ProgramStatus.DRAFT,
            posterUrl, description,
            new ArrayList<>()
        );
    }

    // -----------schedule 관련 -------------------------------------------

    //TODO: 개별 Schedule 취소 기능 추가 시 ScheduleStatus 도입 여부 고려
    //      → 기본 기능 구현 후 고도화 시 feature/schedule-status 브랜치에서 작업

    /**
     * 스케줄 추가는 반드시 Program을 통해서만 가능
     * SOLD_OUT 상태라도 새 회차를 추가하면 다시 판매가 가능해지므로, 스케줄 추가가 가능합니다.
     * V-04: 공연장 중복 예약의 주된 검증은 Application 계층에서 진행
     * 공연장 중복 예약 검증(V-04) 처리 레이어:
     *   1. DB: exclusion constraint (tsrange)로 범위 겹침 원천 차단
     *   2. Application: VenueClient를 통해 선행 검증 후에
     *      findOverlappingSchedulesWithLock()으로
     *      비관적 잠금 후 선검증 → 명확한 에러 메시지 반환
     *   3. Domain: 이 메서드는 기간 유효성(시작<종료)만 검증
     *      공연장 중복 여부는 Application 계층 책임
     * 공연에 새로운 회차(Schedule)를 추가합니다.
     * @throws ProgramException 공연이 취소(CANCELLED)되었거나 종료(CLOSED)된 경우 수정 불가
     */
    public Schedule addSchedule(UUID venueId,
        LocalDateTime eventStartAt, LocalDateTime eventEndAt,
        LocalDateTime saleStartAt, LocalDateTime saleEndAt,
        int totalCapacity) {
        // 프로그램 상태가 CANCELLED, CLOSED 일 경우, 스케줄 추가는 불가능
        if (status == ProgramStatus.CANCELLED || status == ProgramStatus.CLOSED) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }
        Schedule schedule = Schedule.create(
            this, venueId, eventStartAt, eventEndAt, saleStartAt, saleEndAt, totalCapacity
        );
        schedules.add(schedule);
        return schedule;
    }

    /**
     * DRAFT 상태에서만 스케줄 삭제 가능.
     * ON_SALE 이후에는 예매가 진행 중일 수 있으므로
     * Program 전체를 CANCELLED 처리해야 함
     */
    public void removeSchedule(UUID scheduleId) {
        if (this.status != ProgramStatus.DRAFT) {
            throw new ProgramException(ProgramErrorCode.SCHEDULE_NOT_DELETABLE);
        }
        boolean removed = schedules.removeIf(s -> s.getId().equals(scheduleId));
        if (!removed) {
            throw new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    /**
     * 외부에서 리스트를 직접 수정하는 것을 방지하기 위해 불변 리스트로 반환합니다.
     */
    public List<Schedule> getSchedules() {
        return Collections.unmodifiableList(schedules);
    }

    // ------------ 프로그램 상태 관련 -------------------------------------------

    /**
     * 공연을 판매 중(ON_SALE) 상태로 전환합니다.
     * @throws ProgramException 등록된 스케줄이 하나도 없는 경우 공개 불가
     */
    public void publish() {
        status.validateTransition(ProgramStatus.ON_SALE);
        if (schedules.isEmpty()) {
            throw new ProgramException(ProgramErrorCode.SCHEDULE_REQUIRED);
        }
        this.status = ProgramStatus.ON_SALE;
    }

    /**
     * 공연을 취소(CANCELLED) 상태로 전환합니다.
     * 취소 후 Kafka ProgramCancelledEvent 발행은
     * Application 계층(CancelProgramUseCase)에서 처리
     * TODO: Program 취소 시 하위 Schedule도 함께 CANCELLED 처리 예정
     *       → feature/schedule-status 브랜치에서 작업
     */
    public void cancel() {
        status.validateTransition(ProgramStatus.CANCELLED);
        this.status = ProgramStatus.CANCELLED;
    }

    /**
     * 공연을 종료(CLOSED) 상태로 전환합니다.
     * 공연 일정이 모두 마무리된 후 운영자가 수동으로 닫거나
     * 배치로 자동 전이한다.
     */
    public void close() {
        status.validateTransition(ProgramStatus.CLOSED);
        this.status = ProgramStatus.CLOSED;
    }

    // ---------- 프로그램 상태에 따른 정보 수정 -------------------------------

    /**
     * 초안(DRAFT) 상태의 공연 정보를 수정합니다. (주요 필드 수정 가능)
     */
    public void updateDraft(String title, String category, String theme,
        String posterUrl, String description) {
        if (this.status != ProgramStatus.DRAFT) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }
        String nextTitle = (title != null) ? title : this.title;
        String nextCategory = (category != null) ? category : this.category;
        String nextTheme = (theme != null) ? theme : this.theme;
        validateProgramInfo(nextTitle, nextCategory, nextTheme, this.type);

        this.title = nextTitle;
        this.category = nextCategory;
        this.theme = nextTheme;
        if (posterUrl != null)
            this.posterUrl = posterUrl;
        if (description != null)
            this.description = description;
    }

    /**
     * 판매(ON_SALE) 중인 공연의 정보를 수정합니다. (포스터 및 설명 등 일부 정보만 허용)
     */
    public void updateOnSale(String posterUrl, String description) {
        if (this.status != ProgramStatus.ON_SALE) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }
        if (posterUrl != null)
            this.posterUrl = posterUrl;
        if (description != null)
            this.description = description;
    }

    // ---------- 검증 메서드 -----------------------------------------------------

    private static void validateProgramInfo(String title, String category, String theme, ProgramType type) {
        // null/blank 검증
        if (title == null || title.isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_TITLE);
        }
        if (category == null || category.isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_CATEGORY);
        }
        if (theme == null || theme.isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_THEME);
        }
        if (type == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_PROGRAM_TYPE);
        }
    }
}
