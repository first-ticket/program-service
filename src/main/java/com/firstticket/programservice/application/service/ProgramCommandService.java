package com.firstticket.programservice.application.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.programservice.application.dto.command.AddPriceGradeCommand;
import com.firstticket.programservice.application.dto.command.AddSectionCapacityCommand;
import com.firstticket.programservice.application.dto.command.CreateProgramCommand;
import com.firstticket.programservice.application.dto.command.CreateScheduleCommand;
import com.firstticket.programservice.application.dto.command.UpdateProgramDraftCommand;
import com.firstticket.programservice.application.dto.command.UpdateProgramOnSaleCommand;
import com.firstticket.programservice.application.dto.command.UpdateScheduleCommand;
import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.ScheduleRepository;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.SectionValidationData;
import com.firstticket.programservice.domain.service.dto.VenueValidationData;

import lombok.RequiredArgsConstructor;

/**
 * 프로그램 도메인 커맨드 서비스.
 * 트랜잭션·흐름을 조율하며 도메인 메서드를 호출한다.
 * 도메인 로직은 도메인 객체에 위임하고, 이 서비스는 흐름만 조율한다.
 *
 * 권한 검증:
 * HOST·ADMIN 역할 검증은 Controller 계층(AuthContext)에서 처리한다.
 * 이 서비스에서는 프로그램 소유자(createdBy) 검증만 담당한다.
 *
 * MVP 제외 항목:
 * - Kafka 이벤트 발행 (scheduleCreated, programStatusChanged, programCancelled)
 * TODO: Kafka 도입 시 각 메서드에 이벤트 발행 추가
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProgramCommandService {

    private final ProgramRepository programRepository;
    private final ScheduleRepository scheduleRepository;
    private final VenueProvider venueProvider;

    // ----- 프로그램 생성 -------------------------------

    /**
     * 프로그램 생성 (P-01).
     * 초기 상태는 항상 DRAFT.
     * 새로 생성된 Program은 schedules가 빈 리스트로 초기화되므로
     * findByIdWithSchedules() 없이 바로 반환한다.
     *
     * TODO: Kafka 도입 시 ProgramStatusChangedEvent 발행 추가
     */
    public ProgramResult createProgram(CreateProgramCommand command) {
        validateCreateProgramCommand(command);

        // String → ProgramType 변환 — 잘못된 값이면 400 반환
        ProgramType type;
        try {
            type = ProgramType.valueOf(command.type());
        } catch (IllegalArgumentException e) {
            throw new ProgramException(ProgramErrorCode.INVALID_PROGRAM_TYPE);
        }

        Program program = Program.create(command.title(), command.category(), command.theme(), type,
            command.region(), command.posterUrl(), command.description());
        return ProgramResult.from(programRepository.save(program));
    }

    // --- 프로그램 수정 -------------------------------------

    /**
     * DRAFT 상태 프로그램 정보 수정 (P-02).
     * 제목·카테고리·테마·포스터·설명 수정 가능.
     * ProgramResult에 schedules가 포함되므로
     * findByIdWithSchedules()로 조회한다.
     */
    public ProgramResult updateProgramDraft(UUID requesterId, UpdateProgramDraftCommand command) {
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        program.updateDraft(command.title(), command.category(), command.theme(), command.posterUrl(),
            command.description());
        return ProgramResult.from(program);
    }

    /**
     * ON_SALE 상태 프로그램 정보 수정 (P-02).
     * 포스터·설명만 수정 가능.
     * ProgramResult에 schedules가 포함되므로
     * findByIdWithSchedules()로 조회한다.
     */
    public ProgramResult updateProgramOnSale(UUID requesterId, UpdateProgramOnSaleCommand command) {
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        program.updateOnSale(command.posterUrl(), command.description());
        return ProgramResult.from(program);
    }

    // --- 프로그램 상태 전이 -------------------------------------

    /**
     * 프로그램 판매 시작 (P-06).
     * DRAFT → ON_SALE 전이.
     * 최소 1개의 스케줄이 있어야 하므로 schedules 로딩이 필요하다.
     *
     * TODO: Kafka 도입 시 ProgramStatusChangedEvent 발행 추가
     */
    public void publishProgram(UUID requesterId, UUID programId) {
        Program program = findProgramWithSchedulesOrThrow(programId);
        checkOwner(program, requesterId);
        program.publish();
    }

    /**
     * 프로그램 취소 (P-03).
     * scheduleIds 수집이 필요하므로 schedules 로딩이 필요하다.
     * 반환값이 없으므로 findProgramWithSchedulesOrThrow() 사용.
     *
     * TODO: Kafka 도입 시 ProgramCancelledEvent, ProgramStatusChangedEvent 발행 추가
     *       발행 예시:
     *       List<UUID> scheduleIds = program.getSchedules().stream()
     *           .map(Schedule::getId).toList();
     *       programEvents.programCancelled(programId, scheduleIds);
     *       programEvents.programStatusChanged(ProgramStatusEvent.from(program));
     */
    public void cancelProgram(UUID requesterId, UUID programId) {
        Program program = findProgramWithSchedulesOrThrow(programId);
        checkOwner(program, requesterId);
        program.cancel();
    }

    /**
     * 프로그램 종료 (P-06).
     * ON_SALE·SOLD_OUT → CLOSED 전이.
     * 상태만 변경하고 반환값이 없으므로 findProgramOrThrow() 사용.
     * 이벤트를 따로 수신하지 않고, 관리자 및 주최자 임의로 변경 또는 배치로 자동 전이
     */
    public void closeProgram(UUID requesterId, UUID programId) {
        Program program = findProgramOrThrow(programId);
        checkOwner(program, requesterId);
        program.close();
    }

    /**
     * 프로그램 삭제 (P-03).
     * 예매 내역이 없는 DRAFT 상태에서만 물리 삭제.
     * 상태 확인 후 삭제하고 반환값이 없으므로 findProgramOrThrow() 사용.
     */
    public void deleteProgram(UUID requesterId, UUID programId) {
        Program program = findProgramOrThrow(programId);
        checkOwner(program, requesterId);

        if (program.getStatus() != ProgramStatus.DRAFT) {
            if (program.getStatus() != ProgramStatus.SOLD_OUT)
                throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_DELETABLE_IN_PROCESS);

            else
                throw new ProgramException((ProgramErrorCode.PROGRAM_NOT_DELETABLE));
        }

        programRepository.delete(program);
    }

    // ------ 스케줄 관련 --------------------------------------

    /**
     * 스케줄 등록 (P-07).
     *
     * 처리 순서:
     * 1. 공연장 존재 여부 확인 (VenueProvider)
     * 2. 공연장 시간 겹침 검증 (비관적 락 — V-04)
     * 3. 스케줄 생성
     *
     * TODO: Kafka 도입 시 아래 이벤트 발행 추가
     *   - ScheduleCreatedEvent: 좌석 서비스가 BookingSeat 생성
     *   - ProgramStatusChangedEvent: 대기열 서비스가 openAt·closeAt 갱신
     */
    public ProgramResult createSchedule(UUID requesterId, CreateScheduleCommand command) {
        validateCreateScheduleCommand(command);
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        // 기존: venueProvider.validateVenueExists(command.venueId());
        // 변경: venue 존재 확인 + totalCapacity 상한 검증을 한 번에 처리

        VenueValidationData venueValidation =
            venueProvider.validateVenue(command.venueId(), program.getType());

        // 스케줄 totalCapacity가 venue 내 해당 타입 구역 전체 수용량을 초과하는지 검증
        // ex) SEATED 프로그램인데 venue에 SEATED 구역 수용량 합계가 500석이면
        //     totalCapacity는 500을 넘을 수 없다
        if (command.totalCapacity() > venueValidation.totalCapacity()) {
            throw new ProgramException(ProgramErrorCode.TOTAL_CAPACITY_EXCEEDS_VENUE_LIMIT);
        }

        // 2. 공연장 시간 겹침 검증
        // 비관적 락으로 동시 요청 간 TOCTOU 방지 (V-04)
        // DB 레벨 exclusion constraint(tsrange)와 이중 방어
        boolean hasOverlap = !scheduleRepository.findOverlappingSchedulesWithLock(command.venueId(),
            command.eventStartAt(), command.eventEndAt()).isEmpty();
        if (hasOverlap) {
            throw new ProgramException(ProgramErrorCode.VENUE_TIME_CONFLICT);
        }

        // 3. 스케줄 생성
        program.addSchedule(command.venueId(), command.eventStartAt(), command.eventEndAt(), command.saleStartAt(),
            command.saleEndAt(), command.totalCapacity());
        programRepository.save(program);

        return ProgramResult.from(program);
    }

    /**
     * 스케줄 수정.
     * - DRAFT: 전체 필드 수정 가능
     * - ON_SALE·SOLD_OUT: eventStartAt, eventEndAt, totalCapacity만 수정 가능
     * - CANCELLED·CLOSED: 수정 불가
     *
     * TODO: Kafka 도입 시 ProgramStatusChangedEvent 발행 추가
     *   판매 기간 변경 시 대기열 서비스의 openAt·closeAt 갱신이 필요하다.
     */
    public ProgramResult updateSchedule(UUID requesterId, UpdateScheduleCommand command) {
        validateUpdateScheduleCommand(command);
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        Schedule schedule = findScheduleInProgram(program, command.scheduleId());

        // venueId가 변경되는 경우에만 존재 여부 확인
        if (command.venueId() != null) {
            VenueValidationData venueValidation =
                venueProvider.validateVenue(command.venueId(), program.getType());

            if (command.totalCapacity() > venueValidation.totalCapacity()) {
                throw new ProgramException(ProgramErrorCode.TOTAL_CAPACITY_EXCEEDS_VENUE_LIMIT);
            }

            // 변경된 venueId 기준으로 시간 겹침 검증
            // 자기 자신은 제외하고 검증
            UUID targetVenueId = command.venueId();
            LocalDateTime targetStart = command.eventStartAt() != null
                ? command.eventStartAt() : schedule.getEventStartAt();
            LocalDateTime targetEnd = command.eventEndAt() != null
                ? command.eventEndAt() : schedule.getEventEndAt();

            boolean hasOverlap = scheduleRepository
                .findOverlappingSchedulesWithLock(targetVenueId, targetStart, targetEnd)
                .stream()
                .anyMatch(s -> !s.getId().equals(command.scheduleId()));  // 자기 자신 제외

            if (hasOverlap) {
                throw new ProgramException(ProgramErrorCode.VENUE_TIME_CONFLICT);
            }
        }

        schedule.update(
            command.eventStartAt(), command.eventEndAt(),
            command.saleStartAt(), command.saleEndAt(),
            command.venueId(), command.totalCapacity()
        );
        return ProgramResult.from(program);
    }

    /**
     * 스케줄 삭제.
     * DRAFT 상태에서만 삭제 가능.
     * 반환값이 없으므로 findProgramWithSchedulesOrThrow() 사용
     * (removeSchedule()이 schedules 컬렉션을 직접 수정하기 때문).
     *
     * TODO: Kafka 도입 시 ProgramStatusChangedEvent 발행 추가
     */
    public void deleteSchedule(UUID requesterId, UUID programId, UUID scheduleId) {
        Program program = findProgramWithSchedulesOrThrow(programId);
        checkOwner(program, requesterId);
        program.removeSchedule(scheduleId);
    }

    // ---- 가격 등급 관련 --------------------------------------

    /**
     * 가격 등급 추가.
     * - SEATED·STANDING: sectionId 필수
     * - FREE: sectionId null
     * PriceGrade는 Schedule 하위이므로
     * findByIdWithSchedules()로 schedules 컬렉션을 로딩해야 한다.
     */
    public ProgramResult addPriceGrade(UUID requesterId, AddPriceGradeCommand command) {
        validateAddPriceGradeCommand(command);
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        Schedule schedule = findScheduleInProgram(program, command.scheduleId());

        // sectionId가 있는 타입(SEATED·STANDING)에서만 호출
        if (command.sectionId() != null) {
            SectionValidationData sectionValidation =
                venueProvider.validateSection(schedule.getVenueId(), command.sectionId());

            // section의 SeatType과 program의 ProgramType 일치 여부 검증
            // ex) SEATED 프로그램에 STANDING 구역을 등록하는 경우 차단
            if (!sectionValidation.seatType().equals(program.getType().name())) {
                throw new ProgramException(ProgramErrorCode.SECTION_TYPE_MISMATCH);
            }
        }

        schedule.addPriceGrade(command.sectionId(), command.gradeLabel(), command.price());
        return ProgramResult.from(program);
    }

    /**
     * 가격 등급 삭제.
     * 삭제 후 재등록 방식으로 처리한다.
     */
    public void removePriceGrade(UUID requesterId, UUID programId, UUID scheduleId, String gradeLabel) {
        Program program = findProgramWithSchedulesOrThrow(programId);
        checkOwner(program, requesterId);

        Schedule schedule = findScheduleInProgram(program, scheduleId);
        schedule.removePriceGrade(gradeLabel);
        ProgramResult.from(program);
    }

    // ---- 구역별 인원 관련 ---------------------------------------

    /**
     * 구역별 인원 추가 (STANDING·FREE 전용).
     *
     * 처리 순서:
     * 1. 소유자 검증
     * 2. VenueProvider로 Section.capacity 상한 검증
     * 3. 비관적 락으로 Schedule 조회 — 합계 불변식(sum ≤ totalCapacity) 보호
     * 4. 구역별 인원 추가
     *
     * 비관적 락 사용 이유:
     * 동시 요청이 메모리 중복 검사를 통과한 뒤 합계를 초과하는 race condition 방지.
     * &#064;Version  낙관적 락과 이중 방어 구조.
     */
    public ProgramResult addSectionCapacity(UUID requesterId, AddSectionCapacityCommand command) {
        validateAddSectionCapacityCommand(command);
        Program program = findProgramWithSchedulesOrThrow(command.programId());
        checkOwner(program, requesterId);

        // 비관적 락으로 합계 불변식(sum ≤ totalCapacity) 보호
        // findProgramWithSchedulesOrThrow()와 별도로 락을 걸어야 하므로
        // ScheduleRepository.findByIdWithLock() 사용
        Schedule schedule = scheduleRepository.findByIdWithLock(command.scheduleId())
            .orElseThrow(() -> new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND));

        // Schedule이 해당 Program에 속하는지 검증
        // 다른 Program의 Schedule을 수정하는 cross-program 수정 방지
        if (!schedule.getProgram().getId().equals(program.getId())) {
            throw new ProgramException(
                ProgramErrorCode.SCHEDULE_DOES_NOT_BELONG_TO_PROGRAM);
        }

        SectionValidationData sectionValidation =
            venueProvider.validateSection(schedule.getVenueId(), command.sectionId());

        // section의 SeatType과 program의 ProgramType 일치 여부 검증
        if (!sectionValidation.seatType().equals(program.getType().name())) {
            throw new ProgramException(ProgramErrorCode.SECTION_TYPE_MISMATCH);
        }

        // Section.capacity 상한 검증 (기존 getSectionCapacity() 대체)
        // sectionValidation.capacity()가 이미 SEATED→rowCount×colCount / STANDING·FREE→capacity 를 반환
        if (command.capacity() > sectionValidation.capacity()) {
            throw new ProgramException(ProgramErrorCode.SECTION_CAPACITY_EXCEEDS_VENUE_LIMIT);
        }

        schedule.addSectionCapacity(command.sectionId(), command.capacity());
        return ProgramResult.from(program);
    }

    /**
     * 구역별 인원 삭제.
     * 삭제 후 재등록 방식으로 처리한다.
     */
    public ProgramResult removeSectionCapacity(UUID requesterId, UUID programId, UUID scheduleId, UUID sectionId) {
        Program program = findProgramWithSchedulesOrThrow(programId);
        checkOwner(program, requesterId);

        Schedule schedule = findScheduleInProgram(program, scheduleId);
        schedule.removeSectionCapacity(sectionId);
        return ProgramResult.from(program);
    }

    // --- private 헬퍼 ---------------------------------------

    /**
     * 프로그램 단건 조회 — schedules 미로딩.
     * 사용처: 반환값이 없고 schedules가 불필요한 경우
     *   - closeProgram: 상태만 변경
     *   - deleteProgram: 상태 확인 후 삭제
     */
    private Program findProgramOrThrow(UUID programId) {
        return programRepository.findById(programId)
            .orElseThrow(() -> new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));
    }

    /**
     * 프로그램 단건 조회 — schedules JOIN FETCH.
     * 사용처:
     *   - ProgramResult 반환하는 모든 메서드 (schedules 포함)
     *   - schedules 컬렉션을 직접 수정하는 메서드
     *     (addSchedule, removeSchedule, addPriceGrade 등)
     *   - publish: schedules 비어있는지 확인 필요
     *   - cancel: scheduleIds 수집 필요
     */
    private Program findProgramWithSchedulesOrThrow(UUID programId) {
        return programRepository.findByIdWithSchedules(programId)
            .orElseThrow(() -> new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));
    }

    /**
     * Program 내에서 Schedule 단건 조회.
     * schedules가 이미 로딩된 상태에서 호출해야 한다.
     * findProgramWithSchedulesOrThrow() 호출 이후에만 사용한다.
     */
    private Schedule findScheduleInProgram(Program program, UUID scheduleId) {
        return program.getSchedules()
            .stream()
            .filter(s -> s.getId().equals(scheduleId))
            .findFirst()
            .orElseThrow(() -> new ProgramException(ProgramErrorCode.INVALID_SCHEDULE_ID));
    }

    /**
     * 프로그램 소유자 검증.
     * HOST는 자신이 생성한 프로그램만 수정·삭제할 수 있다.
     * ADMIN은 Controller에서 이미 통과했으므로 여기선 createdBy만 비교한다.
     */
    private void checkOwner(Program program, UUID requesterId) {
        validateRequesterId(requesterId);
        if (!program.getCreatedBy().equals(requesterId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // ── private 검증 메서드 ───────────────────────────────────────────────

    /**
     * 프로그램 생성 커맨드 검증.
     * Presentation 계층의 @Valid와 이중 방어.
     * null·blank 검증은 도메인에서도 수행하지만
     * Application 계층에서 먼저 차단하여 명확한 에러 메시지를 반환한다.
     */
    private void validateCreateProgramCommand(CreateProgramCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_TITLE);
        }
        if (command.category() == null || command.category().isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_CATEGORY);
        }
        if (command.theme() == null || command.theme().isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_THEME);
        }
        if (command.type() == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_PROGRAM_TYPE);
        }
        if (command.region() == null || command.region().isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_REGION);
        }
    }

    private void validateRequesterId(UUID requesterId) {
        if (requesterId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 스케줄 생성 커맨드 검증.
     * venueId, 기간 필드 null 검증 및 totalCapacity 범위 검증.
     */
    private void validateCreateScheduleCommand(CreateScheduleCommand command) {
        if (command.venueId() == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_VENUE_ID);
        }
        if (command.eventStartAt() == null || command.eventEndAt() == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_EVENT_PERIOD);
        }
        if (command.saleStartAt() == null || command.saleEndAt() == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SALE_PERIOD);
        }
        if (command.totalCapacity() <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);
        }
    }

    /**
     * 스케줄 수정 커맨드 검증.
     * scheduleId null 검증 및 totalCapacity 범위 검증.
     * 기간 필드는 부분 업데이트이므로 null 허용.
     */
    private void validateUpdateScheduleCommand(UpdateScheduleCommand command) {
        if (command.scheduleId() == null) {
            throw new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND);
        }
        // 도메인의 Schedule.update()와 일관성 유지 — 0도 차단
        if (command.totalCapacity() != null && command.totalCapacity() <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);
        }
    }

    /**
     * 가격 등급 추가 커맨드 검증.
     * gradeLabel null·blank 검증 및 price 범위 검증.
     */
    private void validateAddPriceGradeCommand(AddPriceGradeCommand command) {
        if (command.gradeLabel() == null || command.gradeLabel().isBlank()) {
            throw new ProgramException(ProgramErrorCode.INVALID_GRADE_LABEL);
        }
        if (command.price() < 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_PRICE);
        }
    }

    /**
     * 구역별 인원 추가 커맨드 검증.
     * sectionId null 검증 및 capacity 범위 검증.
     */
    private void validateAddSectionCapacityCommand(AddSectionCapacityCommand command) {
        if (command.sectionId() == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SECTION_ID);
        }
        if (command.capacity() <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);
        }
    }
}
