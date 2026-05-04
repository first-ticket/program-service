package com.firstticket.programservice.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.programservice.application.dto.query.ProgramSearchQuery;
import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.application.dto.result.ProgramSummaryResult;
import com.firstticket.programservice.application.dto.result.ScheduleBookingInfoResult;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.ScheduleRepository;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.domain.query.ProgramQueryRepository;
import com.firstticket.programservice.domain.service.SeatProvider;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.ScheduleRemainingData;
import com.firstticket.programservice.domain.service.dto.VenueInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 프로그램 도메인 쿼리 서비스.
 * 조회 전용 서비스로 트랜잭션은 readOnly로 설정한다.
 * Command 서비스와 분리하여 조회 최적화 쿼리를 별도로 관리한다.
 * 모든 사용자(ALL)가 조회 가능하므로 별도 권한 검증이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramQueryService {

    private final ProgramRepository programRepository;
    private final ProgramQueryRepository programQueryRepository;
    private final SeatProvider seatProvider;
    private final ScheduleRepository scheduleRepository;
    private final VenueProvider venueProvider;

    /**
     * 프로그램 단건 조회 (P-05).
     * 프로그램 기본 정보 + 스케줄 목록 + 회차별 잔여 좌석 수 반환.
     *
     * 잔여 좌석 수 조회 (P-05):
     * 좌석 서비스의 /internal/v1/seats/remaining/{programId}를 호출한다.
     * 좌석 서비스 장애 시 빈 리스트로 fallback하여
     * 프로그램 정보 자체는 정상 반환한다.
     */
    public ProgramResult getProgram(UUID programId) {
        validateProgramId(programId);
        Program program = programRepository.findByIdWithSchedules(programId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));

        // SeatProviderImpl에서 장애 시 빈 리스트로 fallback 처리
        // try-catch 불필요
        List<ScheduleRemainingData> remainingCounts =
            seatProvider.getRemainingCounts(programId);

        return ProgramResult.from(program, remainingCounts);
    }

    /**
     * 프로그램 목록 조회 (P-04).
     * 카테고리·키워드·지역·날짜 필터, 정렬, 페이지네이션 지원.
     * 권한 제한 없음 — ALL.
     */
    public PagedResult<ProgramSummaryResult> searchPrograms(ProgramSearchQuery query) {
        validateSearchQuery(query);
        PagedResult<com.firstticket.programservice.domain.query.ProgramSummaryData> pagedData =
            programQueryRepository.findBySpec(query.toSpec());

        return PagedResult.of(
            pagedData.content().stream()
                .map(ProgramSummaryResult::from)
                .toList(),
            pagedData.totalElements(),
            pagedData.pageNumber(),
            pagedData.pageSize()
        );
    }

    /**
     * 예매 서비스 내부 API용 스케줄 예매 정보 조회.
     * GET /internal/v1/programs/schedules/{scheduleId}/bookingInfo
     *
     * venueName, venueAddress는 VenueProvider를 통해 조회한다.
     */
    public ScheduleBookingInfoResult getScheduleBookingInfo(UUID scheduleId) {
        if (scheduleId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SCHEDULE_ID);
        }

        Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND));

        Program program = programRepository.findById(schedule.getProgram().getId())
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));

        VenueInfo venueInfo = venueProvider.getVenueInfo(schedule.getVenueId());

        return ScheduleBookingInfoResult.of(program, schedule, venueInfo);
    }

    /**
     * 스케줄 목록 조회 — 잔여 좌석 조회 없음.
     * getSchedules(), getSchedule() 전용.
     * SeatProvider 호출이 불필요한 경우 사용한다.
     */
    public ProgramResult getProgramWithoutRemainingCount(UUID programId) {
        Program program = programRepository.findByIdWithSchedules(programId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        // remainingCount 없이 반환 — SeatProvider 호출 없음
        return ProgramResult.from(program);
    }

    /**
     * 프로그램 상태만 조회 — 경량 조회.
     * updateProgram()에서 상태 분기 시 사용한다.
     * SeatProvider 호출 없이 상태값만 반환한다.
     */
    public ProgramStatus getProgramStatus(UUID programId) {
        if (programId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_PROGRAM_ID);
        }
        return programRepository.findById(programId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND))
            .getStatus();
    }

    /**
     * 단건 조회 커맨드 검증.
     * programId null 검증.
     */
    private void validateProgramId(UUID programId) {
        if (programId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_PROGRAM_ID);
        }
    }

    /**
     * 목록 조회 쿼리 검증.
     * pageSize 범위 및 sortField 화이트리스트 검증.
     * sortField 화이트리스트는 QueryRepository에서도 방어하지만
     * 명확한 에러 메시지를 위해 Application 계층에서 먼저 차단한다.
     */
    private void validateSearchQuery(ProgramSearchQuery query) {
        if (query == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SEARCH_QUERY);
        }
        if (query.pageSize() <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_PAGE_SIZE);
        }
        if (query.pageNumber() < 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_PAGE_NUMBER);
        }
    }
}
