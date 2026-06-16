package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.dto.ArticleHistoryEntryDto;
import com.example.lawassistant.dto.CandidateLawDto;
import com.example.lawassistant.dto.CitedArticleDto;
import com.example.lawassistant.dto.EffectiveBasisDto;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterClientException;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterResponseFormatException;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.service.model.RetrievalHit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnswerWriterAgent {

    private static final String INSUFFICIENT_REASONING = "질문에 관련 조문을 특정할 수 있는 정보가 부족합니다. 품목 또는 자료의 종류, 제공받는 기관, 목적지 국가, 제공 목적을 함께 입력해 주세요.";
    private static final String CORRECTIVE_PROMPT = """
            직전 응답은 서비스 응답 기준을 충족하지 못했습니다.
            반드시 한국어만 사용하고, 데모/샘플/미니 프로젝트 같은 표현은 쓰지 마세요.
            제공된 조문 컨텍스트만 근거로 삼고, 허가/위법 여부를 단정하지 마세요.
            JSON만 반환하고 다음 key를 사용하세요: reasoning, followUpQuestions, confidence.
            """;
    private static final List<String> FORBIDDEN_COPY = List.of(
            "demo",
            "sample",
            "mini project",
            "this response",
            "demo project",
            "sample articles",
            "데모",
            "샘플",
            "미니 프로젝트",
            "실제 회사 내부 데이터"
    );
    private static final List<String> DECISIVE_LEGAL_PHRASES = List.of(
            "위법입니다",
            "합법입니다",
            "문제없습니다",
            "허가됩니다",
            "불허됩니다",
            "반드시 위반"
    );

    private final ChatModelClient chatModelClient;
    private final String llmProvider;
    private final ArticleRepository articleRepository;

    @Autowired
    public AnswerWriterAgent(
            ChatModelClient chatModelClient,
            @Value("${app.llm.provider:mock}") String llmProvider,
            ArticleRepository articleRepository
    ) {
        this.chatModelClient = chatModelClient;
        this.llmProvider = llmProvider;
        this.articleRepository = articleRepository;
    }

    public AnswerWriterAgent(ChatModelClient chatModelClient, @Value("${app.llm.provider:mock}") String llmProvider) {
        this.chatModelClient = chatModelClient;
        this.llmProvider = llmProvider;
        this.articleRepository = null;
    }

    public DraftAnswer write(
            String question,
            QuestionInterpretationDto interpretation,
            List<RetrievalHit> hits,
            EffectiveBasisDto effectiveBasis,
            boolean enoughEvidence
    ) {
        return write(question, interpretation, hits, effectiveBasis, enoughEvidence, false);
    }

    public DraftAnswer write(
            String question,
            QuestionInterpretationDto interpretation,
            List<RetrievalHit> hits,
            EffectiveBasisDto effectiveBasis,
            boolean enoughEvidence,
            boolean weakEvidence
    ) {
        if (!enoughEvidence) {
            return new DraftAnswer(
                    SearchStatus.INSUFFICIENT_INFO,
                    List.of(),
                    List.of(),
                    INSUFFICIENT_REASONING,
                    List.of(
                            "어떤 품목, 자료 또는 기술을 다루는지 알려주세요.",
                            "제공받는 기관 또는 최종 사용자가 누구인지 알려주세요.",
                            "목적지 국가와 제공 목적을 알려주세요."
                    ),
                    0.0
            );
        }

        List<CitedArticleDto> citedArticles = hits.stream()
                .map(hit -> toCitedArticle(hit.article(), hit.reason()))
                .toList();
        List<CandidateLawDto> candidateLaws = toCandidateLaws(hits);

        if (interpretation.questionType() == QuestionType.METADATA) {
            return capWeakEvidence(metadataDraft(hits, citedArticles, candidateLaws), weakEvidence);
        }
        if (interpretation.questionType() == QuestionType.REVISION_COMPARE) {
            return capWeakEvidence(revisionCompareDraft(hits, citedArticles, candidateLaws), weakEvidence);
        }

        if (!"openrouter".equalsIgnoreCase(llmProvider)) {
            return capWeakEvidence(fallbackDraft(interpretation, citedArticles, candidateLaws), weakEvidence);
        }

        try {
            List<ChatMessage> messages = buildMessages(question, interpretation, hits, effectiveBasis);
            Map<String, Object> llmResult;
            DraftAnswer draft;
            try {
                llmResult = chatModelClient.generateJson(messages, "AnswerDraft");
                draft = draftFromLlmResult(llmResult, interpretation, citedArticles, candidateLaws);
            } catch (OpenRouterResponseFormatException ex) {
                llmResult = chatModelClient.generateJson(
                        responseFormatRetryMessages(messages),
                        "AnswerDraft"
                );
                draft = draftFromLlmResult(llmResult, interpretation, citedArticles, candidateLaws);
            }
            if (isAcceptable(draft)) {
                return capWeakEvidence(draft, weakEvidence);
            }

            Map<String, Object> retryResult = chatModelClient.generateJson(
                    correctiveMessages(messages, llmResult),
                    "AnswerDraft"
            );
            DraftAnswer retried = draftFromLlmResult(retryResult, interpretation, citedArticles, candidateLaws);
            if (isAcceptable(retried)) {
                return capWeakEvidence(retried, weakEvidence);
            }

            return capWeakEvidence(fallbackDraft(interpretation, citedArticles, candidateLaws), weakEvidence);
        } catch (OpenRouterClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return capWeakEvidence(fallbackDraft(interpretation, citedArticles, candidateLaws), weakEvidence);
        }
    }

    private DraftAnswer capWeakEvidence(DraftAnswer draft, boolean weakEvidence) {
        if (!weakEvidence || draft.status() != SearchStatus.OK) {
            return draft;
        }
        return new DraftAnswer(
                SearchStatus.LOW_CONFIDENCE,
                draft.candidateLaws(),
                draft.citedArticles(),
                "검색된 조문은 있으나 질문과의 직접 관련성이 충분히 높지 않습니다. " + draft.reasoning(),
                draft.followUpQuestions(),
                Math.min(draft.confidence(), 0.34)
        );
    }

    private List<ChatMessage> buildMessages(
            String question,
            QuestionInterpretationDto interpretation,
            List<RetrievalHit> hits,
            EffectiveBasisDto effectiveBasis
    ) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Article article = hits.get(i).article();
            context.append("[").append(i + 1).append("] ")
                    .append(article.getLaw().getTitle()).append(" ")
                    .append(article.getArticleNumber()).append(" - ")
                    .append(article.getArticleTitle()).append("\n")
                    .append(article.getContent()).append("\n")
                    .append("검색 근거: ").append(hits.get(i).reason()).append("\n\n");
        }

        String prompt = """
                당신은 한국어로만 답변하는 법령 조사 보조 서비스입니다.
                반드시 한국어만 사용하세요. 사용자가 영어로 질문해도 답변, 추가 확인 질문, 설명은 모두 한국어로 작성하세요.
                제공된 조문만 근거로 사용하고, 법령 문구나 조문을 임의로 만들지 마세요.
                법률 자문처럼 단정하지 말고, 실무 검토용 요약으로 간결하게 작성하세요.
                JSON만 반환하고 값도 한국어로 작성하세요. 정확히 다음 key를 사용하세요:
                - reasoning: string
                - followUpQuestions: array of Korean strings
                - confidence: number from 0.0 to 1.0

                사용자 질문:
                %s

                질문 해석:
                action=%s
                object=%s
                domains=%s
                uncertainties=%s
                effectiveBasis=%s indexedAt=%s asOf=%s

                검색된 관련 조문:
                %s
                """.formatted(
                question,
                interpretation.action(),
                interpretation.object(),
                interpretation.domainCandidates(),
                interpretation.uncertainties(),
                effectiveBasis.snapshotVersion(),
                effectiveBasis.indexedAt(),
                effectiveBasis.asOf() == null ? "현재 유효 조문" : effectiveBasis.asOf(),
                context
        );
        return List.of(new ChatMessage("user", prompt));
    }

    private List<ChatMessage> correctiveMessages(List<ChatMessage> originalMessages, Map<String, Object> previousResult) {
        List<ChatMessage> messages = new ArrayList<>(originalMessages);
        messages.add(new ChatMessage("assistant", previousResult.toString()));
        messages.add(new ChatMessage("user", CORRECTIVE_PROMPT));
        return messages;
    }

    private List<ChatMessage> responseFormatRetryMessages(List<ChatMessage> originalMessages) {
        List<ChatMessage> messages = new ArrayList<>(originalMessages);
        messages.add(new ChatMessage("user", """
                이전 응답은 JSON object로 파싱되지 않았습니다.
                설명 문장, 마크다운, 코드블록 없이 다음 key만 가진 JSON object를 다시 반환하세요: reasoning, followUpQuestions, confidence.
                모든 값은 한국어로 작성하세요.
                """));
        return messages;
    }

    private DraftAnswer draftFromLlmResult(
            Map<String, Object> llmResult,
            QuestionInterpretationDto interpretation,
            List<CitedArticleDto> citedArticles,
            List<CandidateLawDto> candidateLaws
    ) {
        double confidence = boundedDouble(llmResult.get("confidence"), 0.75);
        return new DraftAnswer(
                confidence < 0.35 ? SearchStatus.LOW_CONFIDENCE : SearchStatus.OK,
                candidateLaws,
                citedArticles,
                rawTextValue(llmResult, "reasoning"),
                rawStringListValue(llmResult, "followUpQuestions", interpretation.uncertainties()),
                confidence
        );
    }

    private DraftAnswer fallbackDraft(
            QuestionInterpretationDto interpretation,
            List<CitedArticleDto> citedArticles,
            List<CandidateLawDto> candidateLaws
    ) {
        return new DraftAnswer(
                SearchStatus.OK,
                candidateLaws,
                citedArticles,
                fallbackReasoning(citedArticles),
                interpretation.uncertainties().stream()
                        .map(this::koreanFollowUp)
                        .toList(),
                0.72
        );
    }

    private String fallbackReasoning(List<CitedArticleDto> citedArticles) {
        CitedArticleDto first = citedArticles.get(0);
        return first.lawTitle() + " " + first.articleNumber()
                + "가 현재 질문과 가장 가까운 근거 조문입니다. 실제 판단 전에는 인용 조문과 함께 품목 또는 자료의 성격, 제공받는 기관, 목적지 국가, 제공 목적을 추가로 확인해야 합니다.";
    }

    private CitedArticleDto toCitedArticle(Article article, String reason) {
        return new CitedArticleDto(
                article.getId(),
                article.getLaw().getId(),
                article.getLaw().getTitle(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent(),
                reason,
                article.getContentHash(),
                article.getEffectiveFrom(),
                article.getEffectiveTo(),
                article.getAmendmentKind(),
                article.getPreviousArticleId()
        );
    }

    private List<CandidateLawDto> toCandidateLaws(List<RetrievalHit> hits) {
        Map<Long, CandidateLawDto> laws = new LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            Article article = hit.article();
            laws.putIfAbsent(
                    article.getLaw().getId(),
                    new CandidateLawDto(
                            article.getLaw().getId(),
                            article.getLaw().getTitle(),
                            article.getLaw().getLawType(),
                            hit.reason(),
                            hit.score()
                    )
            );
        }
        return List.copyOf(laws.values());
    }

    private DraftAnswer metadataDraft(
            List<RetrievalHit> hits,
            List<CitedArticleDto> citedArticles,
            List<CandidateLawDto> candidateLaws
    ) {
        RetrievalHit selected = hits.stream()
                .filter(hit -> hit.article().getLaw().getLawType() == LawType.LAW)
                .findFirst()
                .orElse(hits.get(0));
        Article article = selected.article();
        Law law = article.getLaw();
        List<CitedArticleDto> selectedCitations = List.of(toCitedArticle(article, selected.reason()));
        List<CandidateLawDto> selectedCandidates = List.of(toCandidateLaw(selected));
        List<String> facts = new ArrayList<>();
        addFact(facts, "법령번호", law.getLawNumber());
        addFact(facts, "제정일", formatDate(law.getEnactedAt()));
        addFact(facts, "최근 개정일", formatDate(law.getLastAmendedAt()));
        addFact(facts, "시행일", formatDate(law.getEffectiveFrom()));

        if (facts.isEmpty()) {
            return new DraftAnswer(
                    SearchStatus.LOW_CONFIDENCE,
                    selectedCandidates,
                    selectedCitations,
                    law.getTitle() + "의 메타데이터가 충분하지 않습니다. 법령 상세 화면에서 원문 기준 정보를 추가로 확인해 주세요.",
                    List.of("확인하려는 기준일 또는 법령번호 항목을 구체적으로 알려주세요."),
                    0.35
            );
        }

        return new DraftAnswer(
                SearchStatus.OK,
                selectedCandidates,
                selectedCitations,
                law.getTitle() + "의 " + String.join(", ", facts)
                        + "입니다. 제공된 법령 메타데이터와 인용 조문을 함께 기준으로 확인했습니다.",
                List.of("시행령이나 시행규칙이 아니라 본법 기준인지 확인해 주세요."),
                0.85
        );
    }

    private DraftAnswer revisionCompareDraft(
            List<RetrievalHit> hits,
            List<CitedArticleDto> citedArticles,
            List<CandidateLawDto> candidateLaws
    ) {
        RetrievalHit currentHit = hits.stream()
                .filter(hit -> hit.article().getPreviousArticleId() != null)
                .findFirst()
                .orElse(hits.get(0));
        Article current = currentHit.article();
        List<CandidateLawDto> selectedCandidates = List.of(toCandidateLaw(currentHit));

        if (current.getPreviousArticleId() == null || articleRepository == null) {
            List<CitedArticleDto> selectedCitations = List.of(toCitedArticle(current, currentHit.reason()));
            return new DraftAnswer(
                    SearchStatus.LOW_CONFIDENCE,
                    selectedCandidates,
                    selectedCitations,
                    current.getLaw().getTitle() + " " + current.getArticleNumber()
                            + "의 현행 조문은 확인했지만, 비교할 이전 회차를 찾지 못했습니다.",
                    List.of("비교하려는 조문 번호와 기준일을 함께 알려주세요."),
                    0.42
            );
        }

        return articleRepository.findById(current.getPreviousArticleId())
                .map(previous -> revisionCompareDraft(
                        current,
                        previous,
                        List.of(toCitedArticleWithHistory(current, currentHit.reason(), previous)),
                        selectedCandidates
                ))
                .orElseGet(() -> new DraftAnswer(
                        SearchStatus.LOW_CONFIDENCE,
                        selectedCandidates,
                        List.of(toCitedArticle(current, currentHit.reason())),
                        current.getLaw().getTitle() + " " + current.getArticleNumber()
                                + "의 이전 회차 식별자는 있으나 해당 조문 본문을 찾지 못했습니다.",
                        List.of("비교하려는 기준일을 다시 확인해 주세요."),
                        0.42
                ));
    }

    private DraftAnswer revisionCompareDraft(
            Article current,
            Article previous,
            List<CitedArticleDto> citedArticles,
            List<CandidateLawDto> candidateLaws
    ) {
        String reasoning = current.getLaw().getTitle() + " " + current.getArticleNumber()
                + "는 현행 조문(시행일 " + formatDate(current.getEffectiveFrom()) + ")에서 "
                + quoteSnippet(current.getContent())
                + "로 정리되어 있습니다. 이전 회차(시행일 " + formatDate(previous.getEffectiveFrom())
                + ", 종료일 " + formatDate(previous.getEffectiveTo()) + ")는 "
                + quoteSnippet(previous.getContent())
                + "였습니다. 위 내용은 제공된 회차 본문 사이의 표현 차이를 요약한 것이며, 실제 적용 여부는 품목, 목적지, 최종 사용자 정보를 함께 확인해야 합니다.";
        return new DraftAnswer(
                SearchStatus.OK,
                candidateLaws,
                citedArticles,
                reasoning,
                List.of("비교 기준일과 적용하려는 거래 시점을 확인해 주세요."),
                0.82
        );
    }

    private void addFact(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(label + " " + value);
        }
    }

    private CandidateLawDto toCandidateLaw(RetrievalHit hit) {
        Article article = hit.article();
        return new CandidateLawDto(
                article.getLaw().getId(),
                article.getLaw().getTitle(),
                article.getLaw().getLawType(),
                hit.reason(),
                hit.score()
        );
    }

    private CitedArticleDto toCitedArticleWithHistory(Article article, String reason, Article previous) {
        return new CitedArticleDto(
                article.getId(),
                article.getLaw().getId(),
                article.getLaw().getTitle(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent(),
                reason,
                article.getContentHash(),
                article.getEffectiveFrom(),
                article.getEffectiveTo(),
                article.getAmendmentKind(),
                article.getPreviousArticleId(),
                List.of(toHistoryEntry(previous, false))
        );
    }

    private ArticleHistoryEntryDto toHistoryEntry(Article article, boolean current) {
        return new ArticleHistoryEntryDto(
                article.getId(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent(),
                article.getContentHash(),
                article.getEffectiveFrom(),
                article.getEffectiveTo(),
                article.getAmendmentKind(),
                article.getPreviousArticleId(),
                current
        );
    }

    private String formatDate(LocalDate date) {
        return date == null ? "정보 없음" : date.toString();
    }

    private String quoteSnippet(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 90) {
            normalized = normalized.substring(0, 90).strip() + "...";
        }
        return "'" + normalized + "'";
    }

    private String rawTextValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (raw instanceof String text) {
            return text.trim();
        }
        return "";
    }

    private List<String> rawStringListValue(Map<String, Object> value, String key, List<String> fallback) {
        Object raw = value.get(key);
        if (!(raw instanceof List<?> items)) {
            return fallback.stream().map(this::koreanFollowUp).toList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String text && !text.isBlank()) {
                result.add(text.trim());
            }
        }
        if (result.isEmpty()) {
            return fallback.stream().map(this::koreanFollowUp).toList();
        }
        return result.stream().limit(5).toList();
    }

    private String koreanFollowUp(String value) {
        return switch (value) {
            case "item or data type", "exact item type", "품목 또는 자료의 종류", "구체적인 품목" -> "품목 또는 자료의 구체적인 종류를 확인해 주세요.";
            case "recipient", "recipient or end user", "제공받는 기관", "제공받는 기관 또는 최종 사용자" -> "제공받는 기관 또는 최종 사용자를 확인해 주세요.";
            case "destination country" -> "목적지 국가를 확인해 주세요.";
            case "목적지 국가" -> "목적지 국가를 확인해 주세요.";
            case "purpose", "제공 목적" -> "제공 또는 수출 목적을 확인해 주세요.";
            case "current effective date", "검토 기준일" -> "검토 기준일에 유효한 조문인지 확인해 주세요.";
            default -> isEnglishDominant(value) ? "해당 항목을 확인해 주세요." : value + " 항목을 확인해 주세요.";
        };
    }

    private boolean isEnglishDominant(String value) {
        int latin = 0;
        int hangul = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latin++;
            } else if (ch >= '가' && ch <= '힣') {
                hangul++;
            }
        }
        return latin >= 20 && latin > hangul * 2;
    }

    private boolean isAcceptable(DraftAnswer draft) {
        if ((draft.status() == SearchStatus.OK || draft.status() == SearchStatus.LOW_CONFIDENCE)
                && draft.citedArticles().isEmpty()) {
            return false;
        }
        if (!isKoreanServiceText(draft.reasoning())) {
            return false;
        }
        return draft.followUpQuestions().stream().allMatch(this::isKoreanServiceText);
    }

    private boolean isKoreanServiceText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        if (FORBIDDEN_COPY.stream().anyMatch(lower::contains)) {
            return false;
        }
        if (DECISIVE_LEGAL_PHRASES.stream().anyMatch(value::contains)) {
            return false;
        }
        return !isEnglishDominant(value);
    }

    private double boundedDouble(Object raw, double fallback) {
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            return fallback;
        }
        if (value > 1.0 && value <= 100.0) {
            value = value / 100.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record DraftAnswer(
            SearchStatus status,
            List<CandidateLawDto> candidateLaws,
            List<CitedArticleDto> citedArticles,
            String reasoning,
            List<String> followUpQuestions,
            double confidence,
            String errorMessage
    ) {
        public DraftAnswer(
                SearchStatus status,
                List<CandidateLawDto> candidateLaws,
                List<CitedArticleDto> citedArticles,
                String reasoning,
                List<String> followUpQuestions,
                double confidence
        ) {
            this(status, candidateLaws, citedArticles, reasoning, followUpQuestions, confidence, null);
        }
    }
}
