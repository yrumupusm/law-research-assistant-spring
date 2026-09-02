# ADR 0003: Use versioned local source ingestion and snapshot evidence

**Status:** Accepted

## Context

Legal answers require more than article text: reviewers need to know which source version, ingestion time, effective date, and source path supplied the evidence. A stable local format is also needed to validate parsing before source material reaches the application.

## Decision

The application ingests approved local Markdown law files, optionally after a fast-forward-only Git synchronization. Each ingestion creates a `SnapshotVersion`; law and article records retain source and effective-date metadata. Responses expose `snapshotVersion`, `indexedAt`, and `sourcePath` as answer-basis data.

## Consequences

- The source repository and its Markdown convention become an operational contract.
- Official source acquisition and conversion occur before ingestion; the application does not treat the current demo seed as authoritative law text.
- Source owners must define update cadence, review, and reindex procedures before internal operational use.
