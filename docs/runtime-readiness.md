# Runtime Readiness

Use this checklist before a manual server start or restart. These commands do not start, stop, or restart the Spring Boot process.

## 1. Check Configuration

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders
```

For a Qdrant-backed run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders -RequireQdrant
```

For a Cohere reranker run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders -RequireCohere
```

The script writes `target/readiness-summary.json`. It checks secret presence only; it never prints API key values.
If `VECTOR_PROVIDER=inmemory` or `RERANKER_PROVIDER=mock`, the script can still pass with warnings. That means the runtime can be verified, but Qdrant or Cohere is not part of the final proof unless the matching `-RequireQdrant` or `-RequireCohere` option is used.

## 2. Manual Runtime Validation

After you start or restart the server yourself:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1
```

`verify-final.ps1` runs readiness, runtime provider smoke, scenario harness, evidence report export, and completion evidence verification in order. It does not start, stop, or restart the server.
Use this command as the default final proof command. The expanded sequence below is only for troubleshooting an individual stage.

The expanded manual sequence is:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-completion-evidence.ps1
```

Expected evidence files:

```text
target/readiness-summary.json
target/runtime-evidence/runtime-summary.json
target/scenario-responses/scenario-summary.json
target/evidence-report.md
```

These files are the final proof package for provider connectivity, Korean-only answer policy, citation behavior, requestId audit linkage, and scenario quality.

`export-evidence-report.ps1` includes the readiness summary when `target/readiness-summary.json` exists, so the final report shows configuration readiness, runtime smoke, and scenario quality in one file.

`verify-completion-evidence.ps1` is the final gate. It fails if readiness has errors, provider smoke was not included, runtime citations are missing, scenario audit linkage is missing, forbidden copy appears, or the final report is absent.
