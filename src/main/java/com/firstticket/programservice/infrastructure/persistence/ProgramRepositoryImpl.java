package com.firstticket.programservice.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.ProgramRepository;

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
        return programJpaRepository.findActiveById(id);
    }

    @Override
    public Optional<Program> findByIdWithSchedules(UUID id) {
        return programJpaRepository.findByIdWithSchedules(id);
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
