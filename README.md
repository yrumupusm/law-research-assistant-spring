# Law Research Assistant Spring

Spring Boot 기반 법령 조사 보조 서비스입니다. 자연어 질문을 분석해 관련 법령과 조문을 검색하고, 인용 근거와 추가 확인 항목을 함께 반환합니다.

제한된 법령 범위에서 RAG/Agent 기반 조사 흐름, 기준일 검색, 조문 이력 비교, 검색 로그와 에이전트 추적 기능을 제공합니다.

## 구현 범위

- Spring Boot 3.3, Java 17
- H2 + JPA 기반 법령/조문 저장
- Query Analyzer, Retrieval, Evidence Validator, Answer Writer, Critic, Search Log Agent 흐름
- OpenRouter LLM 연동
- OpenRouter embedding 연동
- Keyword/vector hybrid retrieval, 법령명/조문번호/제목/본문 기반 lexical ranking, reranker 기반 후보 재정렬
- 검색 점수 기반 evidence gate와 약한 근거의 `LOW_CONFIDENCE` 전환
- LLM 응답 품질 게이트와 1회 보정 재시도
- 법령 시행일/개정일 메타데이터 질의와 조문 개정비교 질의의 결정적 응답
- 법령 목록 페이징, 현행 조문 수, 개정 회차 수 조회
- 검색 로그와 agent trace 저장
- 검색 로그 requestId와 agent trace requestId를 연결해 질문 처리 단계 추적
- Ask 응답의 `diagnostics.requestId`로 사용자 결과, 검색 로그, agent trace 연결
- 질문 전문을 남기지 않는 `event=ask_complete` 운영 로그
- 요청 본문과 query string을 남기지 않는 `event=http_request` HTTP 요청 로그
- 수집 run의 성공/부분 실패/실패 상태 기록
- 사용자 화면에서 confidence 수치와 권위적으로 보이는 검증 문구를 노출하지 않음
- 사용자 화면의 관련 법령은 내부 관련도 점수를 숫자로 노출하지 않고, 검색 과정은 한국어 라벨로 표시
- Swagger/OpenAPI 문서
- 시나리오 하네스 기반 회귀 검증
- 무역안보/방산/기술보호 도메인 질문 일부를 Spring seed corpus에 맞게 recall 검증

## 아키텍처

```text
Client
  -> AskController
  -> AskOrchestratorService
  -> QueryAnalyzerAgent
  -> RetrievalAgent
       -> keyword search
       -> embedding
       -> vector search
       -> reranker
  -> EvidenceValidatorAgent
  -> AnswerWriterAgent
  -> CriticAgent
  -> SearchLogAgent
  -> AskResponse
```

