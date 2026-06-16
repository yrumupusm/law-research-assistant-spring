const form = document.querySelector("#ask-form");
const questionInput = document.querySelector("#question");
const asOfInput = document.querySelector("#as-of-date");
const todayButton = document.querySelector("#today-button");
const clearDateButton = document.querySelector("#clear-date-button");
const charCount = document.querySelector("#char-count");
const submitButton = document.querySelector("#submit-button");
const resultArea = document.querySelector("#result");
const serviceStatus = document.querySelector("#service-status");

const statusLabels = {
  OK: "근거 제시",
  LOW_CONFIDENCE: "추가 확인 필요",
  INSUFFICIENT_INFO: "정보 부족",
  FAILED: "실패",
};

const questionTypeLabels = {
  EXPLORATORY: "탐색형",
  CONFIRMATORY: "확인형",
  INSUFFICIENT: "정보 부족",
  REVISION_COMPARE: "개정 비교",
  METADATA: "메타데이터",
};

const retrievalStatLabels = {
  retrieved: "검색 조문",
  cited: "인용 조문",
  keywordHits: "키워드 후보",
  vectorHits: "벡터 후보",
  mergedHits: "병합 후보",
  weakEvidence: "근거 약함",
};

const latencyLabels = {
  analyze: "질문 분석",
  retrieve: "근거 검색",
  validate: "근거 확인",
  synthesize: "답변 작성",
};

