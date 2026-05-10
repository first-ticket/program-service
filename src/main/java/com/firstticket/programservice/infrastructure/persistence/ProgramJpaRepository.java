package com.firstticket.programservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramStatus;

/**
 * Spring Data JPA Repository.
 * 도메인 계층에 직접 노출하지 않는다.
 * ProgramRepositoryImpl을 통해서만 접근한다.
 */
public interface ProgramJpaRepository extends JpaRepository<Program, UUID> {

    /**
     * soft delete 제외 단건 조회.
     * JpaRepository 기본 findById()를 재정의하여
     * deletedAt이 있는 레코드를 반환하지 않는다.
     */
    @NonNull
    @Override
    @Query("SELECT v FROM Program v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<Program> findById(@NonNull @Param("id") UUID id);

    @Override
    @Query("""
        SELECT COUNT(p) > 0 FROM Program p
        WHERE p.id = :id
          AND p.deletedAt IS NULL
        """)
    boolean existsById(@NonNull @Param("id") UUID id);

    /**
     * schedules 컬렉션 JOIN FETCH 조회.
     * N+1 방지. addSchedule·removeSchedule 유스케이스에서 사용.
     * DISTINCT로 Program 레벨 중복을 제거, 단건 반환 보장
     */
    @Query("""
        SELECT DISTINCT p FROM Program p
        LEFT JOIN FETCH p.schedules s
        WHERE p.id = :id
          AND p.deletedAt IS NULL
        """)
    Optional<Program> findByIdWithSchedules(@Param("id") UUID id);

    /**
     * 특정 공연장에 활성 프로그램이 존재하는지 확인.
     * Venue Service의 deleteVenue() 에서 공연장 삭제 가능 여부 판단에 사용한다.
     *
     * venueId는 Schedule에 있으므로 schedules JOIN이 필요하다.
     * CANCELLED·CLOSED는 이미 종료된 프로그램이므로 제외한다.
     * soft delete된 프로그램도 제외한다.
     *
     * @param venueId        삭제하려는 공연장 ID
     * @param excludeStatuses 집계에서 제외할 상태 목록 (CANCELLED·CLOSED)
     */
    @Query("""
        SELECT COUNT(p) > 0 FROM Program p
        JOIN p.schedules s
        WHERE s.venueId = :venueId
          AND p.deletedAt IS NULL
          AND p.status NOT IN :excludeStatuses
        """)
    boolean existsByVenueIdAndStatusNotIn(
        @Param("venueId") UUID venueId,
        @Param("excludeStatuses") List<ProgramStatus> excludeStatuses
    );
}
