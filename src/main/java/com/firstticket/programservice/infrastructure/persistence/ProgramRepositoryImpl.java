package com.firstticket.programservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;
import com.firstticket.programservice.domain.ProgramStatus;

import lombok.RequiredArgsConstructor;

/**
 * ProgramRepository 구현체.
 * 도메인 인터페이스와 JPA Repository 사이의 어댑터 역할.
 */
@Repository
@RequiredArgsConstructor
public class ProgramRepositoryImpl implements ProgramRepository {

    private final ProgramJpaRepository programJpaRepository;

    @Override
    public Program save(Program program) {
        return programJpaRepository.save(program);
    }

    @Override
    public Optional<Program> findById(UUID id) {
        return programJpaRepository.findById(id);
    }

    @Override
    public Optional<Program> findByIdWithSchedules(UUID id) {
        return programJpaRepository.findByIdWithSchedules(id);
    }

    /**
     * 특정 공연장에 활성 프로그램 존재 여부 확인.
     * ProgramJpaRepository에 위임한다.
     */
    @Override
    public boolean existsByVenueIdAndStatusNotIn(UUID venueId, List<ProgramStatus> excludeStatuses) {
        return programJpaRepository.existsByVenueIdAndStatusNotIn(venueId, excludeStatuses);
    }

    @Override
    public void delete(Program program) {
        programJpaRepository.delete(program);
    }

    @Override
    public boolean existsById(UUID id) {
        return programJpaRepository.existsById(id);
    }
}
