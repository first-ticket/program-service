// infrastructure/messaging/payload/ProgramCreatedPayload.java

package com.firstticket.programservice.infrastructure.messaging.payload;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;

/**
 * program.created 토픽 Kafka 페이로드.
 * 대기열 서비스가 수신하여 프로그램 대기열을 초기화한다.
 * 생성 시점에는 스케줄이 없으므로 openAt·closeAt은 null로 발행한다.
 */
public record ProgramCreatedPayload(
    UUID programId,
    LocalDateTime openAt,   // nullable — 스케줄 미등록 시 null
    LocalDateTime closeAt,  // nullable — 스케줄 미등록 시 null
    String status           // 항상 "DRAFT"
) {
    public static ProgramCreatedPayload from(Program program) {
        return new ProgramCreatedPayload(
            program.getId(),
            null,
            null,
            ProgramStatus.DRAFT.name()
        );
    }
}
