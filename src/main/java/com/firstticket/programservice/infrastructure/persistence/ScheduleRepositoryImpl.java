package com.firstticket.programservice.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScheduleRepositoryImpl implements ScheduleRepository {

    private final ScheduleJpaRepository scheduleJpaRepository;

    @Override
    public Optional<Schedule> findById(UUID id) {
        return scheduleJpaRepository.findById(id);
    }

    @Override
    public List<Schedule> findOverlappingSchedulesWithLock(UUID venueId,
        LocalDateTime eventStartAt,
        LocalDateTime eventEndAt) {
        return scheduleJpaRepository
            .findOverlappingSchedulesWithLock(venueId, eventStartAt, eventEndAt);
    }

    @Override
    public Optional<Schedule> findByIdWithProgram(UUID id) {
        return scheduleJpaRepository.findByIdWithProgram(id);
    }

    @Override
    public Optional<Schedule> findByIdWithLock(UUID id) {
        return scheduleJpaRepository.findByIdWithLock(id);
    }
}
