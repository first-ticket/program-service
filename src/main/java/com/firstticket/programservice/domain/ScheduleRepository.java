package com.firstticket.programservice.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Schedule Repository 인터페이스.
 * 도메인 계층에 위치하므로 JPA·Spring 의존성을 갖지 않는다.
 */
public interface ScheduleRepository {

    Optional<Schedule> findById(UUID id);

    /**
     * 동시성 보호 기반 공연장 일정 겹침 조회.
     * 동일 공연장에서 [eventStartAt, eventEndAt) 범위와 겹치는 스케줄 목록을 반환한다.
     * 중복 스케줄 등록 검증(V-04)에 사용하며, 동시 요청 간 TOCTOU를 방지한다.
     */
    List<Schedule> findOverlappingSchedulesWithLock(UUID venueId,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt);

    /**
     * 동시성 보호 기반 단건 조회.
     * sectionCapacity 추가 시 합계 불변식(sum ≤ totalCapacity) 보호에 사용한다.
     * 동시 수정 요청 간 충돌을 방지한다.
     */
    Optional<Schedule> findByIdWithLock(UUID id);

    /**
     * ID로 스케줄 단건 조회 — program JOIN FETCH.
     * ProgramInternalQueryService에서 트랜잭션 종료 후
     * schedule.getProgram().getId() 호출 시 LazyInitializationException 방지.
     */
    Optional<Schedule> findByIdWithProgram(UUID id);
}
