package com.firstticket.programservice.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.firstticket.common.response.ApiResponse;
import com.firstticket.programservice.application.dto.result.ScheduleBookingInfoResult;
import com.firstticket.programservice.application.service.ProgramInternalQueryService;
import com.firstticket.programservice.presentation.dto.response.ScheduleBookingInfoResponse;

import lombok.RequiredArgsConstructor;

/**
 * Program Service 내부 API Controller.
 * 예매 서비스의 FeignClient가 호출하는 내부 전용 엔드포인트.
 * 외부 사용자에게 노출하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/programs")
public class ProgramInternalController {

    private final ProgramInternalQueryService programInternalQueryService;

    /**
     * 스케줄 예매 정보 조회.
     * 예매 서비스가 예매 처리 시 프로그램·공연장 정보를 조회하는 내부 API.
     *
     * 응답 정보:
     * - programTitle: 프로그램 제목
     * - venueName: 공연장 이름 (VenueProvider 조회)
     * - venueAddress: 공연장 주소 (VenueProvider 조회)
     * - eventStartAt: 공연 시작 일시
     * - eventEndAt: 공연 종료 일시
     * - saleStartAt: 판매 시작 일시
     * - saleEndAt: 판매 종료 일시
     */
    @GetMapping("/schedules/{scheduleId}/bookingInfo")
    public ResponseEntity<ApiResponse<ScheduleBookingInfoResponse>> getBookingInfo(
        @PathVariable("scheduleId") UUID scheduleId) {
        ScheduleBookingInfoResult result =
            programInternalQueryService.getScheduleBookingInfo(scheduleId);
        return ApiResponse.success(
            ProgramSuccessCode.SCHEDULE_BOOKING_INFO_FOUND,
            ScheduleBookingInfoResponse.from(result)
        );
    }
}
