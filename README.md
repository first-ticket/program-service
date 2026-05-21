# ProgramService

> 공연 프로그램·스케줄·가격등급을 관리하는 마이크로서비스

프로그램 상태 전이(DRAFT → ON_SALE → CLOSED)를 통해 판매 라이프사이클을 관리하고,
Kafka Outbox 패턴으로 좌석 서비스·대기열 서비스와 연동합니다.

---

## 🛠️ 개발 환경

| 항목 | 버전 |
|------|------|
| Java | 21 (Temurin) |
| Spring Boot | 3.5.x |
| Spring Cloud | 2025.x |
| PostgreSQL | 15 |
| QueryDSL | 5.1.0 (Jakarta) |
| Flyway | 최신 (PostgreSQL 전용) |
| Kafka | - |
| Redis | - |

---

## ⚙️ 개발 환경 구축

### 1. 사전 요구사항

- Docker Desktop 설치
- GitHub Personal Access Token (`read:packages` 권한)

### 2. 저장소 클론

```bash
git clone https://github.com/first-ticket/program-service.git
cd program-service
```

### 3. 환경 변수 설정

루트 디렉토리에 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

```
# .env
GITHUB_USER=깃허브유저명
GITHUB_TOKEN=ghp_xxxxxxxxxxxx

CONFIG_SERVER_USERNAME=
CONFIG_SERVER_PASSWORD=
SPRING_PROFILES_ACTIVE=prod,kafka

# PostgreSQL
DB_HOST=
POSTGRES_USER=
POSTGRES_PASSWORD=
POSTGRES_DB=first_ticket

KAFKA_BOOTSTRAP_SERVERS=

EUREKA_SERVER_URL=eureka-server

ZIPKIN_ENDPOINT=
```

> `GITHUB_TOKEN`은 GitHub → Settings → Developer settings → Personal access tokens → `read:packages` 권한으로 발급합니다.

---

## 🔨 빌드 및 실행

### 로컬 빌드

```bash
./gradlew build
```

### 테스트 실행

```bash
./gradlew test
```

> `@DataJpaTest`는 H2 in-memory DB를 사용합니다.
> `ScheduleOverlapQueryTest`는 Testcontainers + PostgreSQL을 사용하므로 Docker가 실행 중이어야 합니다.

### Docker 실행

```bash
# 1. 빌드 먼저 실행 (REST Docs 포함)
./gradlew build


# 2. 컨테이너 실행
docker-compose up --build
```

---
## 🌐 API 목록

### 프로그램 (Program)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/programs` | HOST·ADMIN | 프로그램 등록 (초기 상태: DRAFT) |
| `GET` | `/api/v1/programs` | ALL | 목록 조회 (카테고리·키워드·지역·날짜·정렬) |
| `GET` | `/api/v1/programs/{programId}` | ALL | 상세 조회 (잔여 좌석 포함) |
| `PATCH` | `/api/v1/programs/{programId}` | HOST·ADMIN | 수정 (상태별 수정 가능 필드 분리) |
| `DELETE` | `/api/v1/programs/{programId}` | HOST·ADMIN | 삭제 (DRAFT 상태만 가능) |
| `PATCH` | `/api/v1/programs/{programId}/publish` | HOST·ADMIN | 판매 시작 (DRAFT → ON_SALE) |
| `PATCH` | `/api/v1/programs/{programId}/cancel` | HOST·ADMIN | 취소 (→ CANCELLED) |
| `PATCH` | `/api/v1/programs/{programId}/close` | HOST·ADMIN | 종료 (→ CLOSED) |

### 스케줄 (Schedule)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/programs/{programId}/schedules` | HOST·ADMIN | 스케줄 등록 |
| `GET` | `/api/v1/programs/{programId}/schedules` | ALL | 스케줄 목록 조회 |
| `GET` | `/api/v1/programs/{programId}/schedules/{scheduleId}` | ALL | 스케줄 상세 조회 |
| `PATCH` | `/api/v1/programs/{programId}/schedules/{scheduleId}` | HOST·ADMIN | 스케줄 수정 |
| `DELETE` | `/api/v1/programs/{programId}/schedules/{scheduleId}` | HOST·ADMIN | 스케줄 삭제 |

