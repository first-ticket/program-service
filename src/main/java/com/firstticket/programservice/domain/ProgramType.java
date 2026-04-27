package com.firstticket.programservice.domain;

/**
 * 공연 타입(ProgramType) 열거형
 *
 * 프로그램의 좌석 운영 방식을 정의
 * 타입에 따라 좌석 생성 방식, 가격 등급 설정, 재고 관리 방식이 달라짐
 *
 * ┌──────────┬──────────────────────────┬──────────────────────────────────────┐
 * │  타입     │  좌석 관리 방식            │  BookingSeat 생성 기준                │
 * ├──────────┼──────────────────────────┼──────────────────────────────────────┤
 * │ SEATED   │ VenueSeat (물리 고정 좌석) │ VenueSeat 복사                        │
 * │ STANDING │ Section.capacity (상한선) │ 프로그램 등록 시 지정한 구역·인원 기반     │
 * │ FREE     │ Section.capacity (상한선) │ 프로그램 등록 시 지정한 구역·인원 기반     │
 * └──────────┴──────────────────────────┴──────────────────────────────────────┘
 */
public enum ProgramType {
    /**
     * 지정 좌석 공연.
     * - 좌석: Section 등록 시 rowCount × colCount 개 VenueSeat 자동 생성
     * - 가격 등급(PriceGrade): sectionId 필수
     * - 재고: BookingSeat 상태(AVAILABLE·RESERVED)로 관리
     * - 예시: 콘서트, 뮤지컬, 연극, 스포츠 경기
     */
    SEATED,

    /**
     * 스탠딩 공연.
     * - 좌석: VenueSeat 없음. ScheduleSectionCapacity로 회차별 인원 관리
     * - 가격 등급(PriceGrade): sectionId 필수
     * - 재고: Redis 원자 연산으로 구역별 잔여 인원 차감 (BK-03)
     * - 예시: 페스티벌, 스탠딩 콘서트
     */
    STANDING,

    /**
     * 자유 입장 공연.
     * - 좌석: VenueSeat 없음. ScheduleSectionCapacity로 회차별 인원 관리
     * - 가격 등급(PriceGrade): sectionId null (단일 가격)
     * - 재고: Redis 원자 연산으로 전체 잔여 수량 차감
     * - 예시: 전시회, 팝업 스토어, 박람회
     */
    FREE
}
