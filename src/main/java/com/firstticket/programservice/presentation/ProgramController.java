package com.firstticket.programservice.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.exception.BusinessException;
import com.firstticket.common.response.ApiResponse;
import com.firstticket.common.response.CommonErrorCode;
import com.firstticket.common.web.AuthContext;
import com.firstticket.common.web.UserRole;
import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.application.dto.result.ProgramSummaryResult;
import com.firstticket.programservice.application.service.ProgramCommandService;
import com.firstticket.programservice.application.service.ProgramQueryService;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.query.PagedResult;
import com.firstticket.programservice.presentation.dto.request.AddPriceGradeRequest;
import com.firstticket.programservice.presentation.dto.request.CreateProgramRequest;
import com.firstticket.programservice.presentation.dto.request.CreateScheduleRequest;
import com.firstticket.programservice.presentation.dto.request.SearchProgramRequest;
import com.firstticket.programservice.presentation.dto.request.UpdateProgramRequest;
import com.firstticket.programservice.presentation.dto.request.UpdateScheduleRequest;
import com.firstticket.programservice.presentation.dto.response.PagedResponse;
import com.firstticket.programservice.presentation.dto.response.PriceGradeResponse;
import com.firstticket.programservice.presentation.dto.response.ProgramResponse;
import com.firstticket.programservice.presentation.dto.response.ProgramStatusResponse;
import com.firstticket.programservice.presentation.dto.response.ProgramSummaryResponse;
import com.firstticket.programservice.presentation.dto.response.ScheduleResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 프로그램 도메인 Controller.
 *
 * 인가 처리:
 * Gateway가 주입한 X-User-Id, X-User-Role 헤더를
 * AuthContext로 추출하여 역할 검증을 수행한다.
 * HOST·ADMIN 검증은 Controller 계층에서 처리한다.
 * 소유자(createdBy) 검증은 Application 계층에서 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramCommandService programCommandService;
    private final ProgramQueryService programQueryService;

    // ---- 프로그램 CRUD ----------------------------------------

    /**
     * 프로그램 등록 (POST /api/programs).
     * 권한: ADMIN, HOST
     * 생성 시 status는 항상 DRAFT.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProgramResponse>> createProgram(
        @RequestBody @Valid CreateProgramRequest request) {
        checkHostOrAdmin();
        ProgramResult result = programCommandService.createProgram(
            request.toCommand()
        );
        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_CREATED,
            ProgramResponse.from(result)
        );
    }

    /**
     * 프로그램 목록 조회 (GET /api/programs).
     * 권한: ALL
     * 카테고리·키워드·지역·날짜 필터, 정렬, 페이지네이션 지원 (P-04).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProgramSummaryResponse>>> searchPrograms(
        @ModelAttribute SearchProgramRequest request) {
        PagedResult<ProgramSummaryResult> pagedResult =
            programQueryService.searchPrograms(request.toQuery());

        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_LIST_FOUND,
            PagedResponse.from(pagedResult, ProgramSummaryResponse::from)
        );
    }

    /**
     * 프로그램 상세 조회 (GET /api/programs/{programId}).
     * 권한: ALL
     * 하위 schedules 목록 및 회차별 잔여 좌석 수 포함 (P-05).
     */
    @GetMapping("/{programId}")
    public ResponseEntity<ApiResponse<ProgramResponse>> getProgram(
        @PathVariable UUID programId) {
        ProgramResult result = programQueryService.getProgram(programId);
        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_FOUND,
            ProgramResponse.from(result)
        );
    }

    /**
     * 프로그램 수정 (PATCH /api/programs/{programId}).
     * 권한: ADMIN, HOST
     * 상태별 수정 가능 필드 제한 (P-02):
     * - DRAFT   : 전체 필드 수정 가능
     * - ON_SALE : posterUrl, description만 수정 가능
     * - 그 외   : 422 반환 (도메인에서 처리)
     */
    @PatchMapping("/{programId}")
    public ResponseEntity<ApiResponse<ProgramResponse>> updateProgram(
        @PathVariable UUID programId,
        @RequestBody @Valid UpdateProgramRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        // getProgram() 대신 경량 조회 — SeatProvider 호출 불필요
        ProgramStatus status = programQueryService.getProgramStatus(programId);
        ProgramResult result;

        if (status == ProgramStatus.DRAFT) {
            result = programCommandService.updateProgramDraft(
                requesterId, request.toDraftCommand(programId)
            );
        } else if (status == ProgramStatus.ON_SALE) {
            result = programCommandService.updateProgramOnSale(
                requesterId, request.toOnSaleCommand(programId)
            );
        } else {
            throw new ProgramException(ProgramErrorCode.PROGRAM_NOT_EDITABLE);
        }

        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_UPDATED,
            ProgramResponse.from(result)
        );
    }

    /**
     * 프로그램 삭제 (DELETE /api/programs/{programId}).
     * 권한: ADMIN, HOST
     * DRAFT 상태에서만 삭제 가능.
     * 예매 내역이 있으면 CANCELLED 처리 후 환불 절차 필요 (P-03).
     */
    @DeleteMapping("/{programId}")
    public ResponseEntity<ApiResponse<Void>> deleteProgram(
        @PathVariable UUID programId) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.deleteProgram(requesterId, programId);
        return ApiResponse.success(ProgramSuccessCode.PROGRAM_DELETED);
    }

    // ---- 상태 전이 -----------------------------------------------------

    /**
     * 프로그램 판매 시작 (PATCH /api/programs/{programId}/publish).
     * 권한: ADMIN, HOST
     * DRAFT → ON_SALE. 잘못된 전이 시 422.
     */
    @PatchMapping("/{programId}/publish")
    public ResponseEntity<ApiResponse<ProgramStatusResponse>> publishProgram(
        @PathVariable UUID programId) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.publishProgram(requesterId, programId);
        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_PUBLISHED,
            ProgramStatusResponse.from(programId,
                com.firstticket.programservice.domain.ProgramStatus.ON_SALE.toString())
        );
    }

    /**
     * 프로그램 취소 (PATCH /api/programs/{programId}/cancel).
     * 권한: ADMIN, HOST
     * → CANCELLED. 잘못된 전이 시 422.
     */
    @PatchMapping("/{programId}/cancel")
    public ResponseEntity<ApiResponse<ProgramStatusResponse>> cancelProgram(
        @PathVariable UUID programId) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.cancelProgram(requesterId, programId);
        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_CANCELLED,
            ProgramStatusResponse.from(programId,
                com.firstticket.programservice.domain.ProgramStatus.CANCELLED.toString())
        );
    }

    /**
     * 프로그램 종료 (PATCH /api/programs/{programId}/close).
     * 권한: ADMIN, HOST
     * → CLOSED. 잘못된 전이 시 422.
     */
    @PatchMapping("/{programId}/close")
    public ResponseEntity<ApiResponse<ProgramStatusResponse>> closeProgram(
        @PathVariable UUID programId) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.closeProgram(requesterId, programId);
        return ApiResponse.success(
            ProgramSuccessCode.PROGRAM_CLOSED,
            ProgramStatusResponse.from(programId,
                com.firstticket.programservice.domain.ProgramStatus.CLOSED.toString())
        );
    }

    // ---- 스케줄 -----------------------------------------------------

    /**
     * 스케줄 등록 (POST /api/programs/{programId}/schedules).
     * 권한: ADMIN, HOST
     * V-04 공연장 중복 예약 검증 수행. 중복이면 409.
     */
    @PostMapping("/{programId}/schedules")
    public ResponseEntity<ApiResponse<ProgramResponse>> createSchedule(
        @PathVariable UUID programId,
        @RequestBody @Valid CreateScheduleRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        ProgramResult result = programCommandService.createSchedule(
            requesterId, request.toCommand(programId)
        );
        return ApiResponse.success(
            ProgramSuccessCode.SCHEDULE_CREATED,
            ProgramResponse.from(result)
        );
    }

    /**
     * 스케줄 목록 조회 (GET /api/programs/{programId}/schedules).
     * 권한: ALL
     * 프로그램 상세 조회(getProgram)에 schedules가 포함되므로
     * 별도 목록 조회는 getProgram으로 대체한다.
     */
    @GetMapping("/{programId}/schedules")
    public ResponseEntity<ApiResponse<ProgramResponse>> getSchedules(
        @PathVariable UUID programId) {
        ProgramResult result = programQueryService.getProgramWithoutRemainingCount(programId);
        return ApiResponse.success(
            ProgramSuccessCode.SCHEDULE_LIST_FOUND,
            ProgramResponse.from(result)
        );
    }

    /**
     * 스케줄 상세 조회 (GET /api/programs/{programId}/schedules/{scheduleId}).
     * 권한: ALL
     */
    @GetMapping("/{programId}/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> getSchedule(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId) {
        ProgramResult result = programQueryService.getProgramWithoutRemainingCount(programId);
        ScheduleResponse schedule = result.schedules().stream()
            .filter(s -> s.id().equals(scheduleId))
            .map(s -> s)
            .findFirst()
            .map(ScheduleResponse::from)
            .orElseThrow(() -> new com.firstticket.programservice.domain.exception
                .ProgramException(
                com.firstticket.programservice.domain.exception
                    .ProgramErrorCode.SCHEDULE_NOT_FOUND));
        return ApiResponse.success(ProgramSuccessCode.SCHEDULE_FOUND, schedule);
    }

    /**
     * 스케줄 수정 (PATCH /api/programs/{programId}/schedules/{scheduleId}).
     * 권한: ADMIN, HOST
     */
    @PatchMapping("/{programId}/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ProgramResponse>> updateSchedule(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId,
        @RequestBody @Valid UpdateScheduleRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        ProgramResult result = programCommandService.updateSchedule(
            requesterId, request.toCommand(programId, scheduleId)
        );
        return ApiResponse.success(
            ProgramSuccessCode.SCHEDULE_UPDATED,
            ProgramResponse.from(result)
        );
    }

    /**
     * 스케줄 삭제 (DELETE /api/programs/{programId}/schedules/{scheduleId}).
     * 권한: ADMIN, HOST
     * DRAFT 상태에서만 삭제 가능.
     */
    @DeleteMapping("/{programId}/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.deleteSchedule(requesterId, programId, scheduleId);
        return ApiResponse.success(ProgramSuccessCode.SCHEDULE_DELETED);
    }

    // --- 가격 등급 -----------------------------------------------

    /**
     * 가격 등급 추가 (POST /api/programs/{programId}/schedules/{scheduleId}/price-grades).
     * 권한: ADMIN, HOST
     */
    @PostMapping("/{programId}/schedules/{scheduleId}/price-grades")
    public ResponseEntity<ApiResponse<ProgramResponse>> addPriceGrade(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId,
        @RequestBody @Valid AddPriceGradeRequest request) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        ProgramResult result = programCommandService.addPriceGrade(
            requesterId, request.toCommand(programId, scheduleId)
        );
        return ApiResponse.success(
            ProgramSuccessCode.PRICE_GRADE_CREATED,
            ProgramResponse.from(result)
        );
    }

    /**
     * 가격 등급 목록 조회 (GET /api/programs/{programId}/schedules/{scheduleId}/price-grades).
     * 권한: ALL
     */
    @GetMapping("/{programId}/schedules/{scheduleId}/price-grades")
    public ResponseEntity<ApiResponse<List<PriceGradeResponse>>> getPriceGrades(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId) {
        // getProgram() 대신 경량 조회 — SeatProvider 호출 불필요
        ProgramResult result = programQueryService.getProgramWithoutRemainingCount(programId);
        List<PriceGradeResponse> priceGrades = result.schedules().stream()
            .filter(s -> s.id().equals(scheduleId))
            .findFirst()
            .map(schedule -> schedule.priceGrades().stream()
                .map(PriceGradeResponse::from)
                .toList())
            .orElseThrow(() ->
                new ProgramException(ProgramErrorCode.SCHEDULE_NOT_FOUND));
        return ApiResponse.success(
            ProgramSuccessCode.PRICE_GRADE_LIST_FOUND, priceGrades
        );
    }

    /**
     * 가격 등급 삭제 (DELETE .../price-grades/{gradeLabel}).
     * 권한: ADMIN, HOST
     * 삭제 후 재등록 방식으로 처리한다.
     */
    @DeleteMapping("/{programId}/schedules/{scheduleId}/price-grades/{gradeLabel}")
    public ResponseEntity<ApiResponse<Void>> removePriceGrade(
        @PathVariable UUID programId,
        @PathVariable UUID scheduleId,
        @PathVariable String gradeLabel) {
        checkHostOrAdmin();
        UUID requesterId = AuthContext.getUserId();

        programCommandService.removePriceGrade(
            requesterId, programId, scheduleId, gradeLabel
        );
        return ApiResponse.success(ProgramSuccessCode.PRICE_GRADE_DELETED);
    }

    // ---- 권한 검증 헬퍼 -------------------------------------------

    /**
     * HOST 또는 ADMIN 역할인지 검증한다.
     * Gateway가 주입한 X-User-Role 헤더를 AuthContext로 추출한다.
     */
    private void checkHostOrAdmin() {
        UserRole role = AuthContext.getRole();
        if (role != UserRole.HOST && role != UserRole.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
