# AGENTS.md

## Project Role

This repository is a Spring Boot law research assistant. Treat its agents as a role-based application pipeline, not as a fully autonomous multi-agent system.

Core runtime flow:

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

## Must Follow

- Read `PROJECT_GUIDE.md` before making behavior changes.
- Preserve the invariant that `status=OK` requires at least one `citedArticles` item.
- Preserve the invariant that `LOW_CONFIDENCE` with `retrievalStats.retrieved > 0` or `retrievalStats.hydrated > 0` requires at least one `citedArticles` item.
- Do not generate law names, article numbers, or legal conclusions that are not grounded in retrieved context.
- Return `INSUFFICIENT_INFO` or a follow-up question when the question lacks enough facts.
- Include `snapshotVersion`, `indexedAt`, and `sourcePath` in answer basis data.
- Preserve strict Ask request validation: reject unknown JSON fields, blank questions, questions over 4000 characters, and invalid `asOf` dates with `400 Bad Request`.
- Return safe failure codes in `AskResponse.errorMessage`, including critic failures such as `missing_citation` and `response_quality_failed`; do not expose provider exception text or full user questions in API responses.
- Preserve request auditability: `AskResponse.diagnostics.requestId`, `SearchLog.requestId`, and `AgentTrace.requestId` must refer to the same request.
- Successful request traces should keep the stage order `QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic`.
- Keep controllers thin. HTTP request/response handling belongs in controllers; analysis, retrieval, validation, answer writing, and logging belong in services or agents.
- Keep LLM, embedding, vector search, and reranker integrations behind provider interfaces.
- Store provider names, URLs, model names, and API keys in configuration or environment variables only.
- Run a relevant test before reporting a change as complete. Prefer `mvn test` for behavior changes.

## Never Do

- Do not return `status=OK` with an empty `citedArticles` list.
- Do not hide provider, retrieval, parsing, or validation failures as empty results.
- Do not answer legal advice, legality, permission, or compliance questions as a final determination.
- Do not save the full user question in operational logs; use hash, length, and bounded preview patterns.
- Do not hardcode API keys, internal URLs, account data, or real company confidential data.
- Do not bypass `CriticAgent` checks when changing answer generation.
- Do not remove scenario checks for citation, forbidden copy, requestId, search log, or agent trace correlation.
- Do not expand beyond the P0 scope unless the task explicitly asks for it.

## Change Workflow

1. Identify whether the change affects API contracts, agent orchestration, retrieval, provider interfaces, logging, or evaluation.
2. Update the closest existing document when behavior or guarantees change.
3. Add or update fixed evaluation questions in `harness/questions.json` when a user-visible answer behavior changes.
4. Add focused tests for new invariants before broad refactors.
5. Verify with the narrowest useful test, then with `mvn test` when the change touches shared behavior.
6. Check `git status --short` before and after edits so local-only files, secrets, generated output, and unrelated user changes stay out of commits.
7. Commit each verified, meaningful unit of work separately; avoid broad "misc" commits that mix docs, tests, behavior, formatting, and generated artifacts.
8. Push after a milestone is complete or when the user asks for remote backup/review; do not wait until a large batch of unrelated work has accumulated.
9. Before pushing, review `git diff --stat`, confirm the intended files are staged, and keep `.env`, `target/`, local handoff notes, imported source repos, and private portfolio drafts untracked unless explicitly requested.

## Git Workflow

- Prefer small commit subjects with a clear type and scope when useful, such as `docs: add git workflow`, `test: cover citation invariant`, `fix: preserve low-confidence citations`, or `feat: export runtime evidence`.
- Keep behavior changes and their tests in the same commit when the test proves the behavior. Keep pure documentation updates separate from runtime code changes.
- Do not rewrite shared history or force-push unless the user explicitly asks for it.
- If generated files are produced during verification, commit them only when they are part of the repository contract; otherwise leave them ignored or remove only the files you created.
- If a change exposes a larger follow-up, commit the completed safe slice first and document the follow-up instead of expanding the commit scope.

## Verification Map

- API behavior and audit correlation: `AskControllerIntegrationTest`, `AdminControllerIntegrationTest`
- Answer invariants: `AskResponseInvariantTest`
- Fixed question regression: `EvaluationHarnessTest`
- Runtime scenario contract: `ScenarioScriptContractTest`, `scripts/run-scenarios.ps1`
- Provider abstraction: `ProviderInterfaceTest`
- Original project parity: `OriginalParityContractTest`, `docs/original-parity.md`
- Full regression: `mvn test`
