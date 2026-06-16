# API Contract

All `/api/...` endpoints are also exposed under `/api/v1/...` for compatibility with the original `law_research_assistant` API shape. `/api/...` keeps the Spring UI camelCase response contract, while `/api/v1/...` returns original-style snake_case field names. Examples include `candidate_laws`, `cited_articles`, `follow_up_questions`, `effective_basis`, `retrieval_stats`, `law_id`, `article_id`, `index_status`, `recent_failures`, and `reindex_enabled`.

## POST /api/ask

Request:

```json
{
  "question": "해외 업체에 기술자료를 제공해도 되나요?",
  "asOf": null
}
```

`asOf`가 `null`이면 현재 유효 조문을 기준으로 검색합니다. 날짜가 지정되면 `effectiveFrom <= asOf <= effectiveTo` 범위의 조문만 검색하며, 시행일/종료일이 모두 없는 legacy 조문은 기준일 비교가 불가능하므로 제외합니다.
`question`은 공백만 있으면 안 되며 최대 4000자까지 허용합니다. 요청 JSON은 엄격하게 해석하므로 `question`, `asOf` 외 알 수 없는 필드나 잘못된 날짜 형식은 `400 Bad Request`와 `{"error":"invalid request"}`로 거부합니다. 원본 `/api/v1` 클라이언트 호환을 위해 기준일 필드는 `as_of`도 alias로 허용합니다.

Response:

```json
{
  "status": "OK",
  "interpretation": {
    "action": "제공",
    "object": "기술자료",
    "domainCandidates": ["수출통제", "기술이전"],
    "uncertainties": ["대상 품목 유형", "제공받는 기관", "목적지 국가", "기준 시점"],
    "generatedQueries": ["제공 기술자료", "수출통제 기술자료"],
    "questionType": "CONFIRMATORY"
  },
  "candidateLaws": [
    {
      "lawId": 1,
      "title": "대외무역법",
      "lawType": "ACT",
      "relevanceReason": "키워드 일치",
      "score": 0.86
    }
  ],
  "citedArticles": [
    {
      "articleId": 1,
      "lawTitle": "대외무역법",
      "articleNumber": "제19조",
      "articleTitle": "전략물자의 고시 및 수출허가",
      "effectiveFrom": "2026-01-01",
      "effectiveTo": null,
      "amendmentKind": "개정",
      "historicalEntries": []
    }
  ],
  "reasoning": "인용 조문을 기준으로 제공 대상, 목적지 국가, 최종 사용자를 추가 확인해야 합니다.",
  "followUpQuestions": ["제공받는 기관 또는 최종 사용자를 확인해 주세요."],
  "effectiveBasis": {
    "snapshotVersion": "law-domain-2026-001",
    "indexedAt": "2026-06-16T00:00:00",
    "sourcePath": "public-reference-data",
    "asOf": null
  },
  "confidence": 0.72,
  "diagnostics": {
    "requestId": "2f7bb8a8-2ed6-4f42-9420-4d090fdc91a8",
    "generatedQueries": ["제공 기술자료", "수출통제 기술자료"],
    "retrievalStats": {
      "retrieved": 2,
      "hydrated": 2,
      "cited": 2,
      "keywordHits": 2,
      "vectorHits": 5,
      "mergedHits": 5,
      "evidenceTopScoreBp": 940,
      "weakEvidence": 0
    },
    "latencyMs": {
      "analyze": 1,
      "retrieve": 12,
      "synthesize": 35
    }
  },
  "disclaimer": "이 답변은 인용 조문에 근거한 법령 조사 보조 결과이며, 최종 판단 전 담당 검토가 필요합니다.",
  "errorMessage": null
}
```

`effectiveBasis.sourcePath`는 해당 스냅샷을 만든 공개 법령 원문 또는 로컬 수집 경로를 나타냅니다.
`effectiveBasis.asOf`는 사용자가 요청한 기준일입니다. 값이 `null`이면 현재 유효 조문 기준입니다.
`diagnostics.requestId`는 응답, 검색 로그, agent trace를 연결하는 감사 키입니다. `hydrated`는 답변 합성 단계에 실제 조문 본문으로 전달된 조문 수입니다. `weakEvidence=1`이면 검색된 조문은 있으나 관련성 점수가 낮다는 뜻이며, 이 경우 답변은 `OK`가 아니라 `LOW_CONFIDENCE`로 제한됩니다. `evidenceTopScoreBp`는 최고 검색 점수를 0~1000 정수로 표현한 값입니다.
실패 응답은 `status="FAILED"`와 함께 `errorMessage`에 `no_snapshot`, `query_analysis_failed`, `retrieval_failed`, `evidence_validation_failed`, `answer_generation_failed`, `missing_citation`, `response_quality_failed` 중 하나를 반환합니다. provider 예외 원문이나 사용자 질문 전문은 API 응답에 노출하지 않습니다.

