package com.firstticket.programservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.firstticket.programservice.domain.Program;

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
}
