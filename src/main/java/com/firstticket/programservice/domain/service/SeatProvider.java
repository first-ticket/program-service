package com.firstticket.programservice.domain.service;

import java.util.List;
import java.util.UUID;

import com.firstticket.programservice.domain.service.dto.ScheduleRemainingData;

/**
 * 좌석 서비스로부터 잔여 좌석 정보를 조회하는 도메인 서비스 인터페이스.
 * 구현체는 infrastructure/provider/SeatProviderImpl에 위치한다.
 * Application 계층이 Feign Client 등 인프라 세부사항에 의존하지 않도록 격리한다.
 */
public interface SeatProvider {

    /**
     * 프로그램의 회차별 잔여 좌석 수 조회 (P-05).
     * 좌석 서비스 장애 시 빈 리스트를 반환한다.
     *
     * @param programId 조회할 프로그램 ID
     * @return 회차별 잔여 좌석 수 목록
     */
    List<ScheduleRemainingData> getRemainingCounts(UUID programId);
}