## POST /api/v1/ask

Original-compatible ask endpoint. Request validation is the same as `/api/ask`, including `as_of` alias support, but the response uses snake_case field names and lower-case enum values.

Response shape excerpt:

```json
{
  "status": "ok",
  "candidate_laws": [],
  "cited_articles": [],
  "follow_up_questions": [],
  "effective_basis": {
    "snapshot_version": "law-domain-2026-001",
    "indexed_at": "2026-06-16T00:00:00",
    "source_path": "public-reference-data",
    "as_of": null
  },
  "diagnostics": {
    "request_id": "2f7bb8a8-2ed6-4f42-9420-4d090fdc91a8",
    "generated_queries": [],
    "retrieval_stats": {},
    "latency_ms": {}
  },
  "error_message": null
}
```

## GET /api/admin/status

색인 상태, 데이터 건수, 마지막 법령 원문 동기화 상태를 반환합니다.
`indexStatus`는 완료된 스냅샷과 색인 조문 수를 기준으로 `healthy`, `missing`, `stale` 중 하나를 반환합니다. `stale`은 DB에 저장된 조문 수와 vector index에 올라간 조문 수가 달라 재색인이 필요한 상태입니다.

Response:

```json
{
  "lastSnapshotVersion": "law-domain-2026-001",
  "lastIndexedAt": "2026-06-16T00:00:00",
  "indexStatus": "healthy",
  "lawsCount": 8,
  "articlesCount": 21,
  "indexedArticlesCount": 21,
  "unindexedArticlesCount": 0,
  "searchLogCount": 12,
  "recentFailures": [],
  "reindexEnabled": false,
  "syncState": {
    "lastSyncedCommitSha": "abc123",
    "lastSyncAt": "2026-06-16T02:40:00",
    "lastForcePushDetectedAt": null
  }
}
```

## GET /api/health

`/api/health` and `/api/v1/health` return readiness-style checks for the database and retrieval index.

Response:

```json
{
  "status": "ok",
  "checks": {
    "db": "ok",
    "index": {
      "status": "healthy",
      "articles": 21,
      "indexedArticles": 21,
      "unindexedArticles": 0
    }
  }
}
```

`status` is `degraded` when the DB check fails or the article index is missing/stale.

## GET /healthz

Root liveness endpoint compatible with the original service shape.

```json
{
  "status": "ok"
}
```

## GET /api/articles/{id}/history

동일 법령의 동일 조문 번호에 대한 변경 이력을 반환합니다.

`REVISION_COMPARE` 질문의 `POST /api/ask` 응답은 비교 대상 인용 조문에 `historicalEntries`를 함께 포함할 수 있습니다. 이 필드는 사용자가 별도 이력 API를 호출하지 않아도 답변에 사용된 이전 회차 본문을 즉시 확인하기 위한 구조화 데이터입니다.

## GET /api/laws/{id}/revisions

법령 단위 개정 이력 그룹을 반환합니다. Spring 축소판에서는 조문에 저장된 `effectiveFrom`과 `amendmentKind`를 기준으로 회차를 묶습니다.

Response:

```json
{
  "lawId": 1,
  "lawTitle": "대외무역법",
  "revisions": [
    {
      "effectiveFrom": "2026-01-01",
      "amendmentKind": "개정",
      "articleCount": 1
    }
  ]
}
```

## GET /api/laws/{id}

법령 메타데이터와 현행 조문 목록을 반환합니다.

Response:

