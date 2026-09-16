# 백엔드 위키 검토 의견

전역 예외 처리를 구현하면서 위키와 어긋나거나 위키 자체를 손봐야 하는 지점을 정리했다.
각 항목은 실제로 실행해 확인한 결과다.

| # | 대상 | 상태 |
| --- | --- | --- |
| 1 | `companions` DDL - SRID 위치 | 위키 수정 필요 |
| 2 | `companions` DDL - 좌표 순서 | 위키 수정 필요 |
| 3 | `VALIDATION_ERROR` 상태 코드 | 팀 결정 필요 |
| 4 | `MAX_LENGTH` 이름 | 코드 반영, 위키 수정 필요 |
| 5 | `@Pattern`의 사유 분기 | 코드 반영, 위키에 규칙 추가 필요 |
| 6 | `details[]` 순서 | 코드 반영 |
| 7 | `NOT_FOUND` 네이밍 | 팀 결정 필요 |

---

## 1. `companions` DDL의 `SRID 4326` 위치

**위키 (동행모집 도메인)**

```sql
origin_location POINT SRID 4326
  GENERATED ALWAYS AS (ST_SRID(POINT(origin_lat, origin_lng), 4326)) STORED NOT NULL,
```

MySQL 8.4에서 이 문법은 실행되지 않는다. 테이블 생성 자체가 실패한다.

```
ERROR 1064 (42000): You have an error in your SQL syntax
  ... near 'GENERATED ALWAYS AS (ST_SRID(POINT(origin_lat, origin_lng), 4326)) STORED NOT NU'
```

생성 컬럼에서는 SRID 속성이 생성 표현식 **뒤에** 와야 한다.

```sql
origin_location POINT
  GENERATED ALWAYS AS (...) STORED NOT NULL SRID 4326,
```

`STORED`와 `VIRTUAL` 모두 이 위치를 따른다.

## 2. `companions` DDL의 좌표 순서

**위키**: `ST_SRID(POINT(origin_lat, origin_lng), 4326)`

MySQL은 `POINT(경도, 위도)` 순서를 기대한다. 위키대로 쓰면 한국 좌표에서 경도 127이
위도 자리에 들어가 INSERT가 실패한다.

```
ERROR 3732 (22S03): contains a geometry with latitude 127.027600,
  which is out of range. It must be within [-90.000000, 90.000000]
```

**수정안**: `ST_SRID(POINT(origin_lng, origin_lat), 4326)`

`ST_Latitude(ST_SRID(POINT(127.0276, 37.4979), 4326))`가 `37.4979`를 반환하는 것으로 확인했다.
1번과 2번을 함께 고치면 STORED · VIRTUAL 생성 컬럼, 공간 인덱스, `ST_Distance_Sphere`가
모두 정상 동작한다. 강남역 기준 거리도 실측과 일치했다.

| 출발지 | 강남역까지 | 서울역까지 |
| --- | --- | --- |
| 강남역 | 0m | 8,067m |
| 판교역 | 13,634m | 21,676m |

## 3. `VALIDATION_ERROR`의 상태 코드

위키에 `400 VALIDATION_ERROR`와 `422 VALIDATION_ERROR`가 함께 나온다.
사용자·인증 도메인과 동행모집 도메인이 422를 쓰고 있어 현재 코드는 **422**로 구현했다.

구현하면서 둘을 나누는 편이 낫다고 판단해 아래처럼 분리했다.

| 상황 | 코드 | 상태 |
| --- | --- | --- |
| JSON이 깨져 본문을 읽지 못함 | `MALFORMED_REQUEST` | 400 |
| 값은 읽혔으나 제약 위반 | `VALIDATION_ERROR` | 422 |

앞은 검증할 값 자체가 없어 `details[]`를 만들 수 없고, 뒤는 어느 필드가 왜 틀렸는지
알려줄 수 있다. 클라이언트 입장에서도 전자는 요청을 만드는 코드의 결함, 후자는
사용자 입력 문제라 대응이 다르다.

## 4. `MAX_LENGTH` → `LENGTH_OUT_OF_RANGE`

**위키**: `@Size(min=2, max=12)` → `MAX_LENGTH`