### 가격 등급 (PriceGrade)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| `POST` | `.../schedules/{scheduleId}/price-grades` | HOST·ADMIN | 가격 등급 추가 |
| `GET` | `.../schedules/{scheduleId}/price-grades` | ALL | 가격 등급 목록 조회 |
| `DELETE` | `.../schedules/{scheduleId}/price-grades/{gradeLabel}` | HOST·ADMIN | 가격 등급 삭제 |

> REST Docs 문서: 서버 실행 후 `/docs/program-api.html` 에서 확인할 수 있습니다.

---

## 🗄️ 도메인 설계

### 프로그램 상태 전이

```
DRAFT ──publish──▶ ON_SALE ──cancel──▶ CANCELLED
                        └──close───▶ CLOSED
SOLD_OUT ──────────close──▶ CLOSED

DRAFT → CANCELLED 직접 전이 불가 (ON_SALE 경유 필수)
```

| 상태 | 수정 가능 필드 |
|------|--------------|
| `DRAFT` | title, category, theme, posterUrl, description |
| `ON_SALE` | posterUrl, description |
| `CANCELLED` / `CLOSED` | 수정 불가 |

### 스케줄 등록

- `totalCapacity` — VenueService Feign 호출로 자동 계산 (주최자 직접 입력 없음)
- 공연장 시간 겹침 이중 방어 (V-04)
  - 비관적 락 `findOverlappingSchedulesWithLock()` (`SELECT ... FOR UPDATE`)
  - PostgreSQL `tsrange` exclusion constraint

### 가격 등급 (PriceGrade)

| 프로그램 타입 | sectionId |
|-------------|----------|
| `SEATED` / `STANDING` | 필수 |
| `FREE` | null 허용 |

- 판매 시작(`saleStartAt`) 이후 추가·삭제 불가

### Kafka 이벤트 (Outbox 패턴)

| 이벤트 | 발행 시점 | 수신 서비스 |
|--------|----------|------------|
| `program.created` | 프로그램 등록 | 대기열 서비스 |
| `schedule.created` | `publish()` 시점 | 좌석 서비스 (BookingSeat 생성) |
| `program.time.updated` | `publish()` / 스케줄 수정 | 대기열 서비스 |
| `program.cancelled` | 프로그램 취소 | 대기열 서비스 |

> `schedule.created`는 `createSchedule()`이 아닌 `publish()` 시점에 발행합니다.
> PriceGrade가 확정된 이후 BookingSeat이 생성되어야 하기 때문입니다.

---

## 📋 응답 형식

### ✅ 성공 응답

```json
{
  "success": true,
  "code": "PROGRAM_CREATED",
  "message": "프로그램이 등록되었습니다",
  "timestamp": "2027-01-01T00:00:00",
  "data": {
    "id": "aaaaaaaa-0000-0000-0000-000000000001",
    "title": "테스트 공연",
    "status": "DRAFT",
    "schedules": []
  }
}
```

### ❌ 에러 응답

```json
{
  "success": false,
  "code": "INVALID_STATUS_TRANSITION",
  "message": "허용되지 않는 상태 전이입니다",
  "timestamp": "2027-01-01T00:00:00"
}
```

---

## 🔴 에러 코드

### Program

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `PROGRAM_NOT_FOUND` | 404 | 프로그램을 찾을 수 없습니다 |
| `PROGRAM_NOT_EDITABLE` | 422 | 수정할 수 없는 상태의 프로그램입니다 |
| `PROGRAM_NOT_DELETABLE` | 422 | 삭제할 수 없는 상태의 프로그램입니다 |
| `PROGRAM_NOT_DELETABLE_IN_PROCESS` | 422 | 진행 중인 프로그램은 삭제할 수 없습니다 |
| `PROGRAM_NOT_ENDED_YET` | 409 | 아직 종료되지 않은 스케줄이 있습니다 |
| `INVALID_STATUS_TRANSITION` | 422 | 허용되지 않는 상태 전이입니다 |
| `INVALID_TITLE` | 400 | 제목은 필수입니다 |
| `INVALID_CATEGORY` | 400 | 카테고리는 필수입니다 |
| `INVALID_THEME` | 400 | 테마는 필수입니다 |
| `INVALID_PROGRAM_TYPE` | 400 | 프로그램 타입은 필수입니다 |
| `INVALID_REGION` | 400 | 지역은 필수입니다 |

