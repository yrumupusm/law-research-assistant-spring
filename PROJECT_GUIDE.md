# Project Guide

## 핵심 원칙

- LLM은 법령을 직접 생성하지 않는다.
- `status=OK` 답변은 최소 1개 이상의 `citedArticle`을 포함해야 한다.
- 검색 또는 hydrate된 조문이 있는 `LOW_CONFIDENCE` 답변도 최소 1개 이상의 `citedArticle`을 포함해야 한다.
- 근거가 부족하면 결론을 내리지 않고 `INSUFFICIENT_INFO`와 follow-up question을 반환한다.
- 모든 답변은 `snapshotVersion`, `indexedAt`, `sourcePath`를 포함한다.
- 실패 응답은 provider 예외 원문 대신 `errorMessage`에 안전한 단계별 또는 품질 게이트별 실패 코드만 포함한다.
- Ask 요청은 `question`, `asOf` 외 알 수 없는 JSON 필드를 거부하고, 빈 질문/4000자 초과/잘못된 날짜를 400으로 처리한다.
- 실제 회사 데이터, 내부 URL, 계정 정보, API key는 사용하지 않는다.

## 작업 규칙

- Controller는 HTTP request/response만 담당한다.
- Service는 질문 분석, 검색, 답변 합성, 검증, 로그 저장 책임을 분리한다.
- LLM, embedding, vector search는 interface 뒤에 둔다.
- RetrievalAgent는 keyword/vector 후보 병합 후 reranker를 적용한다.
- provider URL, model name, API key는 환경 변수로 관리한다.
- 법령 원문은 Git 동기화 후 Markdown 수집 흐름으로 관리한다.
- 테스트는 status, citation, retrieval diagnostics를 함께 검증한다.

## 금지 사항

- `citedArticles`가 비어 있는데 `status=OK` 반환
- context에 없는 법령명이나 조문 생성
- 질문 원문이나 민감정보를 로그에 그대로 저장
- API key 하드코딩
- 현재 법령 조사 범위를 벗어나는 과도한 기능 확장
- Git 동기화 과정에서 로컬 변경을 강제로 덮어쓰기

## 현재 설계상 의도

- LLM provider: OpenRouter 또는 mock
- Embedding provider: OpenRouter 또는 mock
- Vector provider: in-memory
- Reranker provider: Cohere 또는 mock
- DB: H2 in-memory

이 구성은 제한된 법령 범위에서 Spring Boot 기반 AX/RAG 조사 흐름을 구현하기 위한 구성이다.

## Runtime Audit And Scenario Rules

- Ask response, search log, and agent trace must share the same `requestId`.
- Agent trace must preserve the successful request order: `QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic`.
- `scripts/run-scenarios.ps1` verifies status, citation count, expected citations, forbidden copy, requestId, search log correlation, and agent trace order.
- `GET /api/admin/agent-traces?requestId=...` and the admin page requestId filter are part of the audit workflow.
- Behavior changes that affect answers, trace order, logs, or runtime audit fields should update docs and tests together.

## Version Control Rules

- Treat Git history as part of the project evidence: each commit should describe one verified, meaningful unit of work.
- Check `git status --short` before editing, before committing, and before pushing.
- Commit behavior changes with the focused tests or harness updates that prove them.
- Keep documentation-only updates separate from runtime behavior changes unless the documentation is part of the same contract change.
- Do not stage secrets, `.env`, `target/`, local handoff notes, imported law-source repositories, or private portfolio/application drafts unless the user explicitly asks for them.
- Push after a tested milestone or when remote review/backup is needed; avoid saving many unrelated completed changes for one final push.
- Do not rewrite remote history or force-push for this application repository unless the user explicitly requests it.
