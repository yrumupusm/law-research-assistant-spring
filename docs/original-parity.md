# Original Project Parity

This document maps the original `law_research_assistant` guidance to this Spring Boot implementation.
The Spring version is a portfolio-scale reimplementation, not a line-by-line port of the FastAPI and
Next.js codebase. It keeps the original product invariants while replacing the stack with Spring Boot,
JPA, Thymeleaf-free static UI, and provider interfaces.

No company-confidential code, internal data, account data, or private URLs are part of this project.

## Source Guidance Used

- Original `CLAUDE.md`
- Original `docs/p0-scope.md`
- Original `docs/architecture.md`
- Original `docs/constraints.md`

## P0 Scope Mapping

| Original requirement | Spring implementation | Evidence |
| --- | --- | --- |
| Current-law search | `LawQueryService`, `ArticleRepository`, `RetrievalAgent` filter current articles when `asOf` is absent | `docs/architecture.md`, `ArticleRepositoryAsOfTest` |
| Act/decree/rule indexing | `MarkdownLawParser` maps supported law types and skips out-of-scope types | `MarkdownLawParserTest`, `LocalLawIngestionServiceTest` |
| Natural language question input | `POST /api/ask` and `POST /api/v1/ask` | `AskController`, `V1AskController` |
| Query structuring | `QueryAnalyzerAgent` extracts action, object, domain candidates, generated queries, and question type | `QueryAnalyzerAgentTest` |
| Document/article hybrid retrieval | `RetrievalAgent` merges keyword/JPA hits and `VectorSearchClient` hits, then ranks article evidence | `RetrievalAgentRerankerTest` |
| Candidate laws | `AskResponse.candidateLaws` and `/api/v1` `candidate_laws` | `AskResponseInvariantTest`, `V1ResponseMapper` |
| Cited articles | `AskResponse.citedArticles` and `/api/v1` `cited_articles` | `AskResponseInvariantTest`, `EvaluationHarnessTest` |
| Reason explanation | `AnswerWriterAgent` produces grounded reasoning from hydrated evidence only | `AnswerWriterAgentTest` |
| Follow-up questions | `QueryAnalyzerAgent` and `AnswerWriterAgent` preserve uncertainty-driven follow-up questions | `QueryAnalyzerAgentTest`, `AnswerWriterAgentTest` |
| Effective basis | `EffectiveBasisDto` includes `snapshotVersion`, `indexedAt`, `sourcePath`, and optional `asOf` | `docs/api-contract.md` |
| Minimal admin API/UI | `/api/admin/*`, `/api/v1/admin/*`, `static/admin.html`, `static/admin.js` | `AdminControllerIntegrationTest`, `StaticResourceTextTest` |

## Architecture Mapping

| Original layer | Spring implementation |
| --- | --- |
| Ingestion Layer | `SourceSyncService`, `LocalLawIngestionService` |
| Parsing and Normalization Layer | `MarkdownLawParser`, `ParsedLawFile`, `ParsedLawArticle` |
| Indexing Layer | `VectorIndexService`, `EmbeddingClient`, `VectorSearchClient` |
| Retrieval Layer | `RetrievalAgent`, `ArticleRepository`, `RerankerClient` |
| LLM Orchestration Layer | `AskOrchestratorService`, `QueryAnalyzerAgent`, `AnswerWriterAgent`, `CriticAgent` |
| API Layer | `AskController`, `V1AskController`, `LawController`, `V1LawController`, `AdminController`, `V1AdminController` |
| Web UI Layer | `src/main/resources/static/index.html`, `app.js`, `styles.css` |
| Admin Layer | `src/main/resources/static/admin.html`, `admin.js`, `/api/admin/*` |

The runtime pipeline is:

```text
AskController
-> AskOrchestratorService
-> QueryAnalyzerAgent
-> RetrievalAgent
-> EvidenceValidatorAgent
-> AnswerWriterAgent
-> CriticAgent
-> SearchLogAgent
```

## Critical Invariants

- `status=OK requires citations`: an OK ask response must have at least one cited article.
- `LOW_CONFIDENCE` with retrieved or hydrated evidence also preserves cited articles.
- The LLM cannot answer from model knowledge alone; answer generation receives retrieved context.
- No legal final determination is allowed. The answer must stay in research-assistant wording.
- Every answer basis includes `snapshotVersion`, `indexedAt`, and `sourcePath`; `asOf` is included when requested.
- Provider details are hidden behind `ChatModelClient`, `EmbeddingClient`, `VectorSearchClient`, and `RerankerClient`.
- Provider URLs, model names, dimensions, and API keys come from configuration or environment variables.
- Ask responses, search logs, and agent traces share the same `requestId`.