const errorMessageLabels = {
  no_snapshot: "색인된 법령 데이터가 없습니다.",
  query_analysis_failed: "질문 분석 단계에서 처리가 중단되었습니다.",
  retrieval_failed: "근거 검색 단계에서 처리가 중단되었습니다.",
  evidence_validation_failed: "근거 확인 단계에서 처리가 중단되었습니다.",
  answer_generation_failed: "답변 작성 단계에서 처리가 중단되었습니다.",
  missing_citation: "인용 조문이 없어 답변이 중단되었습니다.",
  response_quality_failed: "응답 품질 기준을 충족하지 못해 답변이 중단되었습니다.",
};

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatDate(value) {
  if (!value) return "현재 유효 조문";
  try {
    return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
  } catch {
    return value;
  }
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

function shortText(value, max = 12) {
  const text = String(value ?? "-");
  return text.length > max ? `${text.slice(0, max)}...` : text;
}

function todayString() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function setLoading(loading) {
  submitButton.disabled = loading || !questionInput.value.trim();
  submitButton.querySelector("span").textContent = loading ? "조사 중" : "조사 시작";
}

function updateCharCount() {
  charCount.textContent = `${questionInput.value.length}/4000`;
  submitButton.disabled = !questionInput.value.trim();
}

function renderLoading(question, asOf) {
  resultArea.innerHTML = `
    <div class="loading">
      <strong>관련 조문을 검색하고 답변을 작성하고 있습니다.</strong>
      <div class="muted">${escapeHtml(question)}</div>
      <div class="muted">기준일: ${escapeHtml(asOf ? formatDate(asOf) : "현재 유효 조문")}</div>
    </div>
  `;
}

function renderError(message) {
  resultArea.innerHTML = `<div class="error-box">${escapeHtml(message)}</div>`;
}

function renderResponse(question, asOf, response) {
  const citedArticles = response.citedArticles ?? [];
  const candidateLaws = response.candidateLaws ?? [];
  const followUps = response.followUpQuestions ?? [];
  const diagnostics = response.diagnostics ?? {};
  const interpretation = response.interpretation;
  const basisAsOf = response.effectiveBasis?.asOf ?? asOf;

  resultArea.innerHTML = `
    <article class="result-card">
      <div class="result-header">
        <div>
          <h2 class="section-title">조사 결과</h2>
          <div class="muted">답변 상태와 근거 조문을 함께 확인합니다.</div>
        </div>
        <span class="badge ${response.status === "OK" ? "ok" : response.status === "FAILED" ? "error" : ""}">
          ${escapeHtml(statusLabels[response.status] ?? response.status)}
        </span>
      </div>
      <p class="question-box">${escapeHtml(question)}</p>
      <div class="basis-row">
        <span>기준일: ${escapeHtml(basisAsOf ? formatDate(basisAsOf) : "현재 유효 조문")}</span>
      </div>
      <div class="reasoning">${escapeHtml(response.reasoning || "답변 내용이 없습니다.")}</div>
      ${response.errorMessage ? renderErrorMessage(response.errorMessage) : ""}
      ${followUps.length ? renderFollowUps(followUps) : ""}
      <div class="footer-note">
        기준 데이터: ${escapeHtml(response.effectiveBasis?.snapshotVersion ?? "-")}
        · 색인 시각: ${escapeHtml(formatDateTime(response.effectiveBasis?.indexedAt))}
        <br>${escapeHtml(response.disclaimer ?? "")}
      </div>
    </article>

    ${renderArticles(citedArticles)}
    ${renderLaws(candidateLaws)}
    ${interpretation ? renderInterpretation(interpretation) : ""}
    ${renderDiagnostics(diagnostics)}
  `;

  bindArticleActions();
}

function renderErrorMessage(errorMessage) {
  const label = errorMessageLabels[errorMessage] ?? "요청 처리 중 오류가 발생했습니다.";
  return `<div class="error-summary">처리 상태: ${escapeHtml(label)}</div>`;
}

function renderFollowUps(items) {
  return `
    <div class="section compact-section">
      <h3 class="section-title">추가 확인 질문</h3>
      <ul class="query-list">
        ${items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
      </ul>
    </div>
  `;
}

function renderArticles(articles) {
  if (!articles.length) return "";
  return `
    <section class="section" aria-labelledby="articles-title">
      <h2 id="articles-title" class="section-title">인용 조문 <span class="muted">${articles.length}건</span></h2>
      ${articles.map(renderArticle).join("")}
    </section>
  `;
}

function renderArticle(article) {
  const articleLabel = [article.articleNumber, article.articleTitle].filter(Boolean).join(" ");
  const period = `${formatDate(article.effectiveFrom)} ~ ${article.effectiveTo ? formatDate(article.effectiveTo) : "현재"}`;
  const hasPrevious = Boolean(article.previousArticleId);
  const content = article.content ?? "";
  const contentId = `article-content-${article.articleId}`;
  const expandable = content.length > 260;
  return `
    <article class="article-card" data-article-id="${escapeHtml(article.articleId)}">
      <div class="article-meta">
        <span class="badge">${escapeHtml(article.lawTitle)}</span>
        <span class="article-title">${escapeHtml(articleLabel)}</span>
      </div>
      <div class="article-submeta">
        <span>유효기간: ${escapeHtml(period)}</span>
        ${article.amendmentKind ? `<span>변경구분: ${escapeHtml(article.amendmentKind)}</span>` : ""}
      </div>
      <p class="article-reason">${escapeHtml(article.reason)}</p>
      <div class="article-content ${expandable ? "is-collapsed" : ""}" id="${escapeHtml(contentId)}">${escapeHtml(content)}</div>
      ${expandable ? `
        <button class="text-button content-toggle" type="button" data-action="toggle-content" data-target-id="${escapeHtml(contentId)}" aria-expanded="false">
          전체 보기
        </button>
      ` : ""}
      ${renderInlineHistoricalEntries(article.historicalEntries ?? [])}
      <div class="article-actions">
        <button class="secondary-button" type="button" data-action="history" data-article-id="${escapeHtml(article.articleId)}">
          이력 보기
        </button>
        <button class="secondary-button" type="button" data-action="diff" data-article-id="${escapeHtml(article.articleId)}" data-previous-id="${escapeHtml(article.previousArticleId ?? "")}" ${hasPrevious ? "" : "disabled"}>
          이전 조문 비교
        </button>
      </div>
      <div class="article-detail" id="article-detail-${escapeHtml(article.articleId)}"></div>
    </article>
  `;
}

function renderInlineHistoricalEntries(entries) {
  if (!entries.length) return "";
  return `
    <div class="history-box cited-history">
      <strong>응답에 포함된 이전 회차 ${entries.length}건</strong>
      <p class="muted">개정비교 답변의 근거로 함께 사용된 이전 조문 본문입니다.</p>
      <ol class="history-list">
        ${entries.map((entry) => `
          <li>
            <span>${escapeHtml(formatDate(entry.effectiveFrom))} ~ ${entry.effectiveTo ? escapeHtml(formatDate(entry.effectiveTo)) : "현재"}</span>
            <span>${entry.amendmentKind ? escapeHtml(entry.amendmentKind) : "이전 회차"}</span>
          </li>
        `).join("")}
      </ol>
      ${entries.map((entry) => `
        <div class="article-content historical-content">
          ${escapeHtml(entry.content)}
        </div>
      `).join("")}
    </div>
  `;
}

function renderLaws(laws) {
  if (!laws.length) return "";
  return `
    <section class="section" aria-labelledby="laws-title">
      <h2 id="laws-title" class="section-title">관련 법령 <span class="muted">${laws.length}건</span></h2>
      <div class="law-grid">
        ${laws.map((law) => `
          <div class="law-item">
            <strong>${escapeHtml(law.title)}</strong>
            <span>${escapeHtml(law.relevanceReason)}</span>
          </div>
        `).join("")}
      </div>
    </section>
  `;
}

function renderInterpretation(interpretation) {
  const domains = interpretation.domainCandidates ?? [];
  const uncertainties = interpretation.uncertainties ?? [];
  return `
    <section class="interpretation-panel" aria-labelledby="interpretation-title">
      <h2 id="interpretation-title" class="section-title">질문 해석</h2>
      <dl class="kv-grid">
        <div class="kv"><dt>질문 유형</dt><dd>${escapeHtml(questionTypeLabels[interpretation.questionType] ?? interpretation.questionType)}</dd></div>
        <div class="kv"><dt>행위</dt><dd>${escapeHtml(interpretation.action ?? "-")}</dd></div>
        <div class="kv"><dt>대상</dt><dd>${escapeHtml(interpretation.object ?? "-")}</dd></div>
        <div class="kv"><dt>분야 후보</dt><dd>${escapeHtml(domains.join(", ") || "-")}</dd></div>
        <div class="kv"><dt>불확실성</dt><dd>${escapeHtml(uncertainties.join(", ") || "-")}</dd></div>
      </dl>
    </section>
  `;
}

function renderDiagnostics(diagnostics) {
  const generatedQueries = diagnostics.generatedQueries ?? [];
  const retrievalStats = diagnostics.retrievalStats ?? {};
  const latencyMs = diagnostics.latencyMs ?? {};
  return `
    <section class="process-panel" aria-labelledby="process-title">
      <h2 id="process-title" class="section-title">검색 과정</h2>
      ${generatedQueries.length ? `
        <ul class="query-list">
          ${generatedQueries.map((query) => `<li>${escapeHtml(query)}</li>`).join("")}
        </ul>
      ` : `<p class="muted">생성된 검색어가 없습니다.</p>`}
      <div class="stats-grid">
        <div class="kv">
          <dt>요청 ID</dt>
          <dd>${escapeHtml(shortText(diagnostics.requestId, 18))}</dd>
        </div>
        <div class="kv">
          <dt>검색 통계</dt>
          <dd>${renderRetrievalStats(retrievalStats)}</dd>
        </div>
        <div class="kv">
          <dt>처리 시간</dt>
          <dd>${renderLatency(latencyMs)}</dd>
        </div>
      </div>
    </section>
  `;
}

function renderRetrievalStats(values) {
  const ordered = ["retrieved", "cited", "keywordHits", "vectorHits", "mergedHits", "weakEvidence"];
  const entries = ordered
    .filter((key) => Object.prototype.hasOwnProperty.call(values, key))
    .map((key) => {
      const value = key === "weakEvidence" ? (Number(values[key]) > 0 ? "예" : "아니오") : `${values[key]}건`;
      return `${escapeHtml(retrievalStatLabels[key])}: ${escapeHtml(value)}`;
    });
  return entries.length ? entries.join("<br>") : "-";
}

function renderLatency(values) {
  const ordered = ["analyze", "retrieve", "validate", "synthesize"];
  const entries = ordered
    .filter((key) => Object.prototype.hasOwnProperty.call(values, key))
    .map((key) => `${escapeHtml(latencyLabels[key])}: ${escapeHtml(values[key])}ms`);
  return entries.length ? entries.join("<br>") : "-";
}

function renderKeyValues(values, suffix = "") {
  const entries = Object.entries(values);
  if (!entries.length) return "-";
  return entries
    .map(([key, value]) => `${escapeHtml(key)}: ${escapeHtml(value)}${suffix}`)
    .join("<br>");
}

function bindArticleActions() {
  document.querySelectorAll("[data-action='toggle-content']").forEach((button) => {
    button.addEventListener("click", () => toggleArticleContent(button));
  });
  document.querySelectorAll("[data-action='history']").forEach((button) => {
    button.addEventListener("click", () => loadArticleHistory(button.dataset.articleId));
  });
  document.querySelectorAll("[data-action='diff']").forEach((button) => {
    button.addEventListener("click", () => loadArticleDiff(button.dataset.articleId, button.dataset.previousId));
  });
}

function toggleArticleContent(button) {
  const targetId = button.dataset.targetId;
  const target = targetId ? document.getElementById(targetId) : null;
  if (!target) return;
  const expanded = button.getAttribute("aria-expanded") === "true";
  target.classList.toggle("is-collapsed", expanded);
  target.classList.toggle("is-expanded", !expanded);
  button.setAttribute("aria-expanded", String(!expanded));
  button.textContent = expanded ? "전체 보기" : "접기";
}

async function loadArticleHistory(articleId) {
  const target = document.querySelector(`#article-detail-${CSS.escape(articleId)}`);
  if (!target) return;
  target.innerHTML = `<div class="inline-loading">조문 이력을 불러오는 중입니다.</div>`;
  try {
    const response = await fetch(`/api/articles/${encodeURIComponent(articleId)}/history`);
    if (!response.ok) throw new Error(await response.text());
    const history = await response.json();
    target.innerHTML = `
      <div class="history-box">
        <strong>${escapeHtml(history.lawTitle)} ${escapeHtml(history.articleNumber)} 이력</strong>
        <ol class="history-list">
          ${(history.entries ?? []).map((entry) => `
            <li>
              <span>${escapeHtml(formatDate(entry.effectiveFrom))} ~ ${entry.effectiveTo ? escapeHtml(formatDate(entry.effectiveTo)) : "현재"}</span>
              <span>${entry.current ? "현행" : "이전"}${entry.amendmentKind ? ` · ${escapeHtml(entry.amendmentKind)}` : ""}</span>
            </li>
          `).join("")}
        </ol>
      </div>
    `;
  } catch (error) {
    target.innerHTML = `<div class="error-box small">${escapeHtml(error instanceof Error ? error.message : "이력 조회에 실패했습니다.")}</div>`;
  }
}

async function loadArticleDiff(articleId, previousId) {
  const target = document.querySelector(`#article-detail-${CSS.escape(articleId)}`);
  if (!target || !previousId) return;
  target.innerHTML = `<div class="inline-loading">이전 조문과 비교하는 중입니다.</div>`;
  try {
    const response = await fetch(`/api/articles/${encodeURIComponent(articleId)}/diff?compareWith=${encodeURIComponent(previousId)}`);
    if (!response.ok) throw new Error(await response.text());
    const diff = await response.json();
    target.innerHTML = `
      <div class="diff-grid">
        <div>
          <strong>선택 조문</strong>
          <pre>${escapeHtml(diff.contentA)}</pre>
        </div>
        <div>
          <strong>비교 조문</strong>
          <pre>${escapeHtml(diff.contentB)}</pre>
        </div>
      </div>
    `;
  } catch (error) {
    target.innerHTML = `<div class="error-box small">${escapeHtml(error instanceof Error ? error.message : "조문 비교에 실패했습니다.")}</div>`;
  }
}

async function ask(question, asOf) {
  renderLoading(question, asOf);
  setLoading(true);
  const response = await fetch("/api/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, asOf: asOf || null }),
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`요청 실패 (${response.status}): ${text}`);
  }

  return JSON.parse(text);
}

