const statusArea = document.querySelector("#admin-status");
const refreshButton = document.querySelector("#refresh-button");
const providerSmokeButton = document.querySelector("#provider-smoke-button");
const providerSmokeResult = document.querySelector("#provider-smoke-result");
const reindexButton = document.querySelector("#reindex-button");
const reindexResult = document.querySelector("#reindex-result");
const sourceRepoUrlInput = document.querySelector("#source-repo-url");
const sourceLocalDirInput = document.querySelector("#source-local-dir");
const sourceBranchInput = document.querySelector("#source-branch");
const syncSourceButton = document.querySelector("#sync-source-button");
const syncSourceResult = document.querySelector("#sync-source-result");
const sourceDirInput = document.querySelector("#source-dir");
const ingestButton = document.querySelector("#ingest-button");
const ingestResult = document.querySelector("#ingest-result");
const ingestionRunsArea = document.querySelector("#ingestion-runs");
const searchLogsArea = document.querySelector("#search-logs");
const agentTracesArea = document.querySelector("#agent-traces");
const traceRequestIdInput = document.querySelector("#trace-request-id");
const clearTraceFilterButton = document.querySelector("#clear-trace-filter");
const lawKeywordInput = document.querySelector("#law-keyword");
const lawListArea = document.querySelector("#law-list");
const lawDetailArea = document.querySelector("#law-detail");
let reindexEnabled = false;
reindexButton.disabled = true;

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatDateTime(value) {
  if (!value) return "-";
  try {
    return new Intl.DateTimeFormat("ko-KR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(value));
  } catch {
    return value;
  }
}

function shortText(value, max = 72) {
  const text = String(value ?? "-");
  return text.length > max ? `${text.slice(0, max)}...` : text;
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `요청 실패 (${response.status})`);
  }
  return text ? JSON.parse(text) : {};
}

function renderStatus(status) {
  const syncState = status.syncState;
  const cards = [
    ["색인 상태", status.indexStatus],
    ["법령", `${status.lawsCount}건`],
    ["조문", `${status.articlesCount}건`],
    ["색인 조문", `${status.indexedArticlesCount ?? 0}건`],
    ["미색인 조문", `${status.unindexedArticlesCount ?? 0}건`],
    ["검색 로그", `${status.searchLogCount}건`],
    ["스냅샷", status.lastSnapshotVersion ?? "-"],
    ["색인 시각", formatDateTime(status.lastIndexedAt)],
    ["동기화 commit", syncState?.lastSyncedCommitSha ? shortText(syncState.lastSyncedCommitSha, 12) : "-"],
    ["동기화 시각", formatDateTime(syncState?.lastSyncAt)],
    ["이력 충돌 감지", formatDateTime(syncState?.lastForcePushDetectedAt)],
  ];
  statusArea.innerHTML = cards.map(([label, value]) => `
    <div class="metric-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </div>
  `).join("");
  updateReindexControl(Boolean(status.reindexEnabled));
}

function updateReindexControl(enabled) {
  reindexEnabled = enabled;
  reindexButton.disabled = !enabled;
  if (!enabled) {
    reindexResult.textContent = "재색인은 비활성화되어 있습니다. ADMIN_REINDEX_ENABLED=true 설정 후 사용할 수 있습니다.";
  } else if (!reindexResult.textContent || reindexResult.textContent.includes("ADMIN_REINDEX_ENABLED")) {
    reindexResult.textContent = "실행 대기 중입니다.";
  }
}

