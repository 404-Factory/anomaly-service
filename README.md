# SIGMA 스마트 팩토리 이상 탐지 및 이벤트 제어 서비스 (Anomaly-Service)

이 프로젝트는 **스프링 부트(Spring Boot)** 기반의 고성능 마이크로서비스로, 스마트 팩토리 실시간 공정 내 설비에서 감지된 센서 이상 징후(`Anomaly`)를 적재하고, 분산 환경에서의 동시성 제어 및 중복 억제(Deduplication)를 수행하며, 신뢰성 있는 이벤트 브로커 연동을 통해 전체 이벤트 파이프라인을 조율하는 핵심 서비스입니다.

특히 Flink 실시간 스트림 위반 이벤트 유입에 대응하는 **Lettuce 기반 분산 락**, **동적 심각도 업그레이드(Dynamic Severity Upgrade) 정책**, **Transactional Outbox 패턴**, 그리고 **AI 비동기 분석 컨텍스트 파이프라인**을 통합 제공합니다.

---

## 📌 아키텍처 및 시스템 흐름도

공정 내 센서 데이터가 실시간으로 수집되어 최종 AI 분석 결과가 챗봇에 전달되기까지의 흐름입니다.

```mermaid
sequenceDiagram
    autonumber
    participant F as Flink Stream Job
    participant K as Kafka Topic
    participant A as Anomaly Service
    participant R as Redis (Lock/Cache)
    participant DB as MariaDB (JPA)
    participant C as Chatbot Service (AI)

    F->>K: 1. 센서 룰 위반 데이터 발행 (sensor-violations)
    K->>A: 2. 위반 데이터 소비 (SensorViolationConsumer)
    A->>R: 3. Lettuce 분산 락 획득 시도 (anomaly:lock:...)
    A->>R: 4. Redis 캐시 이력 확인 (anomaly:cache:...)
    alt 신규 이상 발생 (캐시 미존재)
        A->>DB: 5a. 신규 Anomaly 및 Outbox 저장 (One Transaction)
        A->>R: 6a. Redis 캐시 등록 (TTL 5분)
    else 기존 이상 중복 발생 (캐시 존재)
        A->>DB: 5b. Anomaly 업데이트 (수량 증분, Severity 상향 조정)
    end
    A->>R: 7. 분산 락 해제 (finally 블록 보장)
    A->>K: 8. AI 분석 요청 이벤트 발행 (anomaly.analysis-requested)
    K->>C: 9. 이벤트 수신 및 Bedrock AI 분석 실행
```

---

## 📂 프로젝트 구조

```
src/main/java/com/factory/anomaly/
  ├── config/              # Redis (Lettuce), QueryDSL, JPA, Kafka 등 아키텍처 설정
  ├── controller/          # REST API 엔드포인트 (이상 로그 조회, 수동 AI 분석 트리거)
  ├── domain/              # DTO, Enum (Severity, AnomalyType, AnalysisStatus 등)
  ├── event/               # Kafka 이벤트 페이로드 및 Outbox 이벤트 리스너
  │   ├── handler/         # Outbox 테이블의 이벤트를 Kafka로 발행하는 핸들러
  │   └── payload/         # AnalysisRequestedPayload, AnomalyCreatedPayload
  ├── exception/           # 비즈니스 예외 처리 및 글로벌 익셉션 핸들러
  ├── infrastructure/      # 데이터 액세스 및 외부 연동 레이어
  │   ├── entity/          # Anomaly, OutboxEvent, EquipmentInfo 등 JPA 엔티티
  │   ├── querydsl/        # AnomalyRepositorySupport (QueryDSL 동적 쿼리 구현)
  │   └── redis/           # SensorRedisRepository (Lettuce 락/캐시 제어)
  └── service/             # 비즈니스 로직
      ├── AnomalyDetectionService.java # Flink 유입 이벤트 파이프라인 처리
      └── AnomalyService.java          # Anomaly 조회 및 AI 분석 트리거 관리
src/main/resources/
  ├── application.yml      # 기본 및 환경별 Profile 설정
  └── db/migration/        # Flyway DB 마이그레이션 스크립트 (스키마 정의)
```

---

## ⚙️ 핵심 기술 및 구현 상세

### 1. Flink 실시간 스트림 연동 (`SensorViolationConsumer`)
* Flink 분석 엔진이 1분/1초 단위 슬라이딩 윈도우 내에서 설정된 임계치(임계온도 이탈, 회전속도 초과 등)를 벗어난 센서 데이터를 위반 이벤트(`sensor-violations` 토픽)로 발행합니다.
* `SensorViolationConsumer`는 이를 실시간으로 Consume하여 시스템 부하 없이 비즈니스 검증 레이어로 전달합니다.

### 2. Lettuce 기반 분산 락 & 캐시 중복 억제 (Deduplication)
고주파 스트림 환경에서 동일 설비의 동일 센서 에러가 동시다발적으로 밀려올 경우 발생하는 **DB Race Condition(중복 생성)**을 방지하기 위해 분산 락 및 캐싱 메커니즘을 결합했습니다.

