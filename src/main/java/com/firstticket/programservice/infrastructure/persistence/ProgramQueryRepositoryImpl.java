package com.firstticket.programservice.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Repository;

import com.firstticket.programservice.domain.QProgram;
import com.firstticket.programservice.domain.QSchedule;
import com.firstticket.programservice.domain.query.ProgramQueryRepository;
import com.firstticket.programservice.domain.query.ProgramSearchSpec;
import com.firstticket.programservice.domain.query.ProgramSummaryData;
import com.firstticket.programservice.domain.query.QProgramSummaryData;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
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
    public Page<ProgramSummaryData> findBySpec(ProgramSearchSpec spec) {

        List<ProgramSummaryData> content = queryFactory
            .select(new QProgramSummaryData(
                program.id,
                program.title,
                program.category,
                program.theme,
                program.type,
                program.status,
                program.posterUrl,
                schedule.saleStartAt.min()  // 가장 빠른 판매 시작일시
            ))
            .from(program)
            .leftJoin(program.schedules, schedule)
            .where(
                deletedAtIsNull(),
                categoryEq(spec.category()),
                keywordContains(spec.keyword())
            )
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

        Long total = queryFactory
            .select(program.count())
            .from(program)
            .where(
                deletedAtIsNull(),
                categoryEq(spec.category()),
                keywordContains(spec.keyword())
            )
            .fetchOne();

        return new PageImpl<>(content, spec.pageable(), total != null ? total : 0L);
    }

    // --- 정렬 — 화이트리스트 방식 (CVE-2024-49203 대응) ---------

    /**
     * 허용된 필드만 switch 케이스로 정의.
     * 알 수 없는 값은 최신순(default)으로 fallback.
     * 새 정렬 필드 추가 시 반드시 이 메서드에 케이스를 추가해야 한다.
     */
    private OrderSpecifier<?> toOrderSpecifier(String sortField, String direction) {
        boolean isAsc = "asc".equalsIgnoreCase(direction);
        return switch (sortField) {
            case "title" -> isAsc ? program.title.asc() : program.title.desc();
            case "category" -> isAsc ? program.category.asc() : program.category.desc();
            case "saleEndAt" -> isAsc ? schedule.saleEndAt.asc() : schedule.saleEndAt.desc();
            case "createdAt" -> isAsc ? program.createdAt.asc() : program.createdAt.desc();
            default -> program.createdAt.desc();
        };
    }

    // ---- where 조건 헬퍼 ---------------------------------

    private BooleanExpression deletedAtIsNull() {
        return program.deletedAt.isNull();
    }

    private BooleanExpression categoryEq(String category) {
        return category != null ? program.category.eq(category) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return keyword != null ? program.title.containsIgnoreCase(keyword) : null;
    }
}
