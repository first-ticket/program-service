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

        List<ProgramSummaryData> content = buildBaseQuery(spec)
            .select(Projections.constructor(ProgramSummaryData.class,
                program.id,
                program.title,
                program.category,
                program.theme,
                program.type,
                program.status,
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
            .offset(spec.pageable().getOffset())
            .limit(spec.pageable().getPageSize())
            .fetch();

        // count 쿼리도 동일한 join·where 조건 사용
        // content 쿼리와 join/where가 일치해야 페이지 정보가 정확하다
        Long total = buildBaseQuery(spec)
            .select(program.countDistinct())
            .fetchOne();

        return PagedResult.of(
            content,
            total != null ? total : 0L,
            spec.pageable().getPageNumber(),
            spec.pageable().getPageSize()
        );
    }

    // ── 공통 베이스 쿼리 ──────────────────────────────────────────────

    /**
     * content 쿼리와 count 쿼리의 join·where 조건을 공통으로 관리한다.
     * 두 쿼리 간 조건 불일치로 인한 페이지 정보 오류를 방지한다.
     */
    private JPAQuery<?> buildBaseQuery(ProgramSearchSpec spec) {
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
        return keyword != null ? program.title.containsIgnoreCase(keyword) : null;
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
     * 요청한 날짜에 eventStartAt이 포함된 스케줄이 있는 프로그램을 반환한다.
     * 날짜 범위: date 00:00:00 ~ date 23:59:59
     */
    private BooleanExpression dateFilter(LocalDate date) {
        if (date == null)
            return null;
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);
        return schedule.eventStartAt.between(startOfDay, endOfDay);
    }
}
