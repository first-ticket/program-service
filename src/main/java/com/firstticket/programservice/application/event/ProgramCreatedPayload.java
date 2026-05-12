package com.firstticket.programservice.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;

/**
 * 프로그램 생성 이벤트 페이로드 (토픽: program.created).
 * 대기열 서비스가 수신하여 프로그램 대기열을 초기화한다.
 * createProgram() 시 발행한다.
 *
 * status는 항상 DRAFT로 고정된다.
 * openAt: 가장 빠른 스케줄의 saleStartAt
 * closeAt: 가장 늦은 스케줄의 saleEndAt
 * → 스케줄이 없는 DRAFT 초기 생성 시점이므로 null 허용
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
