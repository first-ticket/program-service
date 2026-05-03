package com.firstticket.programservice.application.dto.result;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.service.dto.ScheduleRemainingData;

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
     * 잔여 좌석 수 포함 변환.
     * getProgram() 단건 조회 시 사용한다.
     */
    public static ProgramResult from(Program program,
        List<ScheduleRemainingData> remainingCounts) {
        // scheduleId → remainingCount 맵 구성
        Map<UUID, Integer> remainingMap = remainingCounts.stream()
            .collect(Collectors.toMap(
                ScheduleRemainingData::scheduleId,
                ScheduleRemainingData::remainingCount,
                (existing, incoming) -> incoming
            ));

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
                .map(s -> ScheduleResult.from(s,
                    remainingMap.getOrDefault(s.getId(), 0)))
                .toList()
        );
    }

    /**
     * 잔여 좌석 수 미포함 변환.
     * createProgram() 등 Command 메서드 반환 시 사용한다.
     * 새로 생성·수정된 Program은 잔여 좌석 조회가 불필요하다.
     */
    public static ProgramResult from(Program program) {
        return from(program, List.of());
    }
}
