package com.firstticket.programservice.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.firstticket.common.exception.GlobalExceptionHandler;
import com.firstticket.programservice.application.dto.result.PriceGradeResult;
import com.firstticket.programservice.application.dto.result.ProgramResult;
import com.firstticket.programservice.application.dto.result.ProgramSummaryResult;
import com.firstticket.programservice.application.dto.result.ScheduleResult;
import com.firstticket.programservice.application.service.ProgramCommandService;
import com.firstticket.programservice.application.service.ProgramQueryService;
import com.firstticket.programservice.domain.ProgramStatus;
import com.firstticket.programservice.domain.ProgramType;
import com.firstticket.programservice.domain.exception.ProgramErrorCode;
import com.firstticket.programservice.domain.exception.ProgramException;
import com.firstticket.programservice.domain.query.PagedResult;

/**
 * ProgramController 슬라이스 테스트.
 *
 * [테스트 규칙]
 * - @WebMvcTest 슬라이스 테스트 (@SpringBootTest 미사용)
 * - @MockitoBean 사용
 * - REST Docs 스니펫 생성 필수 (표현 계층 규칙)
 * - 독립 실행 가능
 *
 * [AuthContext 처리 전략] — README 기준
 * AuthContext는 HttpServletRequest에서 X-User-Id, X-User-Role 헤더를 직접 읽는다.
 * - X-User-Id  누락 → 401 UNAUTHORIZED
 * - X-User-Role 누락 → 401 UNAUTHORIZED
 * - X-User-Role 값이 UserRole enum에 없는 값 → 401 UNAUTHORIZED
 * - X-User-Role = "CUSTOMER" → UserRole.CUSTOMER 파싱 성공 → checkHostOrAdmin()이 403 던짐
 * 따라서 403 케이스는 X-User-Id 헤더도 반드시 함께 전달해야 한다.
 *
 * [응답 구조] — common ApiResponse
 * 성공: { "success": true,  "code": "...", "message": "...", "timestamp": "...", "data": {...} }
 * 실패: { "success": false, "code": "...", "message": "...", "timestamp": "..." }
 *
 * [주요 반환 타입]
 * - createProgram / getProgram / updateProgram / createSchedule / updateSchedule : ProgramResponse
 * - getSchedules                                                                  : ProgramResponse (schedules 포함)
 * - getSchedule                                                                   : ScheduleResponse
 * - publish / cancel / close                                                      : ProgramStatusResponse { id, status }
 * - addPriceGrade                                                                 : ProgramResponse
 * - getPriceGrades                                                                : List<PriceGradeResponse>
 * - deleteProgram / deleteSchedule / removePriceGrade                            : ApiResponse<Void> (HTTP 200)
 */
@ExtendWith({RestDocumentationExtension.class, SpringExtension.class})
@WebMvcTest({ProgramController.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
    })
class ProgramControllerTest {

    private MockMvc mockMvc;

    @MockitoBean
    private ProgramCommandService programCommandService;

    @MockitoBean
    private ProgramQueryService programQueryService;

