# Scenario Test Guide

이 문서는 OpenRouter 연동 후 수동 시나리오 테스트를 진행하기 위한 기준입니다.

## 사전 조건

- `.env`에 `OPENROUTER_API_KEY`가 설정되어 있다.
- Qdrant 또는 PostgreSQL 연동을 검증하려면 `scripts/infra.ps1 up`으로 로컬 인프라를 먼저 실행한다.
- `.env` 설정:

```properties
LLM_PROVIDER=openrouter
EMBEDDING_PROVIDER=openrouter
RERANKER_PROVIDER=mock
VECTOR_PROVIDER=inmemory
```

- 서버가 `http://localhost:8080`에서 실행 중이다.

## 빠른 점검

서버 실행 전에 `.env`를 점검:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1
```

외부 provider 구성을 강하게 확인하려면:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1 -Strict
```

`preflight.ps1`는 OpenRouter/Cohere/Qdrant 필수 설정과 감사 로그 안전 플래그를 확인한다. API key 값은 출력하지 않는다.
`SERVER_PORT`, `LLM_TEMPERATURE`, `RERANKER_TOP_K` 같은 숫자 설정도 함께 확인한다.

이미 실행 중인 서버의 화면/API 계약을 한 번에 점검:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1
```

`verify-runtime.ps1`와 `run-scenarios.ps1`는 `-BaseUrl`을 넘기지 않으면 환경 변수 `SERVER_PORT`, `.env`의 `SERVER_PORT`, `8080` 순서로 서버 URL을 정한다.

기본 점검 항목:

- `/`, `/admin.html` 화면의 한국어 문구와 금지 문구
- `/healthz`, `/api/v1/health` 상태
- `/api/admin/status`, `/api/v1/admin/status` 색인 상태
- `/api/laws` 기본 법령 목록
- `/api/ask` camelCase 응답 계약
- `/api/v1/ask` snake_case 응답 계약
- `/api/v1` 응답에 camelCase 필드가 섞이지 않는지 확인

검증 산출물:

```text
target/runtime-evidence/healthz.json
target/runtime-evidence/v1-health.json
target/runtime-evidence/admin-status.json
target/runtime-evidence/v1-admin-status.json
target/runtime-evidence/ask-response.json
target/runtime-evidence/v1-ask-response.json
target/runtime-evidence/runtime-summary.json
```

`runtime-summary.json`은 snapshot, 색인 상태, ask/v1 ask requestId, citation 개수, provider/reindex 옵션 사용 여부를 요약한다.

Provider smoke test:

```powershell
curl.exe -s -X POST http://localhost:8080/api/admin/provider-smoke-test
```

관리 화면에서는 `관리 > Provider 점검 > 연결 점검`으로 같은 확인을 수행할 수 있다.

확인 항목:

- `llmProvider=openrouter`
- `embeddingProvider=openrouter`
- `rerankerProvider=mock` 또는 `cohere`
- `llmStatus=ok`
- `embeddingStatus=ok`
- `rerankerStatus=ok`
- `embeddingDimensions=1024`
- `rerankedCount=1`

일부 provider가 실패해도 smoke API는 전체 요청을 실패시키지 않고 실패한 provider의 `failed` 상태와 안전한 오류 코드를 반환한다.

Provider까지 포함해 런타임 검증을 실행하려면:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke
```

이 옵션은 설정된 LLM, embedding, reranker API를 실제로 1회 이상 호출한다.

`POST /api/v1/admin/reindex`의 `202 Accepted` 계약까지 확인하려면:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeReindexContract
```

이 옵션은 `ADMIN_REINDEX_ENABLED=true`인 상태에서만 사용한다. 실제 재색인 작업과 embedding 호출이 발생할 수 있으므로 기본 검증에는 포함하지 않는다.

## 시나리오 실행

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1
```

실행 데이터:

```text
harness/scenario-requests.json
```

실행 산출물:

```text
target/scenario-responses/{scenario-id}.json
target/scenario-responses/scenario-summary.json
```

`scenario-summary.json`은 각 시나리오의 status, citation 수, vector hit, requestId, 검색 로그 연결 여부, agent trace 순서 검증 여부, 금지 문구 검증 결과를 요약한다. 질문 원문은 요약 파일에 저장하지 않는다.

런타임 검증과 시나리오 검증이 모두 끝난 뒤 제출용 검증 리포트를 만들려면:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1
```

결과는 `target/evidence-report.md`에 저장된다. 이 파일은 `runtime-summary.json`과 `scenario-summary.json`을 기반으로 smoke 결과, 시나리오 pass/fail, requestId 연결, 검색 로그 확인, agent trace 확인 결과를 정리하며 API key나 질문 원문 전체는 포함하지 않는다.

검증 항목:

- 기술자료 제공 질문은 `OK`와 인용 조문을 반환한다.
- 전략물자 수출 질문은 `OK`와 인용 조문을 반환한다.
- 한국어 기술자료 질문은 검색어가 정규화되고 인용 조문을 반환한다.
- 정보가 부족한 질문은 `INSUFFICIENT_INFO`를 반환한다.

## Swagger 수동 테스트

```text
http://localhost:8080/swagger-ui/index.html
```

`POST /api/ask` 요청:

```json
{
  "question": "해외 업체에 기술자료를 제공해도 되나요?",
  "asOf": null
}
```

확인 항목:

- `status`가 `OK`다.
- `citedArticles`가 비어 있지 않다.
- `reasoning`이 인용 조문을 근거로 작성된다.
- `diagnostics.requestId`로 응답, 검색 로그, agent trace를 연결하고, `diagnostics.generatedQueries`에서 검색 경로를 확인할 수 있다.

관리 API 확인:

```text
http://localhost:8080/api/admin/search-logs
http://localhost:8080/api/admin/agent-traces
http://localhost:8080/api/admin/ingestion-runs
```

## Runtime Audit Checks

`scripts/run-scenarios.ps1`는 각 `POST /api/ask` 응답의 `diagnostics.requestId`를 기준으로 다음 항목도 함께 확인한다.

- 같은 `requestId`의 검색 로그가 `GET /api/admin/search-logs`에 존재한다.
- 같은 `requestId`의 agent trace가 `GET /api/admin/agent-traces?requestId=...`로 조회된다.
- trace 단계가 `QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic` 순서로 저장된다.

검색 로그, 에이전트 추적, 재색인 실행 이력이 생성되는지 확인한다.

`scripts/verify-runtime.ps1`는 위 시나리오 하네스보다 더 짧은 smoke test다. 실행 중인 서버를 재시작하지 않고 다음 계약을 확인한다.

- root/admin 정적 화면이 정상 응답한다.
- liveness/readiness가 정상 응답한다.
- 관리자 상태의 색인이 `healthy`다.
- `/api/ask`는 Spring UI용 camelCase 계약을 유지한다.
- `/api/v1/ask`와 `/api/v1/admin/status`는 원본 호환 snake_case 계약을 유지한다.
- 응답에 demo/sample/mini project 등 포트폴리오 설명용 문구가 노출되지 않는다.