최소 길이 미달도 같은 사유로 응답하게 되어, 프론트엔드가 문구를 그대로 쓰면
2자 미만 입력에 "너무 깁니다"를 표시하게 된다.

**적용**: `LENGTH_OUT_OF_RANGE`로 변경. 최소 미달과 최대 초과를 모두 포괄한다.

## 5. 같은 애노테이션이 다른 사유로 갈리는 문제

**위키 (사용자·인증 도메인)**

| 필드 | 애노테이션 | reason |
| --- | --- | --- |
| `bank_name` | `@Pattern`(지원 은행 목록) | `INVALID_ENUM` |
| `account_no` | `@Pattern("^\d{10,14}$")` | `INVALID_FORMAT` |

같은 `@Pattern`인데 사유가 다르다. 애노테이션 이름만으로는 구분할 수 없어
`INVALID_ENUM`이 어떤 입력으로도 나올 수 없었다.

커스텀 제약도 같은 문제를 겪는다. `@BankAccountPair`는 위키가 `REQUIRED`로 정했지만
매핑에 없어 기본값인 `INVALID_FORMAT`으로 조용히 잘못 나간다.

**적용**: 제약의 `message` 속성에 사유를 직접 선언하면 그 값을 우선 사용한다.

```java
@Pattern(regexp = "국민|신한|우리", message = "INVALID_ENUM") String bankName
```

`message`를 지정하지 않으면 Bean Validation 기본 문구가 들어오므로,
자동으로 애노테이션 이름 매핑으로 넘어간다.

**팀 규칙 제안**: 커스텀 제약을 만들 때는 `message`에 사유를 반드시 선언한다.
기본 매핑에 의존하면 `INVALID_FORMAT`으로 잘못 나간다.

## 6. `details[]` 순서

**위키**: "필드 선언 순서가 곧 `details[]` 순서이자 대표 `field` 결정 순서입니다."

Hibernate Validator는 위반 결과를 `Set`에 담아 순서를 보장하지 않는다.
정렬하지 않으면 같은 요청이 매번 다른 순서로 응답한다. 실제로 동일 요청 5회에
5가지 순서가 나왔다.

**적용**: 두 단계로 정렬한다.

1. 요청 DTO의 필드 선언 순서 (record의 컴포넌트 순서)
2. 같은 필드 안에서는 `ValidationReason`의 선언 순서 (`REQUIRED`가 가장 앞)

값이 비어 있으면 길이나 형식 위반은 그 결과이므로 원인이 먼저 오게 했다.

위반 항목은 모두 내보낸다. 한 필드가 여러 제약을 위반하면 항목도 여러 개가 된다.
`nickname = "a!"`처럼 길이와 형식을 동시에 어기는 경우 둘 다 알려야
사용자가 한 번에 고칠 수 있다. 무엇을 보여줄지는 클라이언트가 정한다.

**제약**: 이 정렬은 요청 DTO가 record일 때만 동작한다. 일반 클래스의
`getDeclaredFields()`는 선언 순서를 보장하지 않아 정렬 없이 원래 순서를 유지한다.
위키가 요청 DTO를 record로 명시하고 있어 현재는 문제가 없다.

## 7. `NOT_FOUND` 계열 네이밍

위키에 아래 코드들이 혼재한다.

```
404 NOT_FOUND
404 POST_NOT_FOUND
404 COMPANION_POST_NOT_FOUND
CARPOOL_NOT_FOUND
CARPOOL_REQUEST_NOT_FOUND
```

`NOT_FOUND`는 HTTP 상태 코드가 이미 전달한 정보를 반복할 뿐이라 클라이언트가
무엇을 찾지 못했는지 알 수 없다. 커뮤니티 도메인의 `POST_NOT_FOUND`와
동행모집의 `COMPANION_POST_NOT_FOUND`도 같은 대상을 다르게 부르는지 확인이 필요하다.

**제안**: `{도메인}_{대상}_{상태}` 규칙으로 통일한다. 범용 `NOT_FOUND`는 쓰지 않는다.
단, 존재하지 않는 API 경로는 도메인에 속하지 않으므로 별도로 `ENDPOINT_NOT_FOUND`를 둔다.
