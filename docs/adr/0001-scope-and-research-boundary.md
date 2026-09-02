# ADR 0001: Limit the service to cited legal research support in a selected law domain

**Status:** Accepted

## Context

The application can retrieve and summarize legal-source material, but its corpus is deliberately limited and legal questions often depend on facts outside the text of an individual article. Presenting its output as a final legality, permission, or compliance decision would overstate what the system can establish.

## Decision

The product is an internal research-support service for the selected eight-law domain. Responses must be grounded in retrieved articles and must avoid a final legal conclusion. Where facts or evidence are insufficient, the service returns `INSUFFICIENT_INFO` or a follow-up question.

## Consequences

- The UI and answer policy must frame results as research assistance and retain cited source material.
- Expanding to another law domain requires a defined corpus, evaluation questions, and source-update process.
- This boundary permits useful internal research while keeping a human responsible for legal interpretation and decisions.
