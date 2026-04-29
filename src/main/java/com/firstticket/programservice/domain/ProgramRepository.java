package com.firstticket.programservice.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Program 애그리거트 Repository 인터페이스.
 * 도메인 계층에 위치하므로 JPA·Spring 의존성을 갖지 않는다.
 * 구현체는 infrastructure/persistence/ProgramRepositoryImpl에 위치한다.
 */
public interface ProgramRepository {

    /** 프로그램 저장 (생성·수정) */
    Program save(Program program);

    /**
     * ID로 프로그램 단건 조회.
     * soft delete된 프로그램은 반환하지 않는다.
     */
    Optional<Program> findById(UUID id);

    /**
     * ID로 프로그램 단건 조회 — schedules 컬렉션 함께 로딩.
     * addSchedule·removeSchedule 등 스케줄 수정이 필요한 유스케이스에서 사용한다.
     * N+1 방지를 위해 JOIN FETCH로 구현한다.
     */
    Optional<Program> findByIdWithSchedules(UUID id);

    /** 프로그램 물리 삭제 (예매 내역 없는 DRAFT 상태에서만 허용) */
    void delete(Program program);

    /** ID 존재 여부 확인 */
    boolean existsById(UUID id);
}
