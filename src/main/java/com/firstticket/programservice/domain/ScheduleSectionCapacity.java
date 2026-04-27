// ScheduleSectionCapacity.java — @Entity 제거, VO로 처리
package com.firstticket.programservice.domain;

import java.util.UUID;

import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스케줄별 구역 인원 정보 — Value Object
 *
 * STANDING·FREE 프로그램 등록 시 구역별 허용 인원을 지정
 * - 개별 식별자 불필요 → @Embeddable로 관리
 * - 수정 없음: 삭제 후 재등록 방식으로 처리
 * - SEATED는 VenueSeat 기반이므로 이 VO가 필요하지 않음
 *
 * DB 매핑:
 * - @ElementCollection으로 별도 테이블(schedule_section_capacity)에 저장
 * - gradeLabel 기준 삭제가 필요한 PriceGrade와 달리
 *   sectionId 기준 삭제가 가능하므로 @ElementCollection으로 충분함
 */
@Embeddable
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSectionCapacity {

    /** 공연장 서비스의 Section ID 참조 (값만 보관, MSA 경계 유지) */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID sectionId;

    /**
     * 이 회차에서 해당 구역에 허용하는 인원.
     * 공연장 Section.capacity를 초과할 수 없음 (Application 계층에서 검증).
     */
    @Column(nullable = false)
    private int capacity;

    // ------- 정적 팩토리 메서드 ---------------------------------

    /**
     * Package-private: Schedule.addSectionCapacity()를 통해서만 생성
     * (직접 호출 시 프로그램 타입 검증(SEATED 차단)이 우회될 수 있음)
     */
    static ScheduleSectionCapacity of(UUID sectionId, int capacity) {
        // sectionId, capacity 검증
        validateSectionCapacityInfo(sectionId, capacity);

        return new ScheduleSectionCapacity(sectionId, capacity);
    }

    // ----- 검증 메서드 ---------------------------------------------

    private static void validateSectionCapacityInfo(UUID sectionId, int capacity) {
        if (sectionId == null) {
            throw new ProgramException(ProgramErrorCode.INVALID_SECTION_ID);
        }
        if (capacity <= 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_CAPACITY);
        }
    }

    // ----- VO 동등성 비교 ------------------------------------------

    /**
     * sectionId 기준으로 동등성을 판단한다.
     * 한 스케줄 내 동일 구역은 하나만 존재해야 하므로
     * capacity가 달라도 같은 구역이면 동일 VO로 간주
     * */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ScheduleSectionCapacity other))
            return false;
        return sectionId.equals(other.sectionId);
    }

    @Override
    public int hashCode() {
        return sectionId.hashCode();
    }
}
