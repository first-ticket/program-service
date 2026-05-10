package com.firstticket.programservice.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.firstticket.programservice.application.dto.result.ScheduleBookingInfoResult;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.service.VenueProvider;
import com.firstticket.programservice.domain.service.dto.VenueInfo;

import lombok.RequiredArgsConstructor;

// ProgramInternalQueryService.java — 내부 API 전용 서비스
@Service
@RequiredArgsConstructor
public class ProgramInternalQueryService {

    private final ProgramScheduleReader scheduleReader;
    private final VenueProvider venueProvider;
    private final ProgramRepository programRepository;

    /**
     * 외부 호출(VenueProvider)이 포함되어 있어
     * 트랜잭션을 열지 않는다.
     * DB 조회와 외부 호출을 분리하여 커넥션 점유 시간을 최소화한다.
     */
    public ScheduleBookingInfoResult getScheduleBookingInfo(UUID scheduleId) {
        if (scheduleId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SCHEDULE_ID);
        }

        // JOIN FETCH로 program이 이미 로딩됨
        Schedule schedule = scheduleReader.findSchedule(scheduleId);

        // 트랜잭션 종료 후에도 안전 — JOIN FETCH로 이미 로딩된 데이터
        Program program = schedule.getProgram();

        VenueInfo venueInfo = venueProvider.getVenueInfo(schedule.getVenueId());

        return ScheduleBookingInfoResult.of(program, schedule, venueInfo);
    }

    /**
     * 해당 공연장에 활성 프로그램이 존재하는지 확인.
     * Venue Service의 deleteVenue() 에서 ProgramProvider를 통해 호출한다.
     *
     * CANCELLED·CLOSED 상태는 이미 종료된 프로그램이므로
     * 공연장 삭제를 막지 않는다.
     *
     * 트랜잭션 불필요 — 단순 존재 여부 조회.
     *
     * @param venueId 삭제하려는 공연장 ID
     * @return 활성 프로그램(DRAFT·ON_SALE·SOLD_OUT)이 하나라도 있으면 true
     */
    public boolean hasProgramsForVenue(UUID venueId) {
        if (venueId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_VENUE_ID);
        }

        // CANCELLED·CLOSED는 이미 종료된 프로그램 — 공연장 삭제에 영향 없음
        List<ProgramStatus> excludedStatuses = List.of(
            ProgramStatus.CANCELLED,
            ProgramStatus.CLOSED
        );

        return programRepository.existsByVenueIdAndStatusNotIn(venueId, excludedStatuses);
    }

}