```json
{
  "lawId": 1,
  "slug": "foreign-trade-act",
  "title": "대외무역법",
  "lawType": "ACT",
  "lawNumber": "LAW-001",
  "snapshotVersion": "law-domain-2026-001",
  "effectiveBasis": {
    "snapshotVersion": "law-domain-2026-001",
    "indexedAt": "2026-06-16T00:00:00",
    "sourcePath": "public-reference-data",
    "asOf": null
  },
  "articles": [
    {
      "articleId": 1,
      "articleNumber": "제19조",
      "articleTitle": "전략물자의 고시 및 수출허가",
      "effectiveFrom": "2026-01-01",
      "effectiveTo": null,
      "effectiveBasis": {
        "snapshotVersion": "law-domain-2026-001",
        "indexedAt": "2026-06-16T00:00:00",
        "sourcePath": "public-reference-data",
        "asOf": null
      }
    }
  ]
}
```

법령 상세와 조문 상세 응답의 `effectiveBasis`는 해당 항목이 어느 스냅샷과 원문 출처에서 조회됐는지 추적하기 위한 값입니다.

## GET /api/laws

Query:

```text
q={keyword}&page=1&size=20
```

`keyword` 파라미터도 하위 호환용으로 지원합니다.

Response:

```json
{
  "total": 1,
  "page": 1,
  "size": 20,
  "items": [
    {
      "lawId": 1,
      "title": "대외무역법",
      "lawType": "LAW",
      "lawNumber": "LAW-001",
      "articleCount": 3,
      "revisionCount": 3
    }
  ]
}
```

## GET /api/articles/{id}/diff

Query:

```text
compareWith={previousArticleId}
```

두 조문 버전의 본문 해시와 내용을 비교합니다.

## POST /api/admin/reindex

현재 저장된 조문을 다시 임베딩하고 벡터 인덱스를 갱신합니다. 실행 결과는 ingestion run으로 기록됩니다.
배치 색인 실패 후 단건 재시도까지 실패한 조문이 있으면 `status`는 `PARTIAL_FAILED`가 되고, `failedArticles`와 `errorMessage`에 실패 요약이 포함됩니다.
This endpoint is disabled unless `ADMIN_REINDEX_ENABLED=true` is configured. When disabled, it returns `403 Forbidden` with an `error` message.
`/api/admin/reindex` keeps the Spring admin UI synchronous response contract. `/api/v1/admin/reindex` follows the original service shape more closely: it creates a `RUNNING` ingestion run, schedules the reindex job in the background, and immediately returns `202 Accepted`.

`/api/admin/reindex` response:

```json
{
  "ingestionRunId": 1,
  "status": "SUCCEEDED",
  "indexedArticles": 21,
  "failedArticles": 0,
  "errorMessage": null,
  "snapshotVersion": "law-domain-2026-001",
  "startedAt": "2026-06-16T06:00:00",
  "finishedAt": "2026-06-16T06:00:01"
}
```

`/api/v1/admin/reindex` response:

```json
{
  "status": "accepted",
  "message": "reindex scheduled",
  "ingestion_run_id": 1,
  "snapshot_version": "law-domain-2026-001",
  "started_at": "2026-06-16T06:00:00"
}
```

## POST /api/admin/ingest-local

로컬 Markdown 법령 파일을 파싱해 새 스냅샷으로 저장하고, 저장된 조문을 다시 색인합니다.
같은 법령과 조문 번호가 다시 수집되면 기존 현행 조문은 종료일이 설정되고, 새 조문은 `previousArticleId`로 이전 조문과 연결됩니다.
일부 파일 처리 또는 조문 색인에 실패하면 실행 상태는 `PARTIAL_FAILED`가 되고, 실패 요약은 응답과 ingestion run의 `errorMessage`에 기록됩니다.

Request:

```json
{
  "sourceDir": "C:\\dev\\legal-data",
  "snapshotPrefix": "law-local"
}
```

Response:

```json
{
  "ingestionRunId": 2,
  "status": "SUCCEEDED",
  "snapshotVersion": "law-local-20260616060000",
  "filesProcessed": 8,
  "filesFailed": 0,
  "lawsImported": 8,
  "articlesImported": 21,
  "indexedArticles": 21,
  "errorMessage": null,
  "startedAt": "2026-06-16T06:00:00",
  "finishedAt": "2026-06-16T06:00:02"
}
```

지원하는 Markdown 형식:

```markdown
---
제목: 대외무역법
법령구분: 법률
법령MST: LAW-001
공포일자: 2026-01-01
시행일자: 2026-01-01
---

##### 제19조 (전략물자의 고시 및 수출허가)
전략물자를 수출하려는 경우에는 허가 요건을 확인해야 한다.
```

