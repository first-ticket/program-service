package com.firstticket.programservice.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.Schedule;

/**
 * 프로그램 판매 기간 변경 이벤트 페이로드 (토픽: program.time.updated).
 * 대기열 서비스가 수신하여 openAt·closeAt을 갱신한다.
 *
 * 발행 시점:
 * - publishProgram(): ON_SALE 전환 시
 * - updateSchedule(): 판매 기간 변경 시
 *
 * openAt: 변경된 saleStartAt
 * closeAt: 변경된 saleEndAt
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