### Schedule

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `SCHEDULE_NOT_FOUND` | 404 | 스케줄을 찾을 수 없습니다 |
| `SCHEDULE_REQUIRED` | 400 | 판매 시작 전 스케줄을 등록해야 합니다 |
| `SCHEDULE_NOT_EDITABLE` | 422 | 수정할 수 없는 상태의 스케줄입니다 |
| `SCHEDULE_NOT_DELETABLE` | 422 | 삭제할 수 없는 상태의 스케줄입니다 |
| `VENUE_TIME_CONFLICT` | 409 | 해당 공연장에 이미 예약된 일정이 있습니다 |
| `INVALID_EVENT_PERIOD` | 400 | 공연 종료 시각은 시작 시각보다 이후여야 합니다 |
| `SALE_END_AFTER_EVENT_START` | 400 | 판매 종료는 공연 시작 이전이어야 합니다 |
| `PAST_EVENT_START` | 400 | 공연 시작 시각은 현재 이후여야 합니다 |
| `INVALID_CAPACITY` | 400 | 수용 인원은 1명 이상이어야 합니다 |

### PriceGrade

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `PRICE_GRADE_REQUIRED` | 400 | 판매 시작 전 가격등급을 등록해야 합니다 |
| `PRICE_GRADE_NOT_FOUND` | 404 | 가격 등급을 찾을 수 없습니다 |
| `PRICE_GRADE_DUPLICATE` | 409 | 이미 등록된 가격 등급입니다 |
| `INVALID_PRICE` | 400 | 가격은 0원 이상이어야 합니다 |
| `INVALID_GRADE_LABEL` | 400 | 등급명은 필수입니다 |
| `CANNOT_MODIFY_AFTER_SALE_START` | 409 | 판매 시작 이후 가격등급을 수정할 수 없습니다 |
| `SECTION_ID_REQUIRED` | 400 | SEATED·STANDING 타입은 구역 ID가 필수입니다 |
| `SECTION_ID_NOT_ALLOWED` | 400 | FREE 타입은 구역 ID를 입력할 수 없습니다 |
| `SECTION_TYPE_MISMATCH` | 400 | 구역 타입이 프로그램 타입과 일치하지 않습니다 |

---

## 🟢 성공 코드

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `PROGRAM_CREATED` | 201 | 프로그램이 등록되었습니다 |
| `PROGRAM_FOUND` | 200 | 프로그램을 조회했습니다 |
| `PROGRAM_LIST_FOUND` | 200 | 프로그램 목록을 조회했습니다 |
| `PROGRAM_UPDATED` | 200 | 프로그램이 수정되었습니다 |
| `PROGRAM_DELETED` | 200 | 프로그램이 삭제되었습니다 |
| `PROGRAM_PUBLISHED` | 200 | 프로그램이 판매 시작되었습니다 |
| `PROGRAM_CANCELLED` | 200 | 프로그램이 취소되었습니다 |
| `PROGRAM_CLOSED` | 200 | 프로그램이 종료되었습니다 |
| `SCHEDULE_CREATED` | 201 | 스케줄이 등록되었습니다 |
| `SCHEDULE_FOUND` | 200 | 스케줄을 조회했습니다 |
| `SCHEDULE_LIST_FOUND` | 200 | 스케줄 목록을 조회했습니다 |
| `SCHEDULE_UPDATED` | 200 | 스케줄이 수정되었습니다 |
| `SCHEDULE_DELETED` | 200 | 스케줄이 삭제되었습니다 |
| `PRICE_GRADE_CREATED` | 201 | 가격 등급이 추가되었습니다 |
| `PRICE_GRADE_LIST_FOUND` | 200 | 가격 등급 목록을 조회했습니다 |
| `PRICE_GRADE_DELETED` | 200 | 가격 등급이 삭제되었습니다 |

---
