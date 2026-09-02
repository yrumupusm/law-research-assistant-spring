# ADR 0002: Gate answers on retrieved evidence and citations

**Status:** Accepted

## Context

An LLM can produce plausible legal names, article numbers, or conclusions that are not supported by the available source material. A legal-research product must make unsupported output observable and reject it before returning a successful answer.

## Decision

The request pipeline keeps retrieval, evidence validation, answer writing, and criticism as distinct stages. `OK` requires at least one cited article. A `LOW_CONFIDENCE` result that has retrieved or hydrated evidence also requires a citation. The critic rejects missing citations and response-quality violations.

## Consequences

- The product may decline more questions than a generic chatbot; this is intentional.
- Tests and scenario checks must preserve citation, forbidden-copy, request-ID, and trace-order assertions.
- New answer-generation behavior cannot bypass the critic gate.