정상 답변은 반드시 `citedArticles`를 포함합니다. 근거가 부족한 질문은 `INSUFFICIENT_INFO`로 반환하며, 이 경우 불필요한 embedding/vector 검색을 수행하지 않습니다.
검색된 조문이 있더라도 관련성 점수가 낮으면 답변을 `LOW_CONFIDENCE`로 제한하고, `diagnostics.retrievalStats.weakEvidence`와 `evidenceTopScoreBp`로 근거 품질을 남깁니다.
응답의 `diagnostics.requestId`는 관리자 검색 로그와 agent trace의 requestId와 동일해, 화면에 표시된 답변이 어떤 agent 단계로 처리됐는지 추적할 수 있습니다.
요청 완료 시 애플리케이션 로그에는 `event=ask_complete payload={...}` 형식의 JSON payload가 남습니다. 이 로그는 질문 전문을 포함하지 않고 `questionHash` 앞 12자리, 질문 길이, 상태, citation 수, 후보 수, latency, retrieval stats만 기록합니다.
HTTP 요청 단위 로그는 `event=http_request payload={...}` 형식으로 method, path, status, elapsedMs만 기록합니다. query string과 request body는 남기지 않아 질문 원문이나 API key가 운영 로그에 섞이지 않도록 했습니다.
검색 후보는 keyword/vector 결과를 병합한 뒤 법령명, 조문번호, 조문 제목, 본문 토큰 일치도와 토큰 빈도를 반영해 lexical score를 계산합니다. keyword 순위와 vector 순위는 reciprocal rank fusion 방식으로 보정하고, 이후 설정된 reranker가 있으면 최종 순서를 다시 조정합니다.
질문에 포함된 법령명은 공백 차이와 일부 약칭을 정규화해 canonical 법령명 검색어로 확장합니다. 예를 들어 `대외 무역법`, `산업기술보호법`, `외환거래법` 같은 표현도 관련 법령명 검색으로 연결합니다.
기준일 검색은 조문의 `effectiveFrom <= asOf <= effectiveTo` 조건으로 필터링합니다. 로컬 수집 시 이전 조문 종료일을 다음 시행일의 전날로 저장하므로 `effectiveTo`는 포함되는 종료일입니다. 기준일이 지정된 경우 시행일과 종료일이 모두 없는 legacy 조문은 정확성 우선으로 제외합니다. 요청한 기준일은 응답의 `effectiveBasis.asOf`에도 남겨 답변, 검색 조건, 화면 표시 기준을 맞춥니다. `effectiveBasis.sourcePath`에는 해당 스냅샷의 공개 법령 원문 또는 로컬 수집 경로를 함께 제공합니다.
Ask 응답뿐 아니라 법령 상세와 조문 상세 응답도 `effectiveBasis`를 포함해 상세 조회 결과의 스냅샷 버전, 색인 시각, 원문 출처를 추적할 수 있습니다.
Ask 요청은 원본의 strict schema 정책에 맞춰 `question`, `asOf` 외 알 수 없는 JSON 필드를 거부합니다. 공백 질문, 4000자 초과 질문, 잘못된 날짜 형식은 모두 `400 Bad Request`로 처리합니다. 기준일 필드는 원본 `/api/v1` 클라이언트 호환을 위해 `as_of`도 허용합니다. `/api/...`는 Spring UI용 camelCase 응답을 유지하고, `/api/v1/...`는 원본 FastAPI 계약에 맞춰 `candidate_laws`, `cited_articles`, `effective_basis`, `retrieval_stats`, `law_id`, `article_id`, `index_status` 같은 snake_case 응답을 반환합니다.
LLM 응답이 영어 중심 문장, 데모/샘플 문구, 단정적인 법률 판단 표현을 포함하면 한 번 보정 재시도를 수행하고, 재시도 후에도 기준을 만족하지 못하면 안전한 한국어 fallback 답변으로 전환합니다.
OpenRouter chat 응답은 JSON object 모드로 요청하며, fenced JSON이나 앞뒤 설명이 섞여도 첫 JSON object를 추출한 뒤 답변 품질 게이트를 적용합니다. JSON object를 추출할 수 없는 응답은 원문을 예외 메시지에 담지 않고 포맷 오류로 처리하며, AnswerWriter가 한 번만 재요청합니다. 두 번째도 JSON으로 파싱되지 않으면 성공 답변으로 위장하지 않고 `FAILED` 경로로 넘깁니다.
최종 `CriticAgent`는 reasoning과 follow-up question 모두에 대해 같은 사용자 노출 문구 기준을 다시 확인합니다.
색인 데이터가 없거나 질문 분석, 검색, 근거 확인, 답변 생성 중 예외가 발생하면 `FAILED` 응답으로 전환하고 같은 `requestId`로 검색 로그와 failed agent trace를 남깁니다. 인용 누락이나 응답 품질 기준 위반도 `FAILED`로 강등합니다. `diagnostics.retrievalStats.hydrated`는 답변 합성기에 실제 조문 본문으로 전달된 조문 수이며, 이 값이 있는데 `LOW_CONFIDENCE` 응답의 citation이 비는 조합은 DTO invariant에서 거부합니다. API 응답의 `errorMessage`에는 provider 예외 원문 대신 `no_snapshot`, `retrieval_failed`, `missing_citation`, `response_quality_failed` 같은 안전한 실패 코드만 제공합니다.

