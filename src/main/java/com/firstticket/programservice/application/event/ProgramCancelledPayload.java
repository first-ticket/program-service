package com.firstticket.programservice.application.event;

import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;

/**
 * 프로그램 취소 이벤트 페이로드 (토픽: program.cancelled).
 * 대기열 서비스가 수신하여 대기열을 종료한다.
 * 예매 서비스가 수신하여 해당 프로그램 예매를 일괄 취소한다.
 *
 * status는 항상 CANCELLED로 고정된다.
 */
public record ProgramCancelledPayload(
    UUID programId,
    String status   // 항상 "CANCELLED"
) {
    public static ProgramCancelledPayload from(Program program) {
        return new ProgramCancelledPayload(
            program.getId(),
            ProgramStatus.CANCELLED.name()
        );
    }
}