function renderIngestionRuns(response) {
  const rows = response.items ?? [];
  if (!rows.length) {
    ingestionRunsArea.innerHTML = `<p class="muted">재색인 실행 이력이 없습니다.</p>`;
    return;
  }
  ingestionRunsArea.innerHTML = `
    <table>
      <thead><tr><th>ID</th><th>상태</th><th>처리</th><th>실패</th><th>스냅샷</th><th>시작</th><th>종료</th></tr></thead>
      <tbody>
        ${rows.map((row) => `
          <tr>
            <td>${escapeHtml(row.ingestionRunId)}</td>
            <td>${escapeHtml(row.status)}</td>
            <td>${escapeHtml(row.filesProcessed ?? 0)}건</td>
            <td>${escapeHtml(row.filesFailed ?? 0)}건</td>
            <td>${escapeHtml(row.snapshotVersion ?? "-")}</td>
            <td>${escapeHtml(formatDateTime(row.startedAt))}</td>
            <td>${escapeHtml(formatDateTime(row.finishedAt))}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function renderSearchLogs(response) {
  const rows = response.items ?? [];
  if (!rows.length) {
    searchLogsArea.innerHTML = `<p class="muted">검색 로그가 없습니다.</p>`;
    return;
  }
  searchLogsArea.innerHTML = `
    <table>
      <thead><tr><th>시간</th><th>요청</th><th>상태</th><th>기준일</th><th>질문</th><th>근거</th><th>질문 유형</th></tr></thead>
      <tbody>
        ${rows.map((row) => `
          <tr>
            <td>${escapeHtml(formatDateTime(row.createdAt))}</td>
            <td>
              <button type="button" class="trace-link" data-trace-request-id="${escapeHtml(row.requestId)}">
                ${escapeHtml(shortText(row.requestId, 8))}
              </button>
            </td>
            <td>${escapeHtml(row.status)}</td>
            <td>${escapeHtml(row.asOf ?? "현재")}</td>
            <td>${escapeHtml(shortText(row.questionPreview))}</td>
            <td>${escapeHtml(row.citedArticleCount ?? 0)}건</td>
            <td>${escapeHtml(row.questionType ?? "-")}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
  searchLogsArea.querySelectorAll("[data-trace-request-id]").forEach((button) => {
    button.addEventListener("click", () => loadAgentTraces(button.dataset.traceRequestId ?? ""));
  });
}

function renderAgentTraces(response) {
  const rows = response.items ?? [];
  const filterLabel = response.filteredRequestId
    ? `<p class="muted trace-filter-state">요청 ID ${escapeHtml(shortText(response.filteredRequestId, 18))} 기준으로 표시 중입니다.</p>`
    : "";
  if (!rows.length) {
    agentTracesArea.innerHTML = `${filterLabel}<p class="muted">에이전트 추적 이력이 없습니다.</p>`;
    return;
  }
  agentTracesArea.innerHTML = `
    ${filterLabel}
    <table>
      <thead><tr><th>시간</th><th>요청</th><th>단계</th><th>상태</th><th>처리</th><th>출력</th></tr></thead>
      <tbody>
        ${rows.slice(0, 20).map((row) => `
          <tr>
            <td>${escapeHtml(formatDateTime(row.createdAt))}</td>
            <td>${escapeHtml(shortText(row.requestId, 8))}</td>
            <td>${escapeHtml(row.stepName)}</td>
            <td>${escapeHtml(row.status)}</td>
            <td>${escapeHtml(row.latencyMs ?? 0)}ms</td>
            <td>${escapeHtml(shortText(row.outputSummary))}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function renderLaws(response) {
  const laws = response.items ?? [];
  if (!laws.length) {
    lawListArea.innerHTML = `<p class="muted">조회된 법령이 없습니다.</p>`;
    lawDetailArea.innerHTML = "";
    return;
  }
  lawListArea.innerHTML = laws.map((law) => `
    <button type="button" class="list-item" data-law-id="${escapeHtml(law.lawId)}">
      <strong>${escapeHtml(law.title)}</strong>
      <span>${escapeHtml(law.lawType)} · ${escapeHtml(law.lawNumber ?? "-")} · 조문 ${escapeHtml(law.articleCount ?? 0)}건 · 회차 ${escapeHtml(law.revisionCount ?? 0)}건</span>
    </button>
  `).join("");
  document.querySelectorAll("[data-law-id]").forEach((button) => {
    button.addEventListener("click", () => loadLawDetail(button.dataset.lawId));
  });
}

function renderLawDetail(detail) {
  const articles = detail.articles ?? [];
  lawDetailArea.innerHTML = `
    <div class="detail-title">
      <strong>${escapeHtml(detail.title)}</strong>
      <span>${escapeHtml(detail.snapshotVersion ?? "-")}</span>
    </div>
    <div class="revision-summary" id="law-revisions-${escapeHtml(detail.lawId)}">
      <div class="inline-loading">개정 이력을 불러오는 중입니다.</div>
    </div>
    <div class="article-summary-list">
      ${articles.map((article) => `
        <details>
          <summary>${escapeHtml([article.articleNumber, article.articleTitle].filter(Boolean).join(" "))}</summary>
          <pre>${escapeHtml(article.content)}</pre>
        </details>
      `).join("")}
    </div>
  `;
  loadLawRevisions(detail.lawId);
}

function renderLawRevisions(response) {
  const rows = response.revisions ?? [];
  if (!rows.length) {
    return `<p class="muted">법령 단위 개정 이력이 없습니다.</p>`;
  }
  return `
    <div class="history-box">
      <strong>법령 단위 개정 이력 ${rows.length}건</strong>
      <table>
        <thead><tr><th>시행일</th><th>개정구분</th><th>조문 수</th></tr></thead>
        <tbody>
          ${rows.map((row) => `
            <tr>
              <td>${escapeHtml(row.effectiveFrom ?? "-")}</td>
              <td>${escapeHtml(row.amendmentKind ?? "-")}</td>
              <td>${escapeHtml(row.articleCount ?? 0)}건</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderProviderSmoke(response) {
  const rows = [
    ["LLM", response.llmProvider, formatProviderResult(response.llmStatus, response.llmError)],
    ["임베딩", response.embeddingProvider, `${response.embeddingStatus} · ${response.embeddingDimensions}차원`],
    ["재정렬", response.rerankerProvider, `${response.rerankerStatus} · ${response.rerankedCount}건 · top ${response.topRerankedId ?? "-"}`],
  ];
  if (response.embeddingStatus === "failed") {
    rows[1][2] = `${rows[1][2]} · ${response.embeddingError ?? "embedding_provider_failed"}`;
  }
  if (response.rerankerStatus === "failed") {
    rows[2][2] = `${rows[2][2]} · ${response.rerankerError ?? "reranker_provider_failed"}`;
  }
  const hasFailure = rows.some(([, , result]) => result.includes("failed"));
  providerSmokeResult.className = hasFailure ? "provider-status warning-box small" : "provider-status";
  providerSmokeResult.innerHTML = `
    <table>
      <thead><tr><th>구분</th><th>Provider</th><th>결과</th></tr></thead>
      <tbody>
        ${rows.map(([label, provider, result]) => `
          <tr>
            <td>${escapeHtml(label)}</td>
            <td>${escapeHtml(provider)}</td>
            <td>${escapeHtml(result)}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function formatProviderResult(status, errorCode, detail = "") {
  const suffix = detail ? ` · ${detail}` : "";
  if (status === "failed") {
    return `${status}${suffix} · ${errorCode ?? "provider_failed"}`;
  }
  return `${status}${suffix}`;
}

function formatFailureSuffix(response) {
  const failed = response.failedArticles ?? response.filesFailed ?? 0;
  const message = response.errorMessage ? ` 사유: ${response.errorMessage}` : "";
  return failed > 0 ? ` 실패 ${failed}건.${message}` : "";
}

async function loadDashboard() {
  const [status, ingestionRuns, searchLogs] = await Promise.all([
    fetchJson("/api/admin/status"),
    fetchJson("/api/admin/ingestion-runs"),
    fetchJson("/api/admin/search-logs"),
  ]);
  renderStatus(status);
  renderIngestionRuns(ingestionRuns);
  renderSearchLogs(searchLogs);
  await loadAgentTraces(traceRequestIdInput.value.trim());
}

async function loadAgentTraces(requestId = "") {
  const normalized = requestId.trim();
  traceRequestIdInput.value = normalized;
  const url = normalized
    ? `/api/admin/agent-traces?requestId=${encodeURIComponent(normalized)}`
    : "/api/admin/agent-traces";
  const response = await fetchJson(url);
  renderAgentTraces({
    ...response,
    filteredRequestId: normalized,
  });
}

async function loadLaws() {
  const keyword = lawKeywordInput.value.trim();
  const url = keyword ? `/api/laws?q=${encodeURIComponent(keyword)}&page=1&size=20` : "/api/laws?page=1&size=20";
  const response = await fetchJson(url);
  renderLaws(response);
}

async function loadLawDetail(lawId) {
  lawDetailArea.innerHTML = `<div class="inline-loading">법령 상세를 불러오는 중입니다.</div>`;
  try {
    const detail = await fetchJson(`/api/laws/${encodeURIComponent(lawId)}`);
    renderLawDetail(detail);
  } catch (error) {
    lawDetailArea.innerHTML = `<div class="error-box small">${escapeHtml(error instanceof Error ? error.message : "법령 상세 조회에 실패했습니다.")}</div>`;
  }
}

async function loadLawRevisions(lawId) {
  const target = document.querySelector(`#law-revisions-${CSS.escape(String(lawId))}`);
  if (!target) return;
  try {
    const revisions = await fetchJson(`/api/laws/${encodeURIComponent(lawId)}/revisions`);
    target.innerHTML = renderLawRevisions(revisions);
  } catch (error) {
    target.innerHTML = `<div class="error-box small">${escapeHtml(error instanceof Error ? error.message : "법령 개정 이력 조회에 실패했습니다.")}</div>`;
  }
}

async function runReindex() {
  if (!reindexEnabled) {
    reindexResult.textContent = "재색인은 비활성화되어 있습니다. ADMIN_REINDEX_ENABLED=true 설정 후 사용할 수 있습니다.";
    return;
  }
  reindexButton.disabled = true;
  reindexResult.textContent = "재색인을 실행 중입니다.";
  try {
    const response = await fetchJson("/api/admin/reindex", { method: "POST" });
    reindexResult.textContent = `${response.status}: 조문 ${response.indexedArticles}건을 색인했습니다.${formatFailureSuffix(response)}`;
    await loadDashboard();
  } catch (error) {
    reindexResult.textContent = error instanceof Error ? error.message : "재색인에 실패했습니다.";
  } finally {
    reindexButton.disabled = !reindexEnabled;
  }
}

async function runProviderSmokeTest() {
  providerSmokeButton.disabled = true;
  providerSmokeResult.className = "provider-status muted";
  providerSmokeResult.textContent = "Provider 연결을 점검하는 중입니다.";
  try {
    const response = await fetchJson("/api/admin/provider-smoke-test", { method: "POST" });
    renderProviderSmoke(response);
  } catch (error) {
    providerSmokeResult.className = "provider-status error-box small";
    providerSmokeResult.textContent = error instanceof Error ? error.message : "Provider 연결 점검에 실패했습니다.";
  } finally {
    providerSmokeButton.disabled = false;
  }
}

async function runIngest() {
  const sourceDir = sourceDirInput.value.trim();
  if (!sourceDir) {
    ingestResult.textContent = "수집할 디렉터리 경로를 입력하세요.";
    return;
  }
  ingestButton.disabled = true;
  ingestResult.textContent = "로컬 법령 파일을 수집하고 색인하는 중입니다.";
  try {
    const response = await fetchJson("/api/admin/ingest-local", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sourceDir, snapshotPrefix: "law-local" }),
    });
    ingestResult.textContent = `${response.status}: 법령 ${response.lawsImported}건, 조문 ${response.articlesImported}건을 수집하고 ${response.indexedArticles}건을 색인했습니다.${formatFailureSuffix(response)}`;
    await Promise.all([loadDashboard(), loadLaws()]);
  } catch (error) {
    ingestResult.textContent = error instanceof Error ? error.message : "수집에 실패했습니다.";
  } finally {
    ingestButton.disabled = false;
  }
}

async function runSourceSync() {
  const repoUrl = sourceRepoUrlInput.value.trim();
  const localDir = sourceLocalDirInput.value.trim();
  const branch = sourceBranchInput.value.trim() || "main";
  if (!repoUrl || !localDir) {
    syncSourceResult.textContent = "저장소 URL과 저장 경로를 입력하세요.";
    return;
  }
  syncSourceButton.disabled = true;
  syncSourceResult.textContent = "저장소를 동기화하고 법령 파일을 수집하는 중입니다.";
  try {
    const response = await fetchJson("/api/admin/sync-source", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        repoUrl,
        localDir,
        branch,
        ingestAfterSync: true,
        snapshotPrefix: "law-source",
      }),
    });
    const ingestion = response.ingestion;
    sourceDirInput.value = response.localDir ?? localDir;
    syncSourceResult.textContent = ingestion
      ? `완료: ${response.action} · commit ${shortText(response.commitHash, 12)} · ${ingestion.status} · 법령 ${ingestion.lawsImported}건, 조문 ${ingestion.articlesImported}건 수집.${formatFailureSuffix(ingestion)}`
      : `완료: ${response.action} · commit ${shortText(response.commitHash, 12)}`;
    await Promise.all([loadDashboard(), loadLaws()]);
  } catch (error) {
    syncSourceResult.textContent = error instanceof Error ? error.message : "저장소 동기화에 실패했습니다.";
  } finally {
    syncSourceButton.disabled = false;
  }
}

refreshButton.addEventListener("click", () => {
  loadDashboard();
  loadLaws();
});

clearTraceFilterButton.addEventListener("click", () => {
  loadAgentTraces();
});

traceRequestIdInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    loadAgentTraces(traceRequestIdInput.value);
  }
});

reindexButton.addEventListener("click", runReindex);
providerSmokeButton.addEventListener("click", runProviderSmokeTest);
syncSourceButton.addEventListener("click", runSourceSync);
ingestButton.addEventListener("click", runIngest);

lawKeywordInput.addEventListener("input", () => {
  window.clearTimeout(lawKeywordInput.searchTimer);
  lawKeywordInput.searchTimer = window.setTimeout(loadLaws, 220);
});

loadDashboard().catch((error) => {
  statusArea.innerHTML = `<div class="error-box">${escapeHtml(error instanceof Error ? error.message : "대시보드 조회에 실패했습니다.")}</div>`;
});
loadLaws().catch((error) => {
  lawListArea.innerHTML = `<div class="error-box small">${escapeHtml(error instanceof Error ? error.message : "법령 목록 조회에 실패했습니다.")}</div>`;
});
