package com.firstticket.programservice.infrastructure.messaging.payload;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.Schedule;

/**
 * program.time.updated 토픽 Kafka 페이로드.
 * 대기열 서비스가 수신하여 openAt·closeAt을 갱신한다.
 * publishProgram() / updateSchedule() 에서 발행한다.
 */
public record ProgramTimeUpdatedPayload(
    UUID programId,
    LocalDateTime openAt,
    LocalDateTime closeAt
) {
    public static ProgramTimeUpdatedPayload from(Program program, Schedule schedule) {
        return new ProgramTimeUpdatedPayload(
            program.getId(),
            schedule.getSaleStartAt(),
            schedule.getSaleEndAt()
        );
    }
}
