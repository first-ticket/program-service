package com.firstticket.programservice.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.firstticket.programservice.domain.Schedule;

import jakarta.persistence.LockModeType;

public interface ScheduleJpaRepository extends JpaRepository<Schedule, UUID> {

    @NonNull
    @Override
    @Query("SELECT s FROM Schedule s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Schedule> findById(@NonNull @Param("id") UUID id);

    /**
     * 공연장 시간 범위 겹침 검증 — 비관적 락.
     *
     * [eventStartAt, eventEndAt) 범위와 겹치는 스케줄을 SELECT FOR UPDATE로 조회.
     * 겹침 조건: 기존 스케줄의 시작이 새 종료보다 이전 AND 기존 종료가 새 시작보다 이후
     * soft delete된 스케줄은 제외.
     *
     * DB 레벨 exclusion constraint(tsrange)와 이중 방어 구조.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM Schedule s
        WHERE s.venueId = :venueId
          AND s.deletedAt IS NULL
          AND s.eventStartAt < :eventEndAt
          AND s.eventEndAt > :eventStartAt
        """)
    List<Schedule> findOverlappingSchedulesWithLock(
        @Param("venueId") UUID venueId,
        @Param("eventStartAt") LocalDateTime eventStartAt,
        @Param("eventEndAt") LocalDateTime eventEndAt
    );

    /**
     * sectionCapacity 추가용 비관적 락 조회.
     * 합계 불변식(sum ≤ totalCapacity) 보호.
     * &#064;Version  낙관적 락과 이중 방어 구조.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Schedule> findByIdWithLock(@Param("id") UUID id);
}
