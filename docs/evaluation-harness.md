# Evaluation Harness

## 목적

LLM/RAG 기능은 응답이 흔들릴 수 있으므로 고정 질문셋으로 반복 평가한다.

## 평가셋 구조

원본 `law_research_assistant`와 같은 방향으로 retrieval recall과 답변 품질을 분리한다.

- `harness/questions.json`: v1 성격의 API 통합 평가셋. 질문 분석, retrieval, citation, status, trace 연동을 함께 확인한다.
- `harness/answer-quality-v2.json`: v2 성격의 답변 품질 평가셋. retrieval 결과가 이미 주어졌다고 보고 한국어 응답, 인용 조문, 금지 문구, 단정 표현, 조문번호 유효성을 확인한다.

## 1차 평가 항목

| Metric | 설명 |
|---|---|
| Citation Required | `status=OK`이면 cited article이 존재해야 한다 |
| Retrieval Recall | 기대 법령/조문 쌍 중 최소 1건이 citation에 포함되는지 확인한다 |
| Vector Hit Threshold | 정상 질문은 최소 vector hit 수를 만족해야 한다 |
| Insufficient Handling | 모호한 질문은 `INSUFFICIENT_INFO`로 처리한다 |
| Metadata Handling | 시행일/법령번호 질문은 법령 메타데이터 기준으로 답변한다 |
| Revision Compare | 개정비교 질문은 비교 대상 조문과 이전 회차를 citation/historicalEntries로 묶는다 |
| Log Safety | search log에 hash, 길이, preview 중심으로 저장한다 |
| Agent Trace | 단계별 trace가 저장되고 시나리오 스크립트가 requestId 기준 trace 순서를 확인한다 |
| Failure Audit | 색인 누락과 질문 처리 실패도 `FAILED` search log와 failed agent trace를 남긴다 |
| Runtime Contract | `verify-runtime.ps1`가 `.env`의 `SERVER_PORT`를 반영해 health, admin status, `/api/ask`, `/api/v1/ask` 계약과 금지 문구를 함께 확인한다 |
| Runtime Preflight | `preflight.ps1`가 provider/env/safety 설정과 주요 숫자 설정을 서버 실행 전에 확인한다 |
| Local Verification | `verify-local.ps1`가 서버 없이 가능한 preflight, script parser, JS syntax, 증분 컴파일을 끈 Maven test를 묶어 실행한다 |
| Repository Text Safety | 문서, 스크립트, Java 소스/테스트에 깨진 한글 인코딩 패턴이 들어오지 않도록 검사한다 |
| Runtime Evidence | `verify-runtime.ps1`가 주요 JSON 응답과 `runtime-summary.json`을 `target/runtime-evidence/`에 저장한다 |
| Evidence Report | `export-evidence-report.ps1`가 runtime/scenario summary를 `target/evidence-report.md`로 묶고 API key와 질문 원문 전체를 제외한다 |

## 2차 답변 품질 평가 항목

| Metric | 설명 |
|---|---|
| Critic Status | `CriticAgent` 검토 후 기대 상태와 일치해야 한다 |
| Korean Visible Text | reasoning과 follow-up question이 영어 중심 문장으로 노출되지 않아야 한다 |
| Forbidden Copy | demo, sample, 샘플, 데모, 미니 프로젝트 같은 포트폴리오/시연 문구를 차단한다 |
| Decisive Legal Phrase | 문제없습니다, 합법입니다, 위법입니다 같은 단정 문구를 차단한다 |
| Min Cited Articles | OK 또는 LOW_CONFIDENCE 답변은 인용 조문을 유지해야 한다 |
| Valid Article Numbers | 평가셋에 지정한 조문번호 중 최소 1건이 citedArticles에 있어야 한다 |
| Reasoning Article Numbers | reasoning에 등장하는 조문번호가 citedArticles/expected article number 범위를 벗어나지 않아야 한다 |
| Markdown Marker Limit | LLM이 과한 강조 마크업을 생성하지 않도록 제한한다 |

## 기준 질문

```json
[
  {
    "question": "기술자료를 해외 법인에 제공해도 되나요?",
    "expectedStatus": "OK",
    "minCitedArticles": 1,
    "minVectorHits": 1,
    "expectedCitations": [
      {"lawTitle": "방위사업법", "articleNumber": "제57조"}
    ]
  },
  {
    "question": "이거 해도 돼?",
    "expectedStatus": "INSUFFICIENT_INFO"
  },
  {
    "question": "대외무역법 시행일은 언제인가요?",
    "expectedStatus": "OK"
  },
  {
    "question": "대외무역법 제19조의2는 이전과 어떻게 바뀌었나요?",
    "expectedStatus": "OK"
  }
]
```

실제 고정 질문셋은 `harness/questions.json`에 있으며 현재 12개 질문을 관리한다. 무역안보/방산/기술보호 도메인 질문 중 Spring seed corpus로 검증 가능한 항목을 우선 반영했다.

## 현재 파일

```text
harness/questions.json
harness/answer-quality-v2.json
```

현재 자동 테스트:

```text
src/test/java/com/example/lawassistant/eval/EvaluationHarnessTest.java
src/test/java/com/example/lawassistant/eval/AnswerQualityHarnessTest.java
src/test/java/com/example/lawassistant/docs/RepositoryTextSafetyTest.java
src/test/java/com/example/lawassistant/docs/EnvironmentConfigurationContractTest.java
```