    // ── 공통 픽스처 UUID ─────────────────────────────────────────────
    private static final UUID PROGRAM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID VENUE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID SECTION_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    // @FutureOrPresent 검증 통과용 — 충분히 먼 미래
    private static final LocalDateTime FUTURE = LocalDateTime.of(2027, 6, 1, 14, 0);

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDoc) {
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(new ProgramController(programCommandService, programQueryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .apply(documentationConfiguration(restDoc))
            .build();
    }

    // ── 픽스처 빌더 ──────────────────────────────────────────────────

    private PriceGradeResult priceGradeResult() {
        return new PriceGradeResult(SECTION_ID, "VIP", 50_000);
    }

    private ScheduleResult scheduleResult() {
        return new ScheduleResult(
            SCHEDULE_ID, VENUE_ID,
            FUTURE, FUTURE.plusHours(2),
            FUTURE.minusDays(30), FUTURE.minusDays(1),
            500, 320,
            List.of(priceGradeResult())
        );
    }

    private ProgramResult programResult() {
        return new ProgramResult(
            PROGRAM_ID, "테스트 공연", "CONCERT", "POP",
            ProgramType.SEATED, ProgramStatus.DRAFT,
            "서울", "https://cdn.example.com/poster.jpg", "공연 설명",
            List.of(scheduleResult())
        );
    }

    private ProgramSummaryResult programSummaryResult() {
        return new ProgramSummaryResult(
            PROGRAM_ID, "테스트 공연", "CONCERT", "POP",
            "SEATED", "DRAFT",
            "https://cdn.example.com/poster.jpg",
            FUTURE.minusDays(30)
        );
    }

    // ── 공통 REST Docs 응답 필드 (ApiResponse wrapper) ───────────────

    private static FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
        var base = List.of(
            fieldWithPath("success").description("성공 여부"),
            fieldWithPath("code").description("응답 코드"),
            fieldWithPath("message").description("응답 메시지"),
            fieldWithPath("timestamp").description("응답 시각")
        );

        var all = new java.util.ArrayList<>(base);

        if (dataFields.length > 0) {
            all.addAll(List.of(dataFields));
        } else {
            // 인자가 없는 에러 케이스(400, 403 등):
            // 혹시 모를 'data' 키 포함 여부를 방어하기 위해 optional로 선언
            all.add(fieldWithPath("data").type(JsonFieldType.OBJECT)
                .description("응답 데이터 (에러 발생 시 null 혹은 미포함)")
                .optional());
        }

        return all.toArray(new FieldDescriptor[0]);
    }

    /** ProgramResponse 전체 data 필드 — 성공 응답에 공통으로 사용 */
    private static FieldDescriptor[] programResponseDataFields() {
        return new FieldDescriptor[] {
            fieldWithPath("data.id").description("프로그램 UUID"),
            fieldWithPath("data.title").description("제목"),
            fieldWithPath("data.category").description("카테고리"),
            fieldWithPath("data.theme").description("테마"),
            fieldWithPath("data.type").description("타입"),
            fieldWithPath("data.status").description("상태"),
            fieldWithPath("data.region").description("지역"),
            fieldWithPath("data.posterUrl").type(JsonFieldType.STRING).optional().description("포스터 URL"),
            fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("설명"),
            fieldWithPath("data.schedules[].id").description("스케줄 UUID"),
            fieldWithPath("data.schedules[].venueId").description("공연장 UUID"),
            fieldWithPath("data.schedules[].eventStartAt").description("공연 시작"),
            fieldWithPath("data.schedules[].eventEndAt").description("공연 종료"),
            fieldWithPath("data.schedules[].saleStartAt").description("판매 시작"),
            fieldWithPath("data.schedules[].saleEndAt").description("판매 종료"),
            fieldWithPath("data.schedules[].totalCapacity").description("총 수용 인원"),
            fieldWithPath("data.schedules[].remainingCount").description("잔여 좌석"),
            fieldWithPath("data.schedules[].priceGrades[].sectionId").description("구역 UUID"),
            fieldWithPath("data.schedules[].priceGrades[].gradeLabel").description("등급명"),
            fieldWithPath("data.schedules[].priceGrades[].price").description("가격")
        };
    }

    // ══════════════════════════════════════════════════════════════════
    // 프로그램 CRUD
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/programs — 프로그램 등록")
    class CreateProgram {

        @Test
        @DisplayName("프로그램 등록 성공 — 초기 상태 DRAFT")
        void success() throws Exception {
            given(programCommandService.createProgram(any())).willReturn(programResult());

            mockMvc.perform(post("/api/v1/programs")
                    .header("X-User-Id", USER_ID)
                    .header("X-User-Role", "HOST")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "테스트 공연",
                          "category": "CONCERT",
                          "theme": "POP",
                          "type": "SEATED",
                          "region": "서울"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andDo(document("program/create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("Gateway가 주입하는 사용자 UUID"),
                        headerWithName("X-User-Role").description("사용자 역할 (HOST | ADMIN)")
                    ),
                    requestFields(
                        fieldWithPath("title").description("프로그램 제목"),
                        fieldWithPath("category").description("카테고리"),
                        fieldWithPath("theme").description("테마"),
                        fieldWithPath("type").description("SEATED | STANDING | FREE"),
                        fieldWithPath("region").description("지역"),
                        fieldWithPath("posterUrl").type(JsonFieldType.STRING).optional().description("포스터 URL"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("설명")
                    ),
                    responseFields(apiResponseFields(programResponseDataFields()))
                ));
        }

        @Test
        @DisplayName("제목 누락 시 400 — 입력값 유효성 오류")
        void fail_missingTitle() throws Exception {
            mockMvc.perform(
                    post("/api/v1/programs")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "category": "CONCERT", "theme": "POP", "type": "SEATED", "region": "서울" }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("program/create-400",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(apiResponseFields())
                    ));
        }

        @Test
        @DisplayName("CUSTOMER 역할로 요청 시 403 — 권한 오류")
        void fail_customerRole() throws Exception {
            // X-User-Id 누락 시 AuthContext가 401을 먼저 던지므로 반드시 함께 전달
            // X-User-Role = "CUSTOMER" → UserRole.CUSTOMER 파싱 → checkHostOrAdmin()이 403

            mockMvc.perform(
                    post("/api/v1/programs")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "title": "공연", "category": "CONCERT",
                              "theme": "POP", "type": "SEATED", "region": "서울" }
                            """))
                .andExpect(status().isForbidden())
                .andDo(
                    document("program/create-403",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(apiResponseFields())
                    ));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/programs — 프로그램 목록 조회")
    class SearchPrograms {

        @Test
        @DisplayName("카테고리·키워드·페이지네이션으로 목록 조회 성공")
        void success() throws Exception {
            PagedResult<ProgramSummaryResult> paged =
                new PagedResult<>(List.of(programSummaryResult()), 1L, 1, 0, 20);
            given(programQueryService.searchPrograms(any())).willReturn(paged);

            mockMvc.perform(
                    get("/api/v1/programs")
                        .param("category", "CONCERT")
                        .param("keyword", "테스트")
                        .param("region", "서울")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andDo(document("program/search",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    queryParameters(
                        parameterWithName("category").optional().description("카테고리 필터"),
                        parameterWithName("keyword").optional().description("제목 키워드"),
                        parameterWithName("region").optional().description("지역 필터"),
                        parameterWithName("date").optional().description("날짜 필터 yyyy-MM-dd"),
                        parameterWithName("sort").optional().description("정렬 기준"),
                        parameterWithName("direction").optional().description("asc | desc"),
                        parameterWithName("page").description("페이지 번호 (0부터)"),
                        parameterWithName("size").description("페이지 크기")
                    ),
                    responseFields(
                        apiResponseFields(
                            // ProgramSummaryResponse: id, title, category, type, status, posterUrl, saleStartAt
                            // (theme 없음 — ProgramSummaryResponse에 theme 필드 없음)
                            fieldWithPath("data.content[].id").description("프로그램 UUID"),
                            fieldWithPath("data.content[].title").description("제목"),
                            fieldWithPath("data.content[].category").description("카테고리"),
                            fieldWithPath("data.content[].type").description("타입"),
                            fieldWithPath("data.content[].status").description("상태"),
                            fieldWithPath("data.content[].posterUrl").type(JsonFieldType.STRING)
                                .optional()
                                .description("포스터 URL"),
                            fieldWithPath("data.content[].saleStartAt").description("판매 시작"),
                            fieldWithPath("data.totalElements").description("전체 요소 수"),
                            fieldWithPath("data.totalPages").description("전체 페이지 수"),
                            fieldWithPath("data.size").description("페이지 크기"),
                            fieldWithPath("data.number").description("현재 페이지 번호")
                        ))
                ));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/programs/{programId} — 프로그램 상세 조회")
    class GetProgram {

        @Test
        @DisplayName("상세 조회 성공 — 스케줄 및 잔여 좌석 포함")
        void success() throws Exception {
            given(programQueryService.getProgram(PROGRAM_ID)).willReturn(programResult());

            mockMvc.perform(
                    get("/api/v1/programs/{programId}", PROGRAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.schedules[0].remainingCount").value(320))
                .andDo(
                    document("program/get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("조회할 프로그램 UUID")),
                        responseFields(apiResponseFields(programResponseDataFields()))
                    ));
        }

        @Test
        @DisplayName("존재하지 않는 프로그램 조회 시 404 — 외부 의존성 실패")
        void fail_notFound() throws Exception {
            given(programQueryService.getProgram(PROGRAM_ID))
                .willThrow(new ProgramException(ProgramErrorCode.PROGRAM_NOT_FOUND));

            mockMvc.perform(
                    get("/api/v1/programs/{programId}", PROGRAM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(document("program/get-404",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID")),
                    responseFields(apiResponseFields())
                ));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/programs/{programId} — 프로그램 수정")
    class UpdateProgram {

        @Test
        @DisplayName("DRAFT 상태에서 전체 필드 수정 성공")
        void success_draft() throws Exception {
            given(programQueryService.getProgramStatus(PROGRAM_ID))
                .willReturn(ProgramStatus.DRAFT);

            given(programCommandService.updateProgramDraft(any(), any()))
                .willReturn(programResult());

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "수정된 제목",
                              "category": "MUSICAL",
                              "theme": "CLASSIC",
                              "description": "수정된 설명"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andDo(
                    document("program/update-draft",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("HOST | ADMIN")
                        ),
                        pathParameters(
                            parameterWithName("programId").description("수정할 프로그램 UUID")),
                        requestFields(
                            fieldWithPath("title").type(JsonFieldType.STRING)
                                .optional()
                                .description("제목 (null → 기존 유지)"),
                            fieldWithPath("category").type(JsonFieldType.STRING)
                                .optional()
                                .description("카테고리 (DRAFT 전용)"),
                            fieldWithPath("theme").type(JsonFieldType.STRING).optional().description("테마 (DRAFT 전용)"),
                            fieldWithPath("posterUrl").type(JsonFieldType.STRING).optional().description("포스터 URL"),
                            fieldWithPath("description").type(JsonFieldType.STRING).optional().description("설명")
                        ),
                        responseFields(apiResponseFields(programResponseDataFields()))
                    ));
        }

        @Test
        @DisplayName("ON_SALE 상태에서 title 수정 시도 시 422 — 도메인 규칙 위반")
        void fail_onSale_titleNotModifiable() throws Exception {
            // 컨트롤러가 status 조회 후 toOnSaleCommand() 호출 → title 있으면 즉시 ProgramException
            given(programQueryService.getProgramStatus(PROGRAM_ID))
                .willReturn(ProgramStatus.ON_SALE);

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "title": "수정 불가 제목" }
                            """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("program/update-422-on-sale",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(apiResponseFields())
                    ));
        }

        @Test
        @DisplayName("CANCELLED 상태 프로그램 수정 시도 시 422 — 도메인 규칙 위반")
        void fail_cancelled_notEditable() throws Exception {
            // 컨트롤러: DRAFT·ON_SALE 이외 상태 → 즉시 PROGRAM_NOT_EDITABLE
            given(programQueryService.getProgramStatus(PROGRAM_ID))
                .willReturn(ProgramStatus.CANCELLED);

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "description": "수정 시도" }
                            """))
                .andExpect(status().isUnprocessableEntity())
                .andDo(
                    document("program/update-422-cancelled",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(apiResponseFields())
                    ));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/programs/{programId} — 프로그램 삭제")
    class DeleteProgram {

        @Test
        @DisplayName("DRAFT 상태 프로그램 삭제 성공 — HTTP 200 + ApiResponse<Void>")
        void success() throws Exception {
            willDoNothing().given(programCommandService)
                .deleteProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    delete("/api/v1/programs/{programId}", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(
                    document("program/delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("HOST | ADMIN")
                        ),
                        pathParameters(parameterWithName("programId").description("삭제할 프로그램 UUID"))
                    ));
        }

        @Test
        @DisplayName("DRAFT 아닌 상태 삭제 시도 시 422 — 도메인 규칙 위반")
        void fail_notDraft() throws Exception {
            willThrow(new ProgramException(ProgramErrorCode.PROGRAM_NOT_DELETABLE))
                .given(programCommandService)
                .deleteProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    delete("/api/v1/programs/{programId}", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("program/delete-422",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(apiResponseFields())
                    ));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 상태 전이
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /api/v1/programs/{programId}/publish — 판매 시작")
    class PublishProgram {

        @Test
        @DisplayName("DRAFT → ON_SALE 전이 성공 — ProgramStatusResponse 반환")
        void success() throws Exception {
            willDoNothing().given(programCommandService)
                .publishProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}/publish", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andDo(
                    document("program/publish",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("HOST | ADMIN")
                        ),
                        pathParameters(
                            parameterWithName("programId").description("판매 시작할 프로그램 UUID")),
                        responseFields(
                            apiResponseFields(
                                fieldWithPath("data.id").description("프로그램 UUID"),
                                fieldWithPath("data.status").description("변경된 상태 (ON_SALE)")
                            ))
                    ));
        }

        @Test
        @DisplayName("잘못된 상태 전이 시 422 — 도메인 규칙 위반")
        void fail_invalidTransition() throws Exception {
            willThrow(new ProgramException(ProgramErrorCode.INVALID_STATUS_TRANSITION))
                .given(programCommandService).
                publishProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}/publish", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("program/publish-422",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(apiResponseFields())
                    ));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/programs/{programId}/cancel — 프로그램 취소")
    class CancelProgram {

        @Test
        @DisplayName("→ CANCELLED 전이 성공")
        void success() throws Exception {
            willDoNothing().given(programCommandService)
                .cancelProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}/cancel", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andDo(
                    document("program/cancel",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("취소할 프로그램 UUID")),
                        responseFields(
                            apiResponseFields(
                                fieldWithPath("data.id").description("프로그램 UUID"),
                                fieldWithPath("data.status").description("변경된 상태 (CANCELLED)")
                            ))
                    ));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/programs/{programId}/close — 프로그램 종료")
    class CloseProgram {

        @Test
        @DisplayName("→ CLOSED 전이 성공")
        void success() throws Exception {
            willDoNothing().given(programCommandService).closeProgram(any(), eq(PROGRAM_ID));

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}/close", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andDo(document("program/close",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("종료할 프로그램 UUID")),
                    responseFields(
                        apiResponseFields(
                            fieldWithPath("data.id").description("프로그램 UUID"),
                            fieldWithPath("data.status").description("변경된 상태 (CLOSED)")
                        ))
                ));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 스케줄 (Schedule)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/programs/{programId}/schedules — 스케줄 등록")
    class CreateSchedule {

        @Test
        @DisplayName("스케줄 등록 성공 — ProgramResponse 반환")
        void success() throws Exception {
            given(programCommandService.createSchedule(any(), any()))
                .willReturn(programResult());

            mockMvc.perform(
                    post("/api/v1/programs/{programId}/schedules", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "venueId": "%s",
                              "eventStartAt": "2027-06-01T14:00:00",
                              "eventEndAt":   "2027-06-01T17:00:00",
                              "saleStartAt":  "2027-05-01T10:00:00",
                              "saleEndAt":    "2027-05-31T23:59:59"
                            }
                            """.formatted(VENUE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(PROGRAM_ID.toString()))
                .andDo(
                    document("program/schedule/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                            headerWithName("X-User-Id").description("사용자 UUID"),
                            headerWithName("X-User-Role").description("HOST | ADMIN")
                        ),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        requestFields(
                            fieldWithPath("venueId").description("공연장 UUID"),
                            fieldWithPath("eventStartAt").description("공연 시작 일시 (현재 이후)"),
                            fieldWithPath("eventEndAt").description("공연 종료 일시 (현재 이후)"),
                            fieldWithPath("saleStartAt").description("판매 시작 일시 (현재 이후)"),
                            fieldWithPath("saleEndAt").description("판매 종료 일시 (현재 이후)")
                        )
                    ));
        }

        @Test
        @DisplayName("venueId 누락 시 400 — 입력값 유효성 오류")
        void fail_missingVenueId() throws Exception {
            mockMvc.perform(
                    post("/api/v1/programs/{programId}/schedules", PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "eventStartAt": "2027-06-01T14:00:00",
                              "eventEndAt":   "2027-06-01T17:00:00",
                              "saleStartAt":  "2027-05-01T10:00:00",
                              "saleEndAt":    "2027-05-31T23:59:59"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andDo(
                    document("program/schedule/create-400",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID"))
                    ));
        }

        @Test
        @DisplayName("공연장 중복 예약 시 409 — 도메인 규칙 위반 (V-04)")
        void fail_venueConflict() throws Exception {
            willThrow(new ProgramException(ProgramErrorCode.VENUE_TIME_CONFLICT))
                .given(programCommandService)
                .createSchedule(any(), any());

            mockMvc.perform(
                    post("/api/v1/programs/{programId}/schedules",
                        PROGRAM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "venueId": "%s",
                              "eventStartAt": "2027-06-01T14:00:00",
                              "eventEndAt":   "2027-06-01T17:00:00",
                              "saleStartAt":  "2027-05-01T10:00:00",
                              "saleEndAt":    "2027-05-31T23:59:59"
                            }
                            """.formatted(VENUE_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andDo(
                    document("program/schedule/create-409",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(apiResponseFields())
                    ));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/programs/{programId}/schedules — 스케줄 목록 조회")
    class GetSchedules {

        @Test
        @DisplayName("스케줄 목록 조회 성공 — ProgramResponse 전체 반환")
        void success() throws Exception {
            given(programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID))
                .willReturn(programResult());

            mockMvc.perform(
                    get("/api/v1/programs/{programId}/schedules", PROGRAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schedules[0].id").value(SCHEDULE_ID.toString()))
                .andDo(
                    document("program/schedule/list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("programId").description("프로그램 UUID")),
                        responseFields(
                            apiResponseFields(programResponseDataFields()))
                    ));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/programs/{programId}/schedules/{scheduleId} — 스케줄 상세 조회")
    class GetSchedule {

        @Test
        @DisplayName("스케줄 상세 조회 성공 — ScheduleResponse 반환")
        void success() throws Exception {
            given(programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID))
                .willReturn(programResult());

            mockMvc.perform(
                    get("/api/v1/programs/{programId}/schedules/{scheduleId}",
                        PROGRAM_ID, SCHEDULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$.data.remainingCount").value(320))
                .andDo(document("program/schedule/get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID")
                    ),
                    responseFields(
                        apiResponseFields(
                            fieldWithPath("data.id").description("스케줄 UUID"),
                            fieldWithPath("data.venueId").description("공연장 UUID"),
                            fieldWithPath("data.eventStartAt").description("공연 시작"),
                            fieldWithPath("data.eventEndAt").description("공연 종료"),
                            fieldWithPath("data.saleStartAt").description("판매 시작"),
                            fieldWithPath("data.saleEndAt").description("판매 종료"),
                            fieldWithPath("data.totalCapacity").description("총 수용 인원"),
                            fieldWithPath("data.remainingCount").description("잔여 좌석"),
                            fieldWithPath("data.priceGrades[].sectionId").description("구역 UUID"),
                            fieldWithPath("data.priceGrades[].gradeLabel").description("등급명"),
                            fieldWithPath("data.priceGrades[].price").description("가격")
                        ))
                ));
        }

        @Test
        @DisplayName("프로그램에 없는 scheduleId 조회 시 404 — 외부 의존성 실패")
        void fail_scheduleNotFound() throws Exception {
            UUID unknownScheduleId = UUID.randomUUID();
            given(programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID))
                .willReturn(programResult());
            // programResult의 schedules에 unknownScheduleId 없음 → 컨트롤러에서 SCHEDULE_NOT_FOUND

            mockMvc.perform(
                    get("/api/v1/programs/{programId}/schedules/{scheduleId}",
                        PROGRAM_ID, unknownScheduleId))
                .andExpect(status().isNotFound())
                .andDo(document("program/schedule/get-404",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID")
                    )
                ));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/programs/{programId}/schedules/{scheduleId} — 스케줄 수정")
    class UpdateSchedule {

        @Test
        @DisplayName("일부 필드 수정 성공 — null 필드는 기존 값 유지")
        void success() throws Exception {
            given(programCommandService.updateSchedule(any(), any()))
                .willReturn(programResult());

            mockMvc.perform(
                    patch("/api/v1/programs/{programId}/schedules/{scheduleId}",
                        PROGRAM_ID, SCHEDULE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "eventStartAt": "2027-07-01T14:00:00",
                              "eventEndAt":   "2027-07-01T17:00:00"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(
                    document("program/schedule/update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("programId").description("프로그램 UUID"),
                            parameterWithName("scheduleId").description("스케줄 UUID")
                        ),
                        requestFields(
                            fieldWithPath("eventStartAt").type(JsonFieldType.STRING).optional().description("공연 시작 일시"),
                            fieldWithPath("eventEndAt").type(JsonFieldType.STRING).optional().description("공연 종료 일시"),
                            fieldWithPath("saleStartAt").type(JsonFieldType.STRING).optional().description("판매 시작 일시"),
                            fieldWithPath("saleEndAt").type(JsonFieldType.STRING).optional().description("판매 종료 일시"),
                            fieldWithPath("venueId").type(JsonFieldType.STRING).optional().description("공연장 UUID")
                        )
                    ));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/programs/{programId}/schedules/{scheduleId} — 스케줄 삭제")
    class DeleteSchedule {

        @Test
        @DisplayName("스케줄 삭제 성공 — HTTP 200 + ApiResponse<Void>")
        void success() throws Exception {
            willDoNothing().given(programCommandService)
                .deleteSchedule(any(), eq(PROGRAM_ID), eq(SCHEDULE_ID));

            mockMvc.perform(
                    delete("/api/v1/programs/{programId}/schedules/{scheduleId}",
                        PROGRAM_ID, SCHEDULE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("program/schedule/delete",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("삭제할 스케줄 UUID")
                    )
                ));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 가격 등급 (PriceGrade)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST .../price-grades — 가격 등급 추가")
    class AddPriceGrade {

        @Test
        @DisplayName("sectionId 포함 가격 등급 추가 성공 — ProgramResponse 반환")
        void success_withSectionId() throws Exception {
            given(programCommandService.addPriceGrade(any(), any()))
                .willReturn(programResult());

            mockMvc.perform(
                    post("/api/v1/programs/{programId}/schedules/{scheduleId}/price-grades",
                        PROGRAM_ID, SCHEDULE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "gradeLabel": "VIP",
                              "sectionId": "%s",
                              "price": 50000
                            }
                            """.formatted(SECTION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("program/price-grade/add",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-User-Id").description("사용자 UUID"),
                        headerWithName("X-User-Role").description("HOST | ADMIN")
                    ),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID")
                    ),
                    requestFields(
                        fieldWithPath("gradeLabel").description("등급명 (VIP, R석 등)"),
                        fieldWithPath("sectionId").type(JsonFieldType.STRING)
                            .optional()
                            .description("구역 UUID (FREE 타입은 null 허용)"),
                        fieldWithPath("price").description("가격 (0 이상)")
                    )
                ));
        }

        @Test
        @DisplayName("등급명 누락 시 400 — 입력값 유효성 오류")
        void fail_missingGradeLabel() throws Exception {
            mockMvc.perform(
                    post("/api/v1/programs/{programId}/schedules/{scheduleId}/price-grades",
                        PROGRAM_ID, SCHEDULE_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "sectionId": "%s", "price": 50000 }
                            """.formatted(SECTION_ID)))
                .andExpect(status().isBadRequest())
                .andDo(document("program/price-grade/add-400",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID")
                    )
                ));
        }
    }

    @Nested
    @DisplayName("GET .../price-grades — 가격 등급 목록 조회")
    class GetPriceGrades {

        @Test
        @DisplayName("가격 등급 목록 조회 성공 — List<PriceGradeResponse> 반환")
        void success() throws Exception {
            given(programQueryService.getProgramWithoutRemainingCount(PROGRAM_ID))
                .willReturn(programResult());

            mockMvc.perform(
                    get("/api/v1/programs/{programId}/schedules/{scheduleId}/price-grades",
                        PROGRAM_ID, SCHEDULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].gradeLabel").value("VIP"))
                .andExpect(jsonPath("$.data[0].price").value(50_000))
                .andDo(document("program/price-grade/list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID")
                    ),
                    responseFields(apiResponseFields(
                        fieldWithPath("data[].sectionId").description("구역 UUID"),
                        fieldWithPath("data[].gradeLabel").description("등급명"),
                        fieldWithPath("data[].price").description("가격")
                    ))
                ));
        }
    }

    @Nested
    @DisplayName("DELETE .../price-grades/{gradeLabel} — 가격 등급 삭제")
    class RemovePriceGrade {

        @Test
        @DisplayName("가격 등급 삭제 성공 — HTTP 200 + ApiResponse<Void>")
        void success() throws Exception {
            willDoNothing().given(programCommandService)
                .removePriceGrade(any(), eq(PROGRAM_ID), eq(SCHEDULE_ID), eq("VIP"));

            mockMvc.perform(
                    delete(
                        "/api/v1/programs/{programId}/schedules/{scheduleId}/price-grades/{gradeLabel}",
                        PROGRAM_ID, SCHEDULE_ID, "VIP")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("program/price-grade/delete",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("programId").description("프로그램 UUID"),
                        parameterWithName("scheduleId").description("스케줄 UUID"),
                        parameterWithName("gradeLabel").description("삭제할 등급명")
                    )
                ));
        }
    }
}