같은 파일 안에서 상류 데이터 문제로 `제N조` 헤더가 반복되면 파서가 뒤의 중복 조문 번호를 `제N조의2`, `제N조의3`처럼 비어 있는 접미사로 보정합니다. 이미 `제N조의2`처럼 명시된 번호는 유지합니다.

지원하는 `법령구분` 값은 `법률`, `대통령령`, `시행령`, `총리령`, `시행규칙`, 그리고 `*부령` 패턴입니다. 그 외 `헌법`, `조약`, `중앙선거관리위원회규칙`처럼 현재 범위를 벗어나는 값은 수집 대상에서 제외합니다.

수집된 조문 본문의 `contentHash`는 BOM, 줄바꿈 형식, 줄 끝 공백, 연속 빈 줄을 정규화한 뒤 계산합니다. 포맷 차이만 있는 재수집은 같은 본문으로 보고, 실제 문구 차이가 있는 경우에만 hash가 달라집니다.

## POST /api/admin/sync-source

법령 Markdown 저장소를 로컬 경로로 동기화합니다. `ingestAfterSync=true`이면 동기화 후 즉시 수집과 재색인을 실행합니다.
이전에 동기화한 commit이 원격 브랜치의 조상이 아니면 이력 충돌로 판단해 중단하고 `syncState.lastForcePushDetectedAt`에 감지 시각을 기록합니다.

Request:

```json
{
  "repoUrl": "https://example.com/legal-data.git",
  "localDir": "C:\\dev\\legal-data",
  "branch": "main",
  "ingestAfterSync": true,
  "snapshotPrefix": "law-source"
}
```

Response:

```json
{
  "action": "PULLED",
  "repoUrl": "https://example.com/legal-data.git",
  "localDir": "C:\\dev\\legal-data",
  "branch": "main",
  "commitHash": "abc123",
  "ingestion": {
    "status": "SUCCEEDED",
    "lawsImported": 8,
    "articlesImported": 16,
    "indexedArticles": 16
  }
}
```

## GET /api/admin/ingestion-runs

최근 재색인 실행 이력을 반환합니다.
`status` 값은 `RUNNING`, `SUCCEEDED`, `PARTIAL_FAILED`, `FAILED` 중 하나입니다.

## GET /api/admin/search-logs

최근 검색 로그를 반환합니다. 질문 원문 전체는 저장하지 않고, `requestId`, `questionHash`, `questionLength`, 민감 패턴이 치환된 불완전 `questionPreview`를 제공합니다. 기준일 검색 요청이면 `asOf`에 요청 기준일을 저장하고, 현재 유효 조문 기준이면 `null`입니다.

`requestId`는 같은 질문 처리 과정에서 생성된 agent trace와 검색 로그를 연결하는 감사 키입니다.

## GET /api/admin/agent-traces

최근 agent trace를 반환합니다. `requestId` query parameter를 전달하면 특정 질문 처리 흐름만 필터링합니다.

```text
GET /api/admin/agent-traces?requestId={requestId}
```

## POST /api/admin/provider-smoke-test

설정된 LLM, embedding, reranker provider를 각각 1회 호출해 연결 상태를 확인합니다. 일부 provider가 실패해도 전체 요청은 200으로 반환하며, 실패한 provider는 `failed` 상태와 안전한 오류 코드로 표시합니다.

Response:

```json
{
  "llmProvider": "openrouter",
  "embeddingProvider": "openrouter",
  "rerankerProvider": "cohere",
  "llmStatus": "ok",
  "embeddingStatus": "ok",
  "rerankerStatus": "ok",
  "embeddingDimensions": 1024,
  "rerankedCount": 1,
  "topRerankedId": "a",
  "llmResult": {
    "status": "OK",
    "message": "provider ready"
  },
  "llmError": null,
  "embeddingError": null,
  "rerankerError": null
}
```

Partial failure example:

```json
{
  "llmProvider": "openrouter",
  "embeddingProvider": "openrouter",
  "rerankerProvider": "cohere",
  "llmStatus": "failed",
  "embeddingStatus": "ok",
  "rerankerStatus": "failed",
  "embeddingDimensions": 1024,
  "rerankedCount": 0,
  "topRerankedId": null,
  "llmResult": {},
  "llmError": "llm_response_format_failed",
  "embeddingError": null,
  "rerankerError": "reranker_provider_failed"
}
```
