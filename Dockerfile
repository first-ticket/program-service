# syntax=docker/dockerfile:1.7

# ===== Stage 1: Build (컴파일 및 패키징) =====
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# 1. 빌드 캐시 최적화를 위해 설정 파일 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

ARG GITHUB_USER

COPY src src

RUN --mount=type=secret,id=github_token \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    GITHUB_USER=$GITHUB_USER \
    ./gradlew clean bootJar --no-daemon -x test -x asciidoctor


# 4. Spring Boot 3의 계층화 기능을 활용해 레이어 추출
RUN java -Djarmode=layertools -jar build/libs/*.jar extract


# ===== Stage 2: Runtime (실제 실행 환경) =====
FROM eclipse-temurin:21-jre-jammy

# 5. 보안 및 관리를 위한 전용 유저 생성
RUN useradd -ms /bin/bash spring
WORKDIR /app

# 6. 추출된 레이어들을 순서대로 복사 (효율적인 캐싱)
COPY --from=builder --chown=spring:spring /app/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/application/ ./

# [중요] 7. 런타임 환경에 .env 파일 주입
# docker-compose에서 env_file을 사용하더라도,
# 애플리케이션 내부에서 직접 파일을 읽는 설정을 위해 복사해두는 것이 안전합니다.
#COPY --chown=spring:spring .env .env

USER spring

# 컨테이너 포트 개방
EXPOSE 8080

# 8. Spring Boot 3.x Launcher 실행
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
