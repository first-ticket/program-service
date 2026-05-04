package com.firstticket.programservice.application.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.ScheduleRepository;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;

import lombok.RequiredArgsConstructor;

// ProgramScheduleReader.java — DB 조회 전용 커넥션 최적화 용도의 서비스 빈
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramScheduleReader {

    private final ScheduleRepository scheduleRepository;
    private final ProgramRepository programRepository;

    public Schedule findSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND));
    }

    public Program findProgram(UUID programId) {
        return programRepository.findById(programId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));
    }
}