* **동시성 제어 (Lettuce Distributed Lock)**:
  * Redis Connection Resource 효율성을 극대화하기 위해 Redisson 대신 Lettuce의 `setIfAbsent`를 활용하여 경량화된 분산 락을 직접 구현했습니다.
  * **락 Key 포맷**: `anomaly:lock:{equipmentCode}:{parameterName}:{ruleName}:{anomalyType}`
  * 획득 실패 시 50ms 간격으로 스핀하며 재시도하고, 안전을 위해 5초의 최대 Lock TTL을 부여하여 데드락을 방지합니다.
  * `try-finally` 구조로 트랜잭션 정상/실패 여부와 관계없이 분산 락 반환을 100% 보장합니다.
* **캐시 중복 억제 (Deduplication Cache)**:
  * 락 획득 성공 후, `anomaly:cache:{equipmentCode}:{parameterName}:{ruleName}:{anomalyType}` Key를 가진 캐시를 조회합니다.
  * 캐시가 존재할 경우 신규 DB Insert를 생략하고, 기존 Anomaly 엔티티의 누적 카운트를 1 증가시키고 최종 탐지 시각을 동기화합니다.
* **동적 심각도 업그레이드 (Dynamic Severity Upgrade)**:
  * 기존 이상 정보가 누적 업데이트될 때, 새로 유입된 에러 신호의 심각도가 기존 값보다 높다면 최상위 등급으로 상향 조정합니다. (예: `INFO` -> `WARNING` -> `CRITICAL`)

### 3. Transactional Outbox 패턴을 통한 메시지 신뢰성 보장
데이터베이스 수정과 외부 메시지 브로커(Kafka) 발행을 단일 로컬 트랜잭션으로 묶어 **이중 쓰기(Dual Write) 문제**를 해결하고, **최소 1회 전송(At-least-once Delivery)**을 완벽히 보장합니다.
* 이상 로그가 DB에 저장될 때, 아웃박스 엔티티(`OutboxEvent`)도 동일 트랜잭션 내에 삽입됩니다.
* JPA `EntityListener` 또는 Spring `ApplicationEventPublisher`를 통해 트랜잭션 커밋 완료 시점에 비동기적으로 Kafka Event를 발행하고, 발행이 완료되면 Outbox 레코드의 상태를 변경하여 전송 상태를 트래킹합니다.

### 4. AI 비동기 분석 컨텍스트 파이프라인 설계
`chatbot-service` 내부의 AWS Bedrock LLM이 스마트 팩토리 데이터를 다각도로 해석할 수 있도록, 이상 탐지 및 수동 요청 시점에 풍부한 컨텍스트 정보를 패키징하여 전달합니다.
* **연동 필드**:
  1. `sampleCount`: 이상 탐지 누적 횟수 (분산 락을 통해 정확히 집계된 값)
  2. `lastDetectedAt`: 최종 이상 탐지 시각
  3. `severity`: 최종 조정된 심각도 등급
  4. `ruleType`: 적용된 비즈니스 룰 분류
* 본 메타데이터는 `AnalysisRequestedPayload`를 거쳐 챗봇의 Bedrock 프롬프트로 주입되어 분석 결과의 정밀도를 대폭 상향시킵니다.

---

## 📊 REST API 명세

### 1. 이상 로그 목록 조회 (QueryDSL 동적 필터 및 페이징)
* **Request**
  ```http
  GET /api/anomalies?processId=1&equipmentId=2&keyword=Temperature&page=0&size=10
  ```
* **Response**
  ```json
  {
    "success": true,
    "status": 200,
    "message": "success",
    "data": {
      "content": [
        {
          "anomalyId": 124,
          "equipmentId": 2,
          "equipmentName": "EQP-DEPOSITION-001",
          "parameterName": "Soft Bake Temperature",
          "ruleName": "Temp Limit Exceeded",
          "anomalyType": "OUT_OF_BOUNDS",
          "severity": "CRITICAL",
          "sampleCount": 42,
          "firstDetectedAt": "2026-06-11T04:10:00",
          "lastDetectedAt": "2026-06-11T04:22:15",
          "analysisStatus": "SUCCESS"
        }
      ],
      "pageable": {
        "pageNumber": 0,
        "pageSize": 10,
        "totalElements": 1,
        "totalPages": 1
      }
    },
    "timestamp": "2026-06-11T04:23:00.124"
  }
  ```

### 2. 이상 로그 상세 및 AI 분석 결과 조회
* **Request**
  ```http
  GET /api/anomalies/124
  ```
