# Law Scope

이 서비스는 대한민국 전체 법령을 색인하지 않습니다.

원본 `law_research_assistant`는 공개 법령 소스를 바탕으로 제한된 법령 subset에서 출발했습니다. Spring Boot 버전은 전체 법령 DB 구축보다, 무역안보/방산/기술보호 도메인의 RAG 흐름에 집중합니다.

## Included Laws

- 대외무역법
- 방위사업법
- 관세법
- 외국환거래법
- 국가첨단전략산업 경쟁력 강화 및 보호에 관한 특별조치법
- 산업기술의 유출방지 및 보호에 관한 법률
- 국방과학기술혁신 촉진법
- 군수품관리법

현재 seed corpus는 위 8개 법령의 공개 도메인 요약 조문 21건으로 구성한다. 평가 질문은 전략물자 수출, 기술자료 해외 제공, 탱크/방산물자 수출, 전략물자 판정, 무역안보관리원 근거, 관세법상 수출신고, 개정비교를 포함한다.

## Why This Scope

- 목표는 법령 검색 서비스 운영이 아니라 AX/RAG workflow 설계 역량 설명이다.
- 작은 corpus가 query 분석, embedding, vector search, citation, scenario test를 면접에서 설명하기 쉽다.
- 회사 내부 데이터 없이도 업무 흐름을 축소 재현할 수 있다.

## Data Safety

seed article은 공개 도메인을 바탕으로 정리한 요약이다. 공식 법령 전문도 아니고 회사 내부 데이터도 아니다. 검색, citation, 답변 합성, 검색 로그, agent trace 검증용으로 사용한다.