## 실행

Windows PowerShell 기준:

환경값 사전 점검:

```powershell
cd <project-root>
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1
```

외부 provider까지 확인하려는 실행 전에는 strict mode를 사용할 수 있습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1 -Strict
```

`preflight.ps1`는 `.env`의 provider 조합, 필수 키 존재 여부, 숫자 설정, 감사/민감정보 플래그를 확인합니다. API key 값은 출력하지 않습니다.
`SERVER_PORT`도 `.env` 기준으로 확인하므로, 기본 포트가 아닌 값으로 실행할 때도 사전 점검과 런타임 검증 URL이 같은 포트를 사용합니다.

```powershell
cd <project-root>
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 start
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 status
```

중지:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 stop
```

재시작:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 restart
```

`restart`는 pid 파일과 현재 포트 리스닝 PID만 대상으로 종료를 요청하고, 포트 해제를 짧게 기다린 뒤 바로 반환합니다. 단순 동작 확인은 서버를 재시작하지 말고 `verify-runtime.ps1`을 사용합니다.
`server.ps1`, `verify-runtime.ps1`, `run-scenarios.ps1`는 `-BaseUrl`을 직접 주지 않으면 환경 변수 `SERVER_PORT`, 그 다음 `.env`의 `SERVER_PORT`, 마지막으로 `8080` 순서로 포트를 결정합니다.

로그:

```text
target/bootrun.out.log
target/bootrun.err.log
```

## 환경 변수

`.env`는 Git에 포함하지 않습니다.
새 환경을 만들 때는 `.env.example`을 기준으로 `.env`를 작성합니다.

```properties
LLM_PROVIDER=openrouter
EMBEDDING_PROVIDER=openrouter
RERANKER_PROVIDER=mock
VECTOR_PROVIDER=inmemory
HTTP_TIMEOUT_SECONDS=30
QDRANT_BASE_URL=http://localhost:6333
QDRANT_API_KEY=
RERANKER_BASE_URL=https://api.cohere.com
RERANKER_API_KEY=
RERANKER_MODEL=rerank-v3.5
OPENROUTER_API_KEY=
LLM_API_KEY=
EMBEDDING_API_KEY=
LLM_MODEL=[model-name]
EMBEDDING_MODEL=[embedding-model-name]
EMBEDDING_DIMENSIONS=1024
EMBEDDING_MAX_CHARS=4000
EMBEDDING_BATCH_SIZE=32
QDRANT_PORT=6333
ADMIN_REINDEX_ENABLED=false
LAW_SOURCE_DIR=<local-legal-data-path>
LAW_SOURCE_REPO_URL=https://example.com/legal-data.git
LAW_SOURCE_BRANCH=main
```

`OPENROUTER_API_KEY` 하나만 설정해도 LLM과 embedding provider가 함께 사용할 수 있습니다. 공급자별 키를 분리하고 싶을 때만 `LLM_API_KEY` 또는 `EMBEDDING_API_KEY`를 별도로 설정합니다.
Vector store는 기본값으로 in-memory 구현을 사용합니다. `VECTOR_PROVIDER=qdrant`로 바꾸면 Qdrant HTTP API를 통해 collection 생성, point upsert, vector search를 수행합니다.
`HTTP_TIMEOUT_SECONDS`는 OpenRouter, Cohere, Qdrant 등 `RestClient` 기반 외부 호출의 connect/read timeout에 적용됩니다. 별도 설정이 없으면 `LLM_TIMEOUT_SECONDS` 값을 사용합니다.
OpenRouter embedding 응답은 `EMBEDDING_DIMENSIONS`와 실제 vector 차원이 일치하는지 검증합니다. 장문 조문은 `EMBEDDING_MAX_CHARS` 기준으로 앞부분을 잘라 embedding 요청을 보내며, OpenRouter가 빈 `data`를 반환하면 실패로 처리합니다.
Vector reindex는 `EMBEDDING_BATCH_SIZE` 기준으로 조문을 나눠 색인합니다. 배치 색인이 실패하면 해당 배치만 조문 단위로 다시 시도하고, 끝까지 실패한 조문 id는 재색인 응답과 수집 이력에 부분 실패로 남깁니다.
Reranker는 기본값으로 mock 구현을 사용합니다. `RERANKER_PROVIDER=cohere`로 바꾸면 Cohere `/v2/rerank` API를 통해 keyword/vector 후보를 재정렬합니다.
로컬 법령 Markdown을 반복 수집하면 같은 법령/조문 번호 기준으로 이전 현행 조문을 종료하고 새 조문 이력으로 연결합니다.
조문 변경 여부는 정규화된 본문 hash로 판단합니다. BOM, OS별 줄바꿈, 줄 끝 공백, 연속 빈 줄 차이는 같은 본문으로 처리하고, 의미 있는 내부 공백과 실제 문구 차이는 다른 본문으로 처리합니다.
Markdown 수집 중 같은 법령 안에서 `제N조` 헤더가 반복되면, 뒤의 조문 번호를 `제N조의2`, `제N조의3`처럼 비어 있는 접미사로 보정해 중복 저장 충돌을 방지합니다.
법령구분은 `법률`, `대통령령/시행령`, `총리령/시행규칙/*부령`을 지원하며, 범위 밖 값은 수집에서 제외합니다.
`LAW_SOURCE_REPO_URL`과 `LAW_SOURCE_DIR`를 설정하면 관리자 API 또는 관리 화면에서 Git 저장소를 동기화한 뒤 수집과 재색인을 이어서 실행할 수 있습니다.
동기화는 이전 commit이 원격 브랜치의 조상인지 확인하고 fast-forward pull만 허용합니다.
관리자 상태 API는 전체 조문 수와 vector index에 올라간 조문 수를 함께 반환하며, 두 값이 다르면 `indexStatus=stale`로 표시해 재색인이 필요한 상태를 드러냅니다.

## 로컬 인프라

기본 테스트와 빠른 실행은 H2와 in-memory vector store로 동작합니다. PostgreSQL과 Qdrant를 사용하려면 다음처럼 로컬 인프라를 올립니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\infra.ps1 up
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\infra.ps1 status
```

이후 `.env`에서 다음 값을 활성화합니다.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/law_research
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=law_research
SPRING_DATASOURCE_PASSWORD=change_me
SPRING_JPA_HIBERNATE_DDL_AUTO=update
VECTOR_PROVIDER=qdrant
QDRANT_BASE_URL=http://localhost:6333
```

중지:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\infra.ps1 down
```

## 주요 API

기존 UI용 `/api/...` 경로와 원본 프로젝트 호환용 `/api/v1/...` 경로를 함께 지원합니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Root page: `GET /`
- Admin page: `GET /admin.html`
- Health: `GET /api/health`, `GET /api/v1/health` returns DB/index checks
- Liveness: `GET /healthz`
- Ask: `POST /api/ask`
- Laws: `GET /api/laws?q={keyword}&page=1&size=20`
- Law Detail: `GET /api/laws/{id}`
- Article: `GET /api/articles/{id}`
- Admin Status: `GET /api/admin/status`
- Source Sync: `POST /api/admin/sync-source`
- Local Ingest: `POST /api/admin/ingest-local`
- Search Logs: `GET /api/admin/search-logs`
- Agent Traces: `GET /api/admin/agent-traces`
- Agent Traces By Request: `GET /api/admin/agent-traces?requestId={requestId}`
- Law Revisions: `GET /api/laws/{id}/revisions`
- Reindex: `POST /api/admin/reindex`
- Ingestion Runs: `GET /api/admin/ingestion-runs`
- Provider Smoke Test: `POST /api/admin/provider-smoke-test`

Manual reindex is disabled by default for operator safety. Set `ADMIN_REINDEX_ENABLED=true` and restart before using `POST /api/admin/reindex`; `GET /api/admin/status` exposes `reindexEnabled` and `recentFailures` for the admin UI.
`POST /api/admin/reindex` remains synchronous for the Spring admin UI. `POST /api/v1/admin/reindex` follows the original service behavior more closely by returning `202 Accepted` with `status=accepted` after scheduling a background reindex run.

Provider smoke test는 설정된 LLM, embedding, reranker provider를 각각 1회 호출해 API key와 모델 설정을 확인합니다.
관리 화면의 `Provider 점검` 영역에서도 같은 점검을 실행할 수 있습니다.

일부 provider가 실패해도 smoke API는 전체 요청을 실패시키지 않고 해당 provider의 `failed` 상태와 안전한 오류 코드만 반환합니다.

질문 예시:

```json
{
  "question": "해외 업체에 기술자료를 제공해도 되나요?",
  "asOf": null
}
```

## 검증

최종 포트폴리오 검증은 아래 순서가 기준입니다. 서버 시작/재시작은 사용자가 직접 수행하고, 검증 스크립트는 실행 중인 서버만 확인합니다.

1. 서버 없이 설정과 준비물을 먼저 확인합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders
```

2. 사용자가 서버를 직접 시작하거나 재시작합니다.

3. 실행 중인 서버에 대해 최종 검증을 수행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1
```

`verify-final.ps1`는 readiness, runtime provider smoke, scenario harness, evidence report export, completion evidence gate를 순서대로 실행합니다. 이 스크립트는 서버를 시작, 중지, 재시작하지 않습니다.

최종 검증 산출물:

```text
target/readiness-summary.json
target/runtime-evidence/runtime-summary.json
target/scenario-responses/scenario-summary.json
target/evidence-report.md
```

`VECTOR_PROVIDER=inmemory` 또는 `RERANKER_PROVIDER=mock`이면 검증은 가능하지만 Qdrant/Cohere 실연동 증거는 남지 않습니다. 최종 포트폴리오에서 해당 provider까지 강조하려면 `.env`에서 `VECTOR_PROVIDER=qdrant`, `RERANKER_PROVIDER=cohere`로 전환한 뒤 다음처럼 readiness를 더 엄격하게 확인합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders -RequireQdrant -RequireCohere
```

서버 없이 실행 가능한 로컬 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

이 명령은 `preflight.ps1`, PowerShell 스크립트 파서 검사, 정적 JavaScript 문법 검사, 증분 컴파일을 끈 Maven 테스트를 순서대로 실행합니다. 실행 중인 서버가 필요한 `/api` 런타임 검증은 포함하지 않습니다.

단위/통합 테스트:

```powershell
mvn.cmd "-Dmaven.compiler.useIncrementalCompilation=false" test
```

시나리오 하네스:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1
```

이 스크립트는 각 응답을 `target/scenario-responses/`에 UTF-8 JSON으로 저장하고, 상태값, citation 개수, vector hit, 기대 법령/조문, 금지 문구를 함께 검증합니다.
전체 결과는 `target/scenario-responses/scenario-summary.json`에 요약되며, status, citation 수, vector hit, requestId, 검색 로그 연결 여부, agent trace 순서 검증 여부를 확인할 수 있습니다.

이미 실행 중인 서버에 대한 런타임 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1
```

이 검증은 서버를 재시작하지 않고 root/admin 화면, `/healthz`, `/api/v1/health`, 관리자 상태, 법령 목록, `/api/ask`, `/api/v1/ask`, `/api/v1/admin/status`를 확인합니다. `/api/...`는 Spring UI용 camelCase 계약을, `/api/v1/...`는 원본 호환 snake_case 계약을 유지하는지도 함께 검증합니다.
검증에 사용한 주요 JSON 응답과 요약은 `target/runtime-evidence/`에 저장됩니다. `runtime-summary.json`에는 snapshot, 색인 상태, ask/v1 ask requestId, citation 개수, 선택 옵션 사용 여부가 기록됩니다.

런타임 검증과 시나리오 검증 산출물을 하나의 Markdown 리포트로 묶으려면 다음 명령을 사용합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1
```

결과는 `target/evidence-report.md`에 저장됩니다. 이 리포트는 runtime smoke 결과, scenario pass/fail, requestId 연결, 검색 로그 확인, agent trace 확인 결과를 요약하며 API key나 질문 원문 전체는 포함하지 않습니다.

실제 provider까지 함께 점검하려면 다음 옵션을 사용합니다. 이 명령은 설정된 LLM, embedding, reranker API를 호출합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke
```

`POST /api/v1/admin/reindex`의 `202 Accepted` 백그라운드 재색인 계약까지 확인하려면 다음 옵션을 사용합니다. 실제 재색인과 embedding 호출이 발생할 수 있으므로 `ADMIN_REINDEX_ENABLED=true`일 때만 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeReindexContract
```

현재 확인된 provider 상태는 OpenRouter LLM, OpenRouter embedding, mock reranker 조합입니다. Cohere reranker를 사용하려면 `RERANKER_PROVIDER=cohere`와 `RERANKER_API_KEY`를 설정한 뒤 위 명령을 다시 실행합니다.

현재 하네스는 다음을 검증합니다.

- 기술자료 해외 제공 질문이 `OK`와 citation을 반환
- 전략물자 수출 질문이 `OK`와 citation을 반환
- 한글 질문도 검색 가능한 query로 정규화
- 불충분 질문은 `INSUFFICIENT_INFO` 반환
- 정상 질문은 `vectorHits >= 1`
- 불충분 질문은 `vectorHits = 0`
- LLM이 영어 또는 데모 문구를 반환하면 보정 재시도 또는 fallback으로 한국어 응답 유지
- 법령 시행일 질문은 법령 메타데이터와 citation을 함께 반환
- 개정비교 질문은 비교 대상 조문과 이전 회차를 기준으로 답변
- `개정일` 질문과 `개정 내용/바뀐 점` 질문을 서로 다른 질문 유형으로 라우팅
- 로컬 법령 수집 중 일부 파일 실패 시 `PARTIAL_FAILED` 상태와 실패 요약 기록
- 정적 UI에서 confidence, 신뢰도, 검증됨 문구가 노출되지 않는지 확인
- 약한 검색 근거가 `OK`로 승격되지 않고 `LOW_CONFIDENCE`로 제한되는지 확인
- 탱크/방산물자 수출, 전략물자 판정, 관세법상 수출신고 질문이 기대 조문을 citation으로 반환

## 데이터 안전 원칙

- 회사 내부 코드, 데이터, 계정, 업무 문서 사용 금지
- API key는 `.env`로만 관리
- 답변은 인용 조문 근거가 있을 때만 `OK`
- 모르는 내용은 생성하지 않고 follow-up question으로 전환
- 답변에는 데모/샘플/미니 프로젝트 문구를 노출하지 않음
- 검색 로그에는 질문 원문 전체를 저장하지 않고, requestId/hash/길이/민감 패턴 치환 preview와 기준일 `asOf`만 저장
- 운영 로그에는 질문 원문을 남기지 않고 `ask_complete` JSON payload의 hash/길이/latency/retrieval stats만 저장
- HTTP 요청 로그에는 query string과 request body를 남기지 않고 method/path/status/elapsedMs만 저장

## 면접 설명 포인트

- 기존 Flask 기반 법령 조사 흐름을 Spring Boot의 Controller-Service-Repository 구조로 재구성
- 생성형 AI 호출을 비즈니스 로직에 직접 섞지 않고 provider interface 뒤로 분리
- RAG 품질을 citation invariant, scenario harness, search log, agent trace, 안전한 완료 로그로 검증
- 전체 법령 DB 구축보다 제한된 도메인에서 agent orchestration, embedding retrieval, 감사 가능성을 설명하는 데 집중
