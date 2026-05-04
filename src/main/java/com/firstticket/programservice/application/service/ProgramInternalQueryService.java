package com.firstticket.programservice.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.firstticket.programservice.application.dto.result.ScheduleBookingInfoResult;
import com.firstticket.programservice.domain.Program;
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

}