검증 항목:

- API 응답 HTTP 200
- 기대 `status`
- 최소 `citedArticles` 개수
- 정상 질문의 최소 `vectorHits`
- 기대 법령/조문 쌍 중 최소 1건 citation 포함
- 탱크/방산물자, 전략물자 판정, 관세법상 수출신고 등 도메인별 recall 케이스
- 메타데이터/개정비교 질문의 `questionType` 라우팅과 citation 유지
- 개정비교 응답의 cited article에 이전 회차 `historicalEntries` 최소 개수 포함
- 답변 본문이 인용 목록에 없는 조문번호를 새로 말하지 않는지 확인
- 법령명/조문번호가 명시된 질문에서 lexical ranking이 정확한 후보를 우선 배치하는지 확인
- 띄어쓰기 차이가 있는 법령명과 실무 약칭이 canonical 법령명 검색어로 확장되는지 확인
- `verify-runtime.ps1`가 `/healthz`, `/api/v1/health`, `/api/v1/ask`, `/api/v1/admin/status`, 선택적 `/api/v1/admin/reindex` 계약을 유지하는지 확인
- `verify-runtime.ps1`가 runtime evidence JSON과 summary를 저장하는지 테스트로 고정
- `preflight.ps1`가 OpenRouter/Cohere/Qdrant 필수 설정, 포트/숫자 설정, 민감정보 로그 플래그를 확인하는지 테스트로 고정
- `EnvironmentConfigurationContractTest`가 `application.yml`, `docker-compose.yml`, `preflight.ps1`에서 쓰는 env 이름이 `.env.example`에 문서화되어 있는지 확인
- OpenRouter chat 응답이 JSON object가 아닐 때 원문 노출 없이 포맷 실패로 처리하고, AnswerWriter가 1회 재요청 후 반복 실패를 전파하는지 확인
- `verify-local.ps1`가 서버 비의존 검증을 한 번에 실행하고 runtime 검증은 별도 실행으로 남기는지 확인
- `export-evidence-report.ps1`가 runtime/scenario summary를 제출 가능한 Markdown 리포트로 묶고 비밀값과 질문 원문 전체를 포함하지 않는지 확인
- `RepositoryTextSafetyTest`가 문서, 스크립트, Java 소스/테스트, harness 파일의 깨진 한국어 인코딩 패턴을 차단

## 변경 유형별 검증

| 변경 유형 | 최소 검증 |
|---|---|
| 답변 생성, citation, status 정책 | `AskResponseInvariantTest`, `EvaluationHarnessTest` |
| 질문 분석, 검색어 생성, retrieval | `EvaluationHarnessTest` + 관련 질문 추가 |
| 검색 후보 점수화, reranker 전처리 | `RetrievalAgentRerankerTest`, `EvaluationHarnessTest` |
| Controller/API contract | `AskControllerIntegrationTest` 또는 `AdminControllerIntegrationTest` |
| LLM/Embedding/Vector/Reranker provider | `ProviderInterfaceTest`, `OpenRouterChatModelClientTest`, `AnswerWriterAgentTest` |
| Search log 또는 agent trace | `AdminControllerIntegrationTest` |
| 실패 감사 경로 | `AskOrchestratorServiceFailureTest` |
| 런타임 검증 스크립트 | `ScenarioScriptContractTest` |
| provider/env 사전 점검 | `ScenarioScriptContractTest` |
| 로컬 종합 검증 스크립트 | `ScenarioScriptContractTest`, `scripts/verify-local.ps1` |
| 검증 리포트 산출 | `ScenarioScriptContractTest`, `scripts/export-evidence-report.ps1` |
| 여러 agent를 넘나드는 변경 | `mvn test` |

답변 품질 정책만 바꾸는 경우에는 최소한 다음을 실행한다.

```bash
mvn -Dtest=AnswerQualityHarnessTest,CriticAgentTest,AnswerWriterAgentTest test
```

## 질문셋 운영 규칙

- 사용자에게 보이는 답변 동작이 바뀌면 `harness/questions.json`에 고정 질문을 추가한다.
- `status=OK` 케이스는 `minCitedArticles`를 1 이상으로 둔다.
- `status=OK` 케이스는 가능한 한 `expectedCitations`에 법령명과 조문 번호를 명시한다.
- 개정비교 케이스는 `minHistoricalEntries`를 1 이상으로 둔다.
- 모호한 질문, 정보 부족 질문, 근거가 없는 질문은 `INSUFFICIENT_INFO` 케이스로 유지한다.
- 질문셋은 한국어 질문을 기준으로 관리한다.
- v2 품질 평가셋은 실제 회사 데이터가 아니라 평가용 reasoning과 조문번호만 사용한다.
- 실제 회사명, 내부 URL, 계정, API key, 비공개 사건 정보는 질문셋에 넣지 않는다.
- 한 질문은 하나의 회귀 목적만 갖게 작성한다.

## 완료 기준

변경을 완료했다고 말하려면 다음 중 하나를 명시한다.

- 실행한 테스트 명령과 결과
- 실행하지 못한 테스트와 그 이유
- 문서만 변경한 경우에도 최소한 영향 범위가 코드 동작을 바꾸지 않는다는 판단

권장 전체 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```
