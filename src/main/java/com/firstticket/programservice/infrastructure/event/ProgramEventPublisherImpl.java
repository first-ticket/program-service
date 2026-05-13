package com.firstticket.programservice.infrastructure.event;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.firstticket.common.messaging.event.Events;
import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.Schedule;
import com.firstticket.programservice.domain.event.ProgramEventPublisher;
import com.firstticket.programservice.domain.event.ScheduleCreatedEventData;
import com.firstticket.programservice.infrastructure.messaging.payload.ProgramCancelledPayload;
import com.firstticket.programservice.infrastructure.messaging.payload.ProgramCreatedPayload;
import com.firstticket.programservice.infrastructure.messaging.payload.ProgramTimeUpdatedPayload;
import com.firstticket.programservice.infrastructure.messaging.payload.ScheduleCreatedPayload;

import lombok.RequiredArgsConstructor;

/**
 * ProgramEventPublisher 구현체.
 * 구조 참고: infrastructure/event/SampleEventsImpl.java
 *
 * Events.publish() (Outbox 패턴)를 통해 Kafka 이벤트를 발행한다.
 * 도메인 이벤트 데이터(ScheduleCreatedEventData 등)를
 * Kafka 페이로드(ScheduleCreatedPayload 등)로 변환하여 발행한다.
 *
 * 모든 메서드는 호출자(@Transactional 메서드) 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class ProgramEventPublisherImpl implements ProgramEventPublisher {

    @Value("${kafka.topics.schedule-created}")
    private String scheduleCreated;

    @Value("${kafka.topics.program-created}")
    private String programCreated;

    @Value("${kafka.topics.program-time-updated}")
    private String programTimeUpdated;

    @Value("${kafka.topics.program-cancelled}")
    private String programCancelled;

    /**
     * 프로그램 생성 이벤트 발행 (토픽: program.created).
     * aggregateId로 program.getId()를 사용하므로
     * 반드시 save() 이후에 호출해야 한다.
     */
    @Override
    public void publishProgramCreated(Program program) {
        Events.publish(
            UUID.randomUUID().toString(),
            "PROGRAM",
            program.getId(),
            programCreated,
            ProgramCreatedPayload.from(program)
        );
    }

    /**
     * 스케줄 생성 이벤트 발행 (토픽: schedule.created).
     * ScheduleCreatedEventData.SeatTemplate →
     * ScheduleCreatedPayload.SeatTemplate 변환 후 발행한다.
     */
    @Override
    public void publishScheduleCreated(Program program, Schedule schedule,
        List<ScheduleCreatedEventData.SeatTemplate> seatTemplates) {

        // 도메인 SeatTemplate → Kafka 페이로드 SeatTemplate 변환
        List<ScheduleCreatedPayload.SeatTemplate> payloadTemplates = seatTemplates.stream()
            .map(t -> new ScheduleCreatedPayload.SeatTemplate(
                t.sectionId(), t.sectionName(), t.seatType(),
                t.rowCount(), t.colCount(), t.capacity(), t.price()
            ))
            .toList();

        Events.publish(
            UUID.randomUUID().toString(),
            "SCHEDULE",
            schedule.getId(),
            scheduleCreated,
            new ScheduleCreatedPayload(schedule.getId(), program.getId(), payloadTemplates)
        );
    }

    /**
     * 프로그램 판매 기간 변경 이벤트 발행 (토픽: program.time.updated).
     * 스케줄별 saleStartAt·saleEndAt을 대기열 서비스에 전달한다.
     */
    @Override
    public void publishProgramTimeUpdated(Program program, Schedule schedule) {
        Events.publish(
            UUID.randomUUID().toString(),
            "PROGRAM",
            program.getId(),
            programTimeUpdated,
            ProgramTimeUpdatedPayload.from(program, schedule)
        );
    }

    /**
     * 프로그램 취소 이벤트 발행 (토픽: program.cancelled).
     * 대기열 서비스·예매 서비스가 모두 수신한다.
     */
    @Override
    public void publishProgramCancelled(Program program) {
        Events.publish(
            UUID.randomUUID().toString(),
            "PROGRAM",
            program.getId(),
            programCancelled,
            ProgramCancelledPayload.from(program)
        );
    }
}
