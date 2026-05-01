package com.firstticket.programservice.application.dto.result;

import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;

/**
 * 프로그램 단건 조회 결과 DTO.
 * Application 계층에서 도메인 객체를 이 DTO로 변환하여 반환한다.
 * Presentation 계층은 이 DTO를 받아 ProgramResponse로 변환한다.
 */
public record ProgramResult(
    UUID id,
    String title,
    String category,
    String theme,
    ProgramType type,
    ProgramStatus status,
    String region,
    String posterUrl,
    String description,
    List<ScheduleResult> schedules
) {
    /**
     * 도메인 객체 → 결과 DTO 변환.
     * Application 계층에서 호출한다.
     */
    public static ProgramResult from(Program program) {
        return new ProgramResult(
            program.getId(),
            program.getTitle(),
            program.getCategory(),
            program.getTheme(),
            program.getType(),
            program.getStatus(),
            program.getRegion(),
            program.getPosterUrl(),
            program.getDescription(),
            program.getSchedules().stream()
                .map(ScheduleResult::from)
                .toList()
        );
    }
}
