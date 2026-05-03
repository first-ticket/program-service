package com.firstticket.programservice.domain.service;

import java.util.UUID;

/**
 * Venue 서비스로부터 공연장·구역 정보를 조회하는 도메인 서비스 인터페이스.
 * 구현체는 infrastructure/provider/VenueProviderImpl에 위치한다.
 * Application 계층이 Feign Client 등 인프라 세부사항에 의존하지 않도록 격리한다.
 */
public interface VenueProvider {

    /** 공연장 존재 여부 확인. 존재하지 않으면 예외를 던진다. */
    void validateVenueExists(UUID venueId);

    /**
     * 구역 정보 일괄 조회.
     * SEATED 스케줄 등록 시 rowCount·colCount 조회에 사용한다.
     */
    // TODO: Kafka 도입 시 추가
    // Map<UUID, SectionInfo> getSectionInfos(List<UUID> sectionIds);

    /** Section의 최대 수용 인원 상한 조회. */
    int getSectionCapacity(UUID sectionId);
}
