# Agent Orchestration

이 프로젝트는 완전 자율형 multi-agent가 아니라 역할 기반 agent pipeline이다.

| Agent | 책임 |
|---|---|
| QueryAnalyzerAgent | 질문 유형, 행위, 대상, 검색어 추출 |
| RetrievalAgent | 관련 조문 검색 |
| EvidenceValidatorAgent | 답변 가능한 근거가 있는지 판단하고 약한 근거를 표시 |
| AnswerWriterAgent | cited article 기반 답변 초안 생성, metadata/revision 질의 결정적 응답, 한국어/금지문구/단정표현 품질 게이트, 1회 보정 재시도 |
| CriticAgent | citation invariant와 사용자 노출 문구의 한국어/금지문구/단정표현 최종 검증 |
| SearchLogAgent | 검색 로그 저장 |
| AskAuditLogger | 질문 전문 없이 완료 이벤트 JSON payload 저장 |

## 면접용 표현

완전 자율 agent보다는 역할 기반 agent pipeline으로 설계했습니다. 질문 분석, 검색, 근거 검증, 답변 생성, 최종 검토를 분리했고, Orchestrator가 순서와 실패 처리를 관리합니다.
LLM 응답은 그대로 노출하지 않고 한국어 응답 여부, 인용 근거, 데모성 문구, 단정적인 법률 판단 표현을 확인한 뒤 문제가 있으면 보정 재시도를 수행하도록 구성했습니다.
OpenRouter chat client는 `response_format=json_object`를 사용하고, 모델이 fenced JSON이나 앞뒤 설명을 섞어 반환해도 첫 JSON object를 추출해 답변 품질 게이트로 넘깁니다.
최종 단계의 `CriticAgent`는 reasoning뿐 아니라 follow-up question까지 검사해 영어 중심 문장, 데모성 문구, 단정적인 법률 판단 표현이 화면에 노출되지 않도록 한 번 더 차단합니다.
검색 결과가 존재하더라도 관련성 점수가 낮으면 `EvidenceValidatorAgent`가 weak evidence로 표시하고, `AnswerWriterAgent`는 해당 응답을 `LOW_CONFIDENCE`로 제한합니다.
관리자 agent trace는 `QueryAnalyzerAgent`, `RetrievalAgent`, `EvidenceValidatorAgent`, `AnswerWriterAndCritic` 순서로 저장되어 한 요청의 판단 흐름을 단계별로 재구성할 수 있습니다.
시행일·개정비교처럼 데이터로 확정 가능한 질문은 LLM 생성보다 법령 메타데이터와 조문 이력 기반의 결정적 응답을 우선 사용했습니다.
색인 스냅샷이 없거나 질문 처리 중 분석, 검색, 근거 확인, 답변 생성 단계에서 예외가 발생하면 `FAILED` 응답으로 전환하고, 같은 `requestId`로 실패 search log와 failed agent trace를 남깁니다. `CriticAgent`가 인용 누락이나 응답 품질 기준 위반을 발견해도 `FAILED`로 강등합니다. 클라이언트에는 provider 예외 원문 대신 단계별 또는 품질 게이트별 `errorMessage` 코드만 반환합니다.
요청 완료 로그는 `event=ask_complete payload={...}` 한 줄로 남기되 질문 전문을 제외하고 hash, 길이, 상태, latency, retrieval stats만 JSON payload로 기록해 운영 관측성과 정보 보호를 함께 맞췄습니다.
HTTP 요청 로그는 agent trace와 별도로 `event=http_request payload={...}` 한 줄로 남기며, query string과 request body 없이 method, path, status, elapsedMs만 기록합니다.
