# Architecture

## Pipeline

```text
AskController
→ AskOrchestratorService
→ QueryAnalyzerAgent
→ RetrievalAgent
→ EvidenceValidatorAgent
→ AnswerWriterAgent
→ CriticAgent
→ SearchLogAgent
```

## 계층

- API: HTTP request/response 처리
- Service: agent orchestration과 업무 흐름
- Repository: JPA 기반 DB 접근
- Domain: Law, Article, SnapshotVersion, SearchLog, AgentTrace
- DTO: API 계약

## Runtime Profiles

기본 실행은 H2 in-memory DB와 in-memory vector store를 사용한다. 운영형 로컬 검증에서는 `docker-compose.yml`로 PostgreSQL과 Qdrant를 띄우고, `.env`에서 datasource와 vector provider를 전환한다.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/law_research
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
VECTOR_PROVIDER=qdrant
QDRANT_BASE_URL=http://localhost:6333
```

## 현재 검색 방식

검색은 H2/JPA 기반 keyword search와 embedding 기반 vector search를 병합한 뒤 lexical ranking, reciprocal rank fusion, `RerankerClient`로 최종 후보 순서를 재정렬한다. `VectorSearchClient`는 기본 in-memory 구현과 Qdrant HTTP 구현을 제공한다.

lexical ranking은 법령명, 조문번호, 조문 제목, 본문 토큰 일치도와 토큰 빈도를 가중치로 계산한다. keyword 후보와 vector 후보에는 각각의 순위를 기준으로 reciprocal rank score를 더해 한쪽 검색 경로의 원점수만으로 결과가 고정되지 않도록 했다. 예를 들어 `관세법 제241조 수출신고`처럼 법령명과 조문번호가 함께 들어온 질문은 일반적인 수출통제 조문보다 해당 법령/조문 후보가 먼저 올라오도록 점수를 보정한다.

질문 분석 단계는 `대외 무역법`처럼 띄어쓰기가 다른 법령명과 `산업기술보호법`, `외환거래법` 같은 실무 약칭을 canonical 법령명 검색어로 확장한다. 검색 점수화도 공백과 일부 구분자를 제거한 compact 문자열을 함께 비교해 법령명/조문번호 명시 질문의 recall을 보강한다.

기준일 검색은 keyword search와 vector search 후처리 모두 같은 정책을 적용한다. `asOf`가 없으면 현재 조문(`effectiveTo is null`)을 검색하고, `asOf`가 있으면 `effectiveFrom <= asOf <= effectiveTo` 범위에 있는 조문만 사용한다. 이 Spring 구현은 로컬 수집 시 이전 조문의 `effectiveTo`를 다음 시행일의 전날로 저장하므로 종료일을 포함한다. 시행일/종료일이 모두 비어 있는 legacy 조문은 기준일 지정 검색에서는 제외한다. 요청 기준일은 `EffectiveBasisDto.asOf`로 응답에 포함해 검색 조건과 화면 표시 기준을 추적할 수 있게 한다. 응답 기준 정보에는 `snapshotVersion`, `indexedAt`, `sourcePath`도 포함해 어떤 스냅샷과 법령 원문 출처를 기준으로 답변했는지 API에서 확인할 수 있게 한다.

## Source Synchronization

법령 원문은 로컬 Markdown 디렉터리에서 수집한다. 관리 기능은 Git 저장소 URL, 로컬 저장 경로, 브랜치를 입력받아 다음 흐름을 수행한다.

```text
SourceSyncService
→ git clone 또는 git fetch/checkout/pull --ff-only
→ HEAD commit hash 기록
→ SyncState 갱신
→ LocalLawIngestionService
→ MarkdownLawParser
→ SnapshotVersion/Law/Article 저장
→ VectorIndexService
```

이미 Git 저장소인 경로는 이전 동기화 commit이 원격 브랜치의 조상인지 확인한 뒤 fast-forward pull만 허용한다. 조상이 아니면 이력 충돌로 기록하고 동기화를 중단한다. Git 저장소가 아닌 비어 있지 않은 경로는 거부한다. 로컬 파일 변경을 덮어쓰는 강제 reset은 수행하지 않는다.

`MarkdownLawParser`는 `##### 제N조 (제목)` 형식의 조문을 분리한다. 같은 법령 파일에서 bare 조문 번호가 반복되면 이미 사용된 번호를 피해 `제N조의K`로 보정해 article number 중복을 방지한다. 명시적으로 들어온 `제N조의K` 번호는 유지한다.
법령구분은 `법률`, `대통령령/시행령`, `총리령/시행규칙/*부령`을 내부 `LawType`으로 매핑한다. 현재 범위를 벗어나는 법령구분은 명시적으로 skip한다.
`Article.contentHash`는 BOM, CRLF/LF/CR, 줄 끝 공백, 연속 빈 줄을 정규화한 뒤 SHA-256으로 계산한다. 이 hash는 반복 수집 시 `amendmentKind`를 `유지` 또는 `개정`으로 판정하는 기준이다.