async function submitQuestion(question) {
  const trimmed = question.trim();
  if (!trimmed) return;
  const asOf = asOfInput.value || null;
  questionInput.value = trimmed;
  updateCharCount();
  const url = new URL(window.location.href);
  url.searchParams.set("q", trimmed);
  if (asOf) {
    url.searchParams.set("asOf", asOf);
  } else {
    url.searchParams.delete("asOf");
  }
  window.history.replaceState(null, "", url);

  try {
    const response = await ask(trimmed, asOf);
    renderResponse(trimmed, asOf, response);
  } catch (error) {
    renderError(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
  } finally {
    setLoading(false);
  }
}

async function loadServiceStatus() {
  try {
    const response = await fetch("/api/admin/status");
    if (!response.ok) throw new Error("status failed");
    const status = await response.json();
    const healthy = status.indexStatus === "healthy";
    serviceStatus.className = healthy ? "status-pill ok" : "status-pill error";
    const indexed = status.indexedArticlesCount ?? 0;
    const indexLabel = healthy ? "정상" : "색인 확인 필요";
    serviceStatus.textContent = `${indexLabel} · 법령 ${status.lawsCount}건 · 조문 ${status.articlesCount}건 · 색인 ${indexed}건`;
  } catch {
    serviceStatus.className = "status-pill error";
    serviceStatus.textContent = "API 확인 필요";
  }
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  submitQuestion(questionInput.value);
});

questionInput.addEventListener("input", updateCharCount);

todayButton.addEventListener("click", () => {
  asOfInput.value = todayString();
});

clearDateButton.addEventListener("click", () => {
  asOfInput.value = "";
});

document.querySelectorAll("[data-question]").forEach((button) => {
  button.addEventListener("click", () => {
    submitQuestion(button.dataset.question ?? "");
  });
});

const searchParams = new URLSearchParams(window.location.search);
const initialQuestion = searchParams.get("q");
const initialAsOf = searchParams.get("asOf");

if (initialAsOf) {
  asOfInput.value = initialAsOf;
}

updateCharCount();
loadServiceStatus();

if (initialQuestion) {
  submitQuestion(initialQuestion);
}