* **Response**
  ```json
  {
    "success": true,
    "status": 200,
    "message": "success",
    "data": {
      "anomalyId": 124,
      "equipmentId": 2,
      "equipmentName": "EQP-DEPOSITION-001",
      "parameterName": "Soft Bake Temperature",
      "ruleName": "Temp Limit Exceeded",
      "anomalyType": "OUT_OF_BOUNDS",
      "severity": "CRITICAL",
      "sampleCount": 42,
      "analysisStatus": "SUCCESS",
      "aiReport": {
        "reportId": 89,
        "summary": "Soft Bake 공정 중 온도가 허용 임계치(110°C)를 초과하여 CRITICAL 등급 이상이 42회 누적 발생했습니다. 히스토리 분석 결과, 히터 단선의 징후가 보이며 Spin Speed와의 상관계수가 높아 불량 유발 확률이 높습니다.",
        "recommendation": "1. 히터 센서 정밀 캘리브레이션 실행\n2. 온도를 105°C 이하로 즉시 억제하고 스핀 속도를 2200 RPM 수준으로 유지 조치 권장",
        "generatedAt": "2026-06-11T04:23:05"
      }
    },
    "timestamp": "2026-06-11T04:23:10.552"
  }
  ```

### 3. AI 분석 수동 재수행 요청 (Manual Trigger)
* **Request**
  ```http
  POST /api/anomalies/124/analyze
  Content-Type: application/json

  {
    "focusArea": "HEATER_UNIT",
    "hints": "최근 3일간의 동일 모델 캘리브레이션 이력 반영 요망"
  }
  ```
* **Response**
  ```json
  {
    "success": true,
    "status": 202,
    "message": "AI analysis task requested successfully",
    "data": {
      "anomalyId": 124,
      "analysisStatus": "RUNNING"
    },
    "timestamp": "2026-06-11T04:24:01.002"
  }
  ```

### 4. 설비별 이상 발생 건수 조회 (내부 배치 통계용)
* **Request**
  ```http
  GET /api/anomalies/count?equipmentName=EQP-DEPOSITION-001&since=2026-06-11T00:00:00
  ```
* **Response**
  ```json
  {
    "success": true,
    "status": 200,
    "message": "success",
    "data": {
      "equipmentName": "EQP-DEPOSITION-001",
      "count": 14,
      "since": "2026-06-11T00:00:00"
    },
    "timestamp": "2026-06-11T04:24:15.918"
  }
  ```

---

## 🧪 검증 및 멀티스레드 동시성 테스트

분산 락과 중복 제거 로직이 멀티스레드 환경에서 올바르게 동기화되는지 검증하기 위해 JUnit 동시성 테스트를 수행합니다.

* **검증 시나리오 (`AnomalyDetectionServiceTest`)**:
  * 동일 설비 및 센서에 대한 이상 징후 위반 이벤트 4개가 동시에 유입되는 상황 모사.
  * `ExecutorService`를 활용해 4개의 독립 스레드에서 `AnomalyDetectionService.detect()`를 병렬 호출.
  * `CountDownLatch`를 사용하여 스레드들이 동시에 실행을 시작하도록 임계점 설정.
  * **결과 검증**:
    * 데이터베이스 내에 Anomaly 레코드가 단 **1개**만 신규 삽입(Insert)되는지 검증.
    * 나머지 3개의 호출은 캐시를 타고 기존 레코드를 누적 카운트 증분(Update)하는지 검증.
    * `sampleCount` 결과값이 정확히 `4`가 되는지 확인.

* **테스트 명령어**:
  ```bash
  ./gradlew test --tests "com.factory.anomaly.service.AnomalyDetectionServiceTest"
  ```

---

## 💾 환경 변수 및 설정

로컬 및 배포 환경에서 구동하기 위해 필요한 환경 변수 목록입니다. 보안 강화를 위해 실제 자격 증명(Credential)은 README에 기술하지 않으며, 루트 디렉토리에 `.env.example` 파일을 참고하여 `.env` 파일을 생성해 사용합니다.

| 환경 변수명 | 설명 | 예시 / 기본값 |
| :--- | :--- | :--- |
| `DB_URL` | 데이터베이스 연결 URL (MariaDB/MySQL 호환) | `jdbc:mariadb://localhost:3306/anomaly_db` |
| `DB_USERNAME` | 데이터베이스 접속 계정명 | `your_db_user` |
| `DB_PASSWORD` | 데이터베이스 접속 패스워드 | `your_db_password` |
| `DB_MAX_POOL_SIZE` | HikariCP 최대 커넥션 풀 크기 | `10` |
| `DB_MIN_IDLE` | HikariCP 최소 유휴 커넥션 수 | `5` |
| `REDIS_HOST` | Redis 호스트 주소 (Lettuce 분산 락/캐시용) | `localhost` |
| `REDIS_PORT` | Redis 포트 번호 | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Apache Kafka 브로커 주소 | `localhost:9092` |
| `VIOLATION_TOPIC` | Flink 룰 위반 이벤트 수신 Kafka 토픽명 | `sensor-violations` |
| `VIOLATION_GROUP_ID` | Kafka 컨슈머 그룹 ID | `anomaly-violation-group` |

---

## 🚀 로컬 실행 방법

### 1. 의존성 및 빌드 검증
```bash
./gradlew clean build -x test
```

### 2. 로컬 실행
```bash
./gradlew bootRun
```
