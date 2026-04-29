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
     * 공연장 시간 범위 겹침 검증용 비관적 락 조회.
     * 동일 venue_id에서 [eventStartAt, eventEndAt) 범위와 겹치는 스케줄 목록 반환.
     * SELECT FOR UPDATE로 잠금하여 동시 요청 간 TOCTOU를 방지한다.
     *
     * DB 레벨: exclusion constraint(tsrange)가 최후 방어선
     * Application 레벨: 이 쿼리로 명확한 에러 메시지 반환
     */
    List<Schedule> findOverlappingSchedulesWithLock(UUID venueId,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt);

    /**
     * sectionCapacity 추가 시 합계 불변식 보호용 비관적 락 조회.
     * SELECT FOR UPDATE로 해당 Schedule 행을 잠금 후 반환한다.
     */
    Optional<Schedule> findByIdWithLock(UUID id);
}
