package com.firstticket.programservice.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;

import com.firstticket.programservice.domain.QProgram;
import com.firstticket.programservice.domain.QSchedule;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.domain.query.ProgramQueryRepository;
import com.firstticket.programservice.domain.query.ProgramSearchSpec;
import com.firstticket.programservice.domain.query.ProgramSummaryData;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 프로그램 목록 조회 QueryDSL 구현체.
 *
 * CVE-2024-49203 대응:
 * PathBuilder.get(userInput) 패턴 금지.
 * 정렬 필드는 toOrderSpecifier() switch 화이트리스트로만 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class ProgramQueryRepositoryImpl implements ProgramQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QProgram program = QProgram.program;
    private static final QSchedule schedule = QSchedule.schedule;

    @Override
    public PagedResult<ProgramSummaryData> findBySpec(ProgramSearchSpec spec) {

        List<ProgramSummaryData> content = buildContentQuery(spec)
            .select(Projections.constructor(ProgramSummaryData.class,
                program.id,
                program.title,
                program.category,
                program.theme,
                program.type.stringValue(),     // ← enum → String
                program.status.stringValue(),
                program.posterUrl,
                schedule.saleStartAt.min()
            ))
            .groupBy(
                program.id,
                program.title,
                program.category,
                program.theme,
                program.type,
                program.status,
                program.posterUrl
            )
            .orderBy(toOrderSpecifier(spec.sortField(), spec.direction()))
            // Pageable 대신 ProgramSearchSpec 원시값 사용
            .offset(spec.getOffset())
            .limit(spec.pageSize())
            .fetch();

        // schedule 기반 조건(dateFilter)이 있으면 join 포함
        // 없으면 join 제외하여 불필요한 비용 방지
        Long total = hasScheduleFilter(spec)
            ? buildContentQuery(spec).select(program.countDistinct()).fetchOne()
            : buildCountQuery(spec).select(program.countDistinct()).fetchOne();

        return PagedResult.of(
            content,
            total != null ? total : 0L,
            spec.pageNumber(),
            spec.pageSize()
        );
    }

    // ── 공통 베이스 쿼리 ──────────────────────────────────────────────

    /** schedule join 포함 — dateFilter 등 schedule 기반 조건이 있을 때 사용 */
    private JPAQuery<?> buildContentQuery(ProgramSearchSpec spec) {
        return queryFactory
            .from(program)
            .leftJoin(program.schedules, schedule)
            .where(
                deletedAtIsNull(),
                categoryEq(spec.category()),
                keywordContains(spec.keyword()),
                regionEq(spec.region()),
                dateFilter(spec.date())
            );
    }

    /** schedule join 제외 — schedule 기반 조건이 없을 때 count 쿼리에 사용 */
    private JPAQuery<?> buildCountQuery(ProgramSearchSpec spec) {
        return queryFactory
            .from(program)
            .where(
                deletedAtIsNull(),
                categoryEq(spec.category()),
                keywordContains(spec.keyword()),
                regionEq(spec.region())
                // dateFilter 제외 — schedule join 불필요
            );
    }

    // ── 정렬 — 화이트리스트 방식 (CVE-2024-49203 대응) ───────────────

    /**
     * 허용된 필드만 switch 케이스로 정의.
     *
     * sortField null 처리:
     * ProgramSearchSpec.sortField는 nullable이므로
     * null 입력 시 switch NullPointerException 방지를 위해
     * null을 "createdAt"으로 정규화한다.
     *
     * saleEndAt 정렬:
     * Program이 여러 Schedule을 가지므로 단순 schedule.saleEndAt 정렬 시
     * GROUP BY 미포함으로 비결정적 결과가 발생한다.
     * min(saleEndAt) 집계로 결정적 정렬을 보장한다.
     */
    private OrderSpecifier<?> toOrderSpecifier(String sortField, String direction) {
        // direction 검증
        if (direction == null
            || (!direction.equalsIgnoreCase("asc")
            && !direction.equalsIgnoreCase("desc"))) {
            return program.createdAt.desc();
        }

        // null 정규화 — switch에 null이 들어오면 NPE 발생
        String field = Objects.toString(sortField, "createdAt");
        boolean isAsc = "asc".equalsIgnoreCase(direction);

        return switch (field) {
            case "title" -> isAsc ? program.title.asc() : program.title.desc();
            case "category" -> isAsc ? program.category.asc() : program.category.desc();
            // min(saleEndAt): Program당 가장 빠른 판매 종료일 기준 결정적 정렬
            case "saleEndAt" -> isAsc ? schedule.saleEndAt.min().asc() : schedule.saleEndAt.min().desc();
            case "createdAt" -> isAsc ? program.createdAt.asc() : program.createdAt.desc();
            default -> program.createdAt.desc();
        };
    }

    // ── where 조건 헬퍼 ────────────────────────────────────────────────

    private BooleanExpression deletedAtIsNull() {
        return program.deletedAt.isNull();
    }

    private BooleanExpression categoryEq(String category) {
        return category != null ? program.category.eq(category) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null)
            return null;
        String trimmed = keyword.trim();
        if (trimmed.isBlank())
            return null;
        return program.title.containsIgnoreCase(trimmed);
    }

    /**
     * 지역 필터 (P-04).
     * Program.region 필드 기준 — Schedule 등록 시 Venue.address에서 역정규화된 값.
     *
     */
    private BooleanExpression regionEq(String region) {
        return region != null ? program.region.eq(region) : null;
    }

    /**
     * 날짜 필터 (P-04).
     * 반열린 구간 [startOfDay, nextDayStart)으로 처리한다.
     *
     * 수정 이유:
     * date.atTime(23, 59, 59) 사용 시 23:59:59.001 같은
     * 밀리초·나노초 단위 타임스탬프가 필터에서 누락된다.
     * nextDayStart를 exclusive 상한으로 사용하면 하루 전체를 정확히 커버한다.
     */
    private BooleanExpression dateFilter(LocalDate date) {
        if (date == null)
            return null;
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();
        return schedule.eventStartAt.goe(startOfDay)
            .and(schedule.eventStartAt.lt(nextDayStart));
    }

    /**
     * schedule join이 필요한 조건이 있는지 확인.
     * 현재는 dateFilter만 schedule 기반이지만,
     * 추후 schedule 기반 조건이 추가되면 이 메서드만 수정하면 된다.
     */
    private boolean hasScheduleFilter(ProgramSearchSpec spec) {
        return spec.date() != null;
    }
}
