package com.firstticket.programservice.application.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Schedule findSchedule(UUID scheduleId) {
        // JOIN FETCH로 program을 함께 로딩하여
        // 트랜잭션 종료 후 LazyInitializationException 방지
        return scheduleRepository.findByIdWithProgram(scheduleId)
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND));
    }
}
