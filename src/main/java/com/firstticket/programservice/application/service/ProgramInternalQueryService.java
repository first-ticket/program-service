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
        // 별도 빈 호출 → 프록시 정상 작동 → 트랜잭션 적용
        Schedule schedule = scheduleReader.findSchedule(scheduleId);
        Program program = scheduleReader.findProgram(
            schedule.getProgram().getId());

        // 외부 호출 — 트랜잭션 밖에서 실행
        VenueInfo venueInfo = venueProvider.getVenueInfo(schedule.getVenueId());
        return ScheduleBookingInfoResult.of(program, schedule, venueInfo);
    }

}
