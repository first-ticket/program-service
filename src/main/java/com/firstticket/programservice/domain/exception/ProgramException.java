package com.firstticket.programservice.domain.exception;

import com.firstticket.common.exception.BusinessException;

/**
 * 프로그램 도메인 전용 비즈니스 예외 클래스
 * 공통 모듈의 BusinessException을 상속받아, 프로그램 서비스 내의
 * 비즈니스 로직 위반 시 일관된 예외 처리를 수행합니다.
 */
public class ProgramException extends BusinessException {
    /**
     * 프로그램 전용 에러 코드를 인자로 받아 예외를 생성합니다.
     * @param errorCode 발생한 비즈니스 에러의 종류 (ProgramErrorCode)
     */
    public ProgramException(ProgramErrorCode errorCode) {
        super(errorCode);
    }
}
