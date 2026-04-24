package com.firstticket.programservice.domain;

import java.util.Objects;
import java.util.UUID;

import com.firstticket.common.persistence.BaseEntity;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PriceGrade는 도메인 모델 상 Value Object로,
 * gradeLabel 기준 개별 삭제 쿼리가 필요하여 @Entity로 구현해
 * 특정 공연 회차(Schedule) 내의 좌석 등급별 가격 정보를 관리합니다.
 * - 'VIP석 - 150,000원'과 같이 등급 레이블과 가격을 매핑합니다.
 * - PriceGradeRepository를 별도로 두지 않습니다.
 * - id는 DB 매핑용 기술적 PK이며 도메인 식별자가 아닙니다.
 * - 동등성은 (scheduleId, gradeLabel) 값 조합으로 판단합니다.
 * - 수정 없음: 삭제 후 재등록 방식으로 처리합니다.
 */
@Entity
@Table(name = "price_grade",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_price_grade_schedule_label",
        columnNames = {"schedule_id", "grade_label"}
    )
)
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceGrade extends BaseEntity {
    // 생성자 정보 불필요 → BaseEntity (시간만)

    /** DB 매핑용 기술적 PK — 도메인 식별자 아님 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;  // DB 매핑용 기술적 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    /**
     * SEATED/STANDING: sectionId 필수 (구역별 등급 설정)
     * FREE: sectionId null 허용 (가격 구분 없음)
     */
    @Column(columnDefinition = "uuid")
    private UUID sectionId;

    /** 등급 명칭 (예: VIP, R, S, 일반석 등) */
    @Column(nullable = false, length = 50)
    private String gradeLabel;

    /** 한 스케줄 내 해당 등급의 티켓 가격 */
    @Column(nullable = false)
    private int price;

    /**
     * Package-private: Schedule.addPriceGrade()를 통해서만 생성됩니다.
     * @param price 0원 이상의 가격만 허용 (무료 공연 가능)
     * @throws ProgramException 가격이 음수일 경우 예외 발생
     */
    static PriceGrade create(Schedule schedule, UUID sectionId,
        String gradeLabel, int price) {
        if (price < 0) {
            throw new ProgramException(ProgramErrorCode.INVALID_PRICE);
        }
        return new PriceGrade(null, schedule, sectionId, gradeLabel, price);
    }

    /**
     * id가 아닌 (scheduleId, gradeLabel) 값 기준으로 VO의 동등성을 판단합니다.
     * 동일한 회차 내에서 등급 레이블이 같다면 동일한 객체로 간주합니다.
     * */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PriceGrade other))
            return false;
        return gradeLabel.equals(other.gradeLabel)
            && schedule.getId().equals(other.schedule.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(schedule.getId(), gradeLabel);
    }
}
