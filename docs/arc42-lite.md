# arc42 Lite

This is the smallest useful subset of arc42 for this repository. It intentionally avoids duplicating API examples and detailed class-level documentation.

## 1. Goals and Scope

The application is an internal legal-research support service for a limited set of Korean laws in the trade-security, defence, and technology-protection domain.

It must return the evidence used for an answer, disclose the source snapshot, and decline to make an answer when the available evidence is insufficient. It is not a legal-advice, compliance-approval, or nationwide-law-search product.

The current reference corpus is a development/demo corpus. Replacing it with official source material for the chosen eight laws is a required milestone before internal operational use.

## 2. Constraints

- Java 17 and Spring Boot 3.3 are the application platform.
- Provider implementations are behind interfaces; provider names, URLs, models, and keys remain configuration, never source code.
- Development defaults use H2, in-memory vectors, and mock providers.
- Official or otherwise approved source material must be versioned, traceable, and ingested as local Markdown files.
- User questions are not stored verbatim in operational logs.

## 3. Quality Goals

| Priority | Quality goal | How it is enforced today |
|---|---|---|
| 1 | Grounded answers | Citation and evidence gates; critic check; insufficient-info response |
| 2 | Auditability | Shared request ID, search logs, agent traces, snapshot/source basis |
| 3 | Source freshness | Snapshot version and effective-date filtering; source-sync and ingestion path |
| 4 | Safe failure | Safe API error codes; no provider exception text in responses |
| 5 | Operability | Admin status, ingestion history, provider smoke test, scenario scripts |

## 4. Solution Strategy

The request pipeline separates question analysis, retrieval, evidence validation, answer composition, final criticism, and audit logging. Keyword and vector retrieval are merged and may be reranked. The answer writer may use an LLM, but retrieved evidence and the critic gate control the final result.

The system supports a local Markdown ingestion path, optionally preceded by a fast-forward-only Git source synchronization. Every ingestion creates a snapshot and indexes the resulting articles.

See [the C4 overview](architecture/c4.md) for the structural view.

## 5. Runtime Scenario

1. A caller submits a validated question and optional `asOf` date.
2. The query analyzer classifies the question and derives retrieval terms.
3. Retrieval selects effective articles using keyword and, when appropriate, vector search.
4. Evidence validation rejects missing or weak evidence as appropriate.
5. The answer writer composes a cited research answer; the critic checks citation and response-quality rules.
6. The response, search log, and agent trace receive the same request ID.

Failures at any stage produce an auditable `FAILED` response with a safe error code. A lack of evidence produces `INSUFFICIENT_INFO`, rather than an invented legal conclusion.

## 6. Deployment and Operations

For local development and tests, the default configuration is intentionally lightweight. For an internal operational environment, use PostgreSQL and Qdrant, configure real providers only when needed, keep secrets outside the repository, and run readiness, provider-smoke, runtime, and scenario verification.

Before exposing an admin endpoint beyond a trusted local environment, add an authentication and authorization boundary. This is not currently supplied by the application.

## 7. Risks and Next Milestones

| Risk or gap | Required next step |
|---|---|
| Demo summaries are not official full text | Ingest approved official source files for all eight selected laws |
| Source freshness can drift | Establish a source owner, update cadence, review, and reindex procedure |
| Default storage is ephemeral | Deploy and test PostgreSQL plus Qdrant |
| Admin endpoints lack an auth boundary | Introduce authentication, authorization, and operator roles before wider access |
| Retrieval quality is proven mainly by fixed scenarios | Build a reviewed real-work question set and measure citation accuracy and abstention |

## Related Decisions

The decision records in [docs/adr](adr/README.md) explain the few choices that materially constrain future implementation work.