## Provider Abstraction

LLM, embedding, vector, reranker는 다음 인터페이스 뒤로 분리한다.

```text
ChatModelClient
EmbeddingClient
VectorSearchClient
RerankerClient
```

현재 구현:

- `MockChatModelClient`
- `MockEmbeddingClient`
- `InMemoryVectorSearchClient`
- `QdrantVectorSearchClient`
- `MockRerankerClient`
- `CohereRerankerClient`

service 계층은 provider 구현체가 아니라 interface에만 의존한다.

`RestClientTimeoutConfig`는 `HTTP_TIMEOUT_SECONDS`를 모든 `RestClient` 기반 외부 provider 호출의 connect/read timeout으로 적용한다. 값이 없으면 `LLM_TIMEOUT_SECONDS`를 사용해 OpenRouter 호출이 무기한 대기하지 않도록 한다.

OpenRouter embedding provider는 요청 전 입력을 `EMBEDDING_MAX_CHARS` 기준으로 제한하고, 응답 vector 차원이 `EMBEDDING_DIMENSIONS`와 다르면 실패시킨다. OpenRouter가 200 응답과 함께 빈 `data`를 반환하는 경우도 정상 색인으로 보지 않고 예외로 처리한다.

`VectorIndexService`는 `EMBEDDING_BATCH_SIZE` 기준으로 조문을 배치 색인한다. 배치 실패 시 같은 배치를 단건으로 재시도해 한 조문 또는 일시적인 provider 응답 문제가 전체 색인을 중단시키지 않도록 하고, 끝까지 실패한 조문 id는 부분 실패 결과로 수집 이력에 기록한다.

## Vector Provider

```properties
VECTOR_PROVIDER=inmemory
```

운영형 vector store를 사용할 때는 다음처럼 Qdrant provider로 전환한다.

```properties
VECTOR_PROVIDER=qdrant
QDRANT_BASE_URL=http://localhost:6333
QDRANT_API_KEY=
QDRANT_DISTANCE=Cosine
```

Qdrant provider는 collection이 없으면 생성하고, 조문 vector를 point로 upsert한 뒤 `/points/search`로 검색한다.

## Reranker Provider

```properties
RERANKER_PROVIDER=mock
```

외부 reranker를 사용할 때는 다음처럼 Cohere provider로 전환한다.

```properties
RERANKER_PROVIDER=cohere
RERANKER_BASE_URL=https://api.cohere.com
RERANKER_API_KEY=
RERANKER_MODEL=rerank-v3.5
```

Cohere provider는 `/v2/rerank`에 query와 후보 조문 텍스트를 전달하고, 응답의 `index`와 `relevance_score`를 최종 `RetrievalHit` 순서와 점수로 반영한다.

## Observability

관리자 API:

```text
GET /api/admin/status
GET /api/admin/search-logs
GET /api/admin/agent-traces
POST /api/admin/reindex
POST /api/admin/sync-source
POST /api/admin/ingest-local
GET /api/admin/ingestion-runs
```

`GET /api/admin/status`는 전체 조문 수와 vector index에 올라간 조문 수를 함께 반환한다. 완료된 스냅샷이 없으면 `missing`, 조문 수와 색인 수가 다르면 `stale`, 두 값이 일치하면 `healthy`로 표시해 수집과 색인 사이의 불일치를 운영자가 바로 확인할 수 있게 한다.

`SearchLog`는 질문 전문 대신 requestId, hash, 길이, 질문 유형, 상태, citation 수, latency를 저장한다. 화면 표시용 `questionPreview`는 민감 패턴을 치환한 뒤 원문 전체가 되지 않도록 잘라 저장한다.

`AgentTrace`는 requestId 기준으로 agent step, input/output summary, status, latency를 저장한다. Ask 응답의 `diagnostics.requestId`, `SearchLog.requestId`, `AgentTrace.requestId`는 같은 값이며, 관리자 API는 `GET /api/admin/agent-traces?requestId=...` 필터를 지원해 특정 답변의 처리 단계를 재구성할 수 있다. 관리 화면에서는 검색 로그의 requestId를 선택하거나 직접 입력해 해당 요청의 trace만 확인할 수 있다.

`AskAuditLogger`는 요청 완료 시 `event=ask_complete payload={...}` 형식의 JSON payload 로그를 남긴다. 질문 전문은 기록하지 않고 `questionHash` 앞 12자리, 질문 길이, 상태, snapshot version, 기준일, citation 수, 후보 수, latency, retrieval stats만 남겨 운영 분석과 장애 재현에 필요한 최소 정보만 제공한다.

`HttpRequestAuditFilter`는 모든 HTTP 요청 완료 시 `event=http_request payload={...}` 형식의 JSON payload 로그를 남긴다. payload는 method, path, status, elapsedMs만 포함하며 query string과 request body는 기록하지 않는다.

`SyncState`는 마지막 동기화 commit, 동기화 시각, 이력 충돌 감지 시각을 저장한다.
