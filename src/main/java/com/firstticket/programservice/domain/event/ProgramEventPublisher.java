// domain/event/ProgramEventPublisher.java

package com.firstticket.programservice.domain.event;

import java.util.List;

import com.firstticket.programservice.domain.Program;
import com.firstticket.programservice.domain.Schedule;

/**
 * 프로그램 도메인 이벤트 발행 인터페이스.
 * 구조 참고: domain/event/SampleEvents.java
 *
 * Application 계층이 Kafka·Outbox 등 인프라 세부사항에 의존하지 않도록 격리한다.
 * 구현체: infrastructure/event/ProgramEventPublisherImpl
 *
 * 모든 발행 메서드는 @Transactional 메서드 안에서 호출되어야 한다.
 * Events.publish() → OutboxEventListener → Outbox 저장이 같은 트랜잭션 안에서 처리된다.
 */
public interface ProgramEventPublisher {

    /**
     * 프로그램 생성 이벤트 발행 (토픽: program.created).
     * 대기열 서비스가 수신하여 프로그램 대기열을 초기화한다.
     * createProgram() 에서 save() 이후 호출한다.
     *
     * @param program 저장 완료된 Program (getId() != null 보장)
     */
    void publishProgramCreated(Program program);

    /**
     * 스케줄 생성 이벤트 발행 (토픽: schedule.created).
     * 좌석 서비스가 수신하여 BookingSeat을 생성한다.
     * publishProgram() 에서 모든 PriceGrade 확정 후 호출한다.
     *
     * @param program       소속 프로그램
     * @param schedule      생성된 스케줄
     * @param seatTemplates 구역별 좌석 템플릿 목록 (sectionId, price 포함)
     */
    void publishScheduleCreated(Program program, Schedule schedule,
        List<ScheduleCreatedEventData.SeatTemplate> seatTemplates);

    /**
     * 프로그램 판매 기간 변경 이벤트 발행 (토픽: program.time.updated).
     * 대기열 서비스가 수신하여 openAt·closeAt을 갱신한다.
     * publishProgram() / updateSchedule() 에서 호출한다.
     *
     * @param program  소속 프로그램
     * @param schedule 판매 기간이 변경된 스케줄
     */
    void publishProgramTimeUpdated(Program program, Schedule schedule);

    /**
     * 프로그램 취소 이벤트 발행 (토픽: program.cancelled).
     * 대기열 서비스·예매 서비스가 수신하여 대기열 종료 및 예매 일괄 취소를 처리한다.
     * cancelProgram() 에서 호출한다.
     *
     * @param program 취소된 프로그램
     */
    void publishProgramCancelled(Program program);
}
