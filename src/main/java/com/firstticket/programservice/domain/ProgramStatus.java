package com.firstticket.programservice.domain;

import java.util.Map;
import java.util.Set;

import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;

/**
 * 공연 상태(ProgramStatus) 관리 열거형
 * 공연의 생명주기를 정의하며, 상태 간 전이 가능 여부를 검증하는 로직을 포함합니다.
 */
public enum ProgramStatus {
    /** 초안: 정보 입력 중인 상태 (수정 자유로움) */
    DRAFT,
    /** 판매 중: 예매가 가능한 상태 (수정 제한됨) */
    ON_SALE,
    /** 매진: 모든 회차의 좌석이 소진된 상태 */
    SOLD_OUT,
    /** 취소: 공연 자체가 취소된 상태 (복구 불가) */
    CANCELLED,
    /** 종료: 공연 일정이 모두 마무리된 상태 */
    CLOSED;

    /**
     * 상태 전이 규칙(State Transition Matrix)
     * 키(Key)는 현재 상태, 값(Set)은 전이 가능한 다음 상태들입니다.
     * DRAFT     → ON_SALE, CANCELLED
     * ON_SALE   → SOLD_OUT, CANCELLED, CLOSED
     * SOLD_OUT  → CLOSED, CANCELLED
     * CANCELLED → (불가)
     * CLOSED    → (불가)
     */
    private static final Map<ProgramStatus, Set<ProgramStatus>> ALLOWED = Map.of(
        DRAFT, Set.of(ON_SALE, CANCELLED),
        ON_SALE, Set.of(SOLD_OUT, CANCELLED, CLOSED),
        SOLD_OUT, Set.of(CLOSED, CANCELLED),
        CANCELLED, Set.of(),
        CLOSED, Set.of()
    );

    /**
     * 현재 상태에서 목표 상태로 변경이 가능한지 검증합니다.
     * @param next 변경하고자 하는 목표 상태
     * @throws ProgramException 정의되지 않은 상태 전환을 시도할 경우 발생
     */
    public void validateTransition(ProgramStatus next) {
        if (!ALLOWED.get(this).contains(next)) {
            throw new ProgramException(ProgramErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