## Original API Compatibility

The Spring implementation exposes two API shapes:

- `/api/...`: camelCase DTOs used by the Spring static UI.
- `/api/v1/...`: original-compatible snake_case DTOs.

Important original-style response fields are preserved:

- `candidate_laws`
- `cited_articles`
- `follow_up_questions`
- `effective_basis`
- `retrieval_stats`
- `request_id`
- `error_message`

`POST /api/v1/ask` supports both strict validation and the `as_of` alias for the requested basis date.

## Retrieval And Provider Parity

The original architecture requires hybrid retrieval:

- exact/fuzzy title matching
- full-text or keyword search
- dense vector retrieval
- reranker

The Spring implementation uses:

- JPA keyword search and lexical scoring in `RetrievalAgent`
- compact law-title and article-number matching for spacing and alias tolerance
- `EmbeddingClient` for dense vectors
- `VectorSearchClient` with in-memory and Qdrant implementations
- `RerankerClient` with mock and Cohere implementations

External provider implementations:

- OpenRouter chat: `OpenRouterChatModelClient`
- OpenRouter embedding: `OpenRouterEmbeddingClient`
- Qdrant vector store: `QdrantVectorSearchClient`
- Cohere reranker: `CohereRerankerClient`

The mock providers are local development fallbacks, not the intended final runtime evidence for portfolio
verification.

## Audit And Observability Mapping

| Original need | Spring implementation |
| --- | --- |
| Search result traceability | `SearchLog` stores request id, question hash, status, citation count, latency, and bounded preview |
| Agent/process traceability | `AgentTrace` stores step order, summaries, status, and latency |
| Request correlation | `AskResponse.diagnostics.requestId`, `SearchLog.requestId`, and `AgentTrace.requestId` are the same |
| HTTP audit | `HttpRequestAuditFilter` logs method, path, status, and elapsed time without body/query details |
| Operational admin state | `AdminService`, `/api/admin/status`, `/api/admin/ingestion-runs`, `/api/admin/provider-smoke-test` |

## Harness And Verification Mapping

The original project used Claude harness guidance around citation checks, documentation lookup, provider cost
discipline, and silent-failure detection. The Spring implementation captures the same intent through code
contracts and scripts:

- `AskResponseInvariantTest`: citation/status invariants.
- `EvaluationHarnessTest` and `AnswerQualityHarnessTest`: fixed question answer quality.
- `ScenarioScriptContractTest`: runtime scenario script contract.
- `SilentFailureContractTest`: no silent provider/retrieval/parsing/validation failure patterns in guidance.
- `ProviderInterfaceTest`: service boundaries depend on provider interfaces.
- `scripts/verify-local.ps1`: server-independent local regression.
- `scripts/verify-readiness.ps1`: configuration and secret-presence readiness without printing keys.
- `scripts/verify-runtime.ps1`: live server health, admin status, ask, and optional provider smoke.
- `scripts/run-scenarios.ps1`: scenario answers, citations, Korean-only policy, forbidden copy, search log, and trace checks.
- `scripts/export-evidence-report.ps1`: final evidence report.
- `scripts/verify-completion-evidence.ps1`: final evidence gate.
- `scripts/verify-final.ps1`: final wrapper after the user manually starts or restarts the server.

## Deliberate Differences

- The original stack was FastAPI, Next.js, Qdrant, and Docker Compose. This implementation uses Spring Boot,
  JPA, static frontend assets, and pluggable Qdrant support.
- The original P0 excluded date-based search and revision comparison UI. The Spring implementation includes
  controlled `asOf`, history, and diff support as extensions because they strengthen the portfolio story, but
  the core ask flow still works without those extensions.
- The original P0 kept the reranker as interface plus mock. This implementation also supports Cohere reranking,
  with mock retained for local development.

## Remaining Runtime Proof

Local tests can prove the implementation contracts without starting the server. Full portfolio evidence still
requires a manual server run with configured providers:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1
```

This command does not start, stop, or restart the Spring Boot server. It only verifies a server that the user
has already started.
