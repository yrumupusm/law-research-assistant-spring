package com.example.lawassistant.service;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryAnalyzerAgent {

    private static final Pattern ARTICLE_NUMBER_PATTERN = Pattern.compile("제\\s*\\d+조(?:의\\s*\\d+)?");
    private static final List<String> KNOWN_LAW_TITLES = List.of(
            "대외무역법",
            "방위사업법",
            "관세법",
            "외국환거래법",
            "국가첨단전략산업 경쟁력 강화 및 보호에 관한 특별조치법",
            "산업기술의 유출방지 및 보호에 관한 법률",
            "국방과학기술혁신 촉진법",
            "군수품관리법"
    );
    private static final Map<String, String> LAW_TITLE_ALIASES = lawTitleAliases();

    public QuestionInterpretationDto analyze(String question) {
        String normalized = question == null ? "" : question.trim();
        QuestionType type = classify(normalized);
        String action = inferAction(normalized);
        String object = inferObject(normalized);
        List<String> domains = inferDomains(normalized);
        List<String> uncertainties = inferUncertainties(type);
        List<String> queries = buildQueries(normalized, action, object, domains);

        return new QuestionInterpretationDto(action, object, domains, uncertainties, queries, type);
    }

    private QuestionType classify(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (isRevisionCompareQuestion(question, lower)) {
            return QuestionType.REVISION_COMPARE;
        }
        if (isMetadataQuestion(question, lower)) {
            return QuestionType.METADATA;
        }
        if (question.length() < 8
                || containsAny(question, "\uC774\uAC70", "\uADF8\uAC70", "\uD574\uB3C4 \uB3FC")
                || containsAny(lower, "can i do this", "is this allowed", "can we do this")) {
            return QuestionType.INSUFFICIENT;
        }
        if (containsAny(question, "\uD574\uC57C", "\uAC00\uB2A5", "\uD544\uC694", "\uC81C\uACF5", "\uC218\uCD9C", "\uBC18\uCD9C")
                || containsAny(lower, "can i", "should", "need", "provide", "export", "transfer")) {
            return QuestionType.CONFIRMATORY;
        }
        return QuestionType.EXPLORATORY;
    }

    private boolean isRevisionCompareQuestion(String question, String lower) {
        return containsAny(
                question,
                "바뀐", "바뀌", "변경", "달라", "개정 내용", "개정 전", "개정 후", "개정 이력",
                "이전과", "이전 조문", "이전 회차", "비교", "신설"
        ) || containsAny(lower, "changed", "previous", "compare", "revision history", "amendment history");
    }

    private boolean isMetadataQuestion(String question, String lower) {
        return containsAny(question, "시행일", "개정일", "제정일", "공포일", "법령번호")
                || containsAny(lower, "effective date", "revision date", "amendment date", "law number");
    }

    private String inferAction(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (containsAny(question, "\uC218\uCD9C") || lower.contains("export")) {
            return "수출";
        }
        if (containsAny(question, "\uC81C\uACF5", "\uACF5\uC720") || containsAny(lower, "provide", "providing", "share")) {
            return "제공";
        }
        if (containsAny(question, "\uBC18\uCD9C", "\uC774\uC804") || containsAny(lower, "transfer", "transferring")) {
            return "이전";
        }
        if (containsAny(question, "\uAC80\uD1A0", "\uD655\uC778") || containsAny(lower, "review", "check")) {
            return "검토";
        }
        if (containsAny(question, "근거", "설립", "역할") || containsAny(lower, "basis", "legal basis", "role")) {
            return "근거확인";
        }
        return null;
    }

    private String inferObject(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (containsAny(question, "\uAE30\uC220\uC790\uB8CC", "\uAE30\uC220 \uC790\uB8CC")
                || lower.contains("technical data")) {
            return "기술자료";
        }
        if (containsAny(question, "\uC804\uB7B5\uBB3C\uC790", "\uC804\uB7B5 \uBB3C\uC790")
                || containsAny(question, "판정")
                || lower.contains("strategic item")) {
            return "전략물자";
        }
        if (containsAny(question, "\uBC29\uC0B0\uBB3C\uC790", "\uAD70\uC218\uD488", "\uAD70\uC0AC", "탱크")
                || containsAny(lower, "tank", "defense material", "military-related")) {
            return "방산물자";
        }
        if (containsAny(question, "\uC790\uB8CC", "\uB370\uC774\uD130") || containsAny(lower, "data", "material")) {
            return "자료";
        }
        if (containsAny(question, "무역안보관리원")) {
            return "무역안보관리원";
        }
        for (String lawTitle : KNOWN_LAW_TITLES) {
            if (containsIgnoringSpaces(question, lawTitle)) {
                return lawTitle;
            }
        }
        for (Map.Entry<String, String> alias : LAW_TITLE_ALIASES.entrySet()) {
            if (containsIgnoringSpaces(question, alias.getKey())) {
                return alias.getValue();
            }
        }
        return null;
    }

    private List<String> inferDomains(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        List<String> domains = new ArrayList<>();
        boolean customsQuestion = containsAny(question, "관세법", "수출신고", "통관")
                || containsAny(lower, "customs", "export declaration");
        if (customsQuestion) {
            domains.add("통관");
        }
        if (containsAny(question, "\uC218\uCD9C", "\uD574\uC678", "\uC804\uB7B5\uBB3C\uC790")
                || containsAny(lower, "export", "overseas", "strategic item")) {
            if (!customsQuestion || containsAny(question, "전략물자", "방산", "기술자료", "탱크")
                    || containsAny(lower, "strategic item", "defense", "technical data", "tank")) {
                domains.add("수출통제");
            }
        }
        if (containsAny(question, "\uBC29\uC0B0", "\uAD70\uC218", "\uAD70\uC0AC", "탱크")
                || containsAny(lower, "tank", "defense")) {
            domains.add("방산");
        }
        if (containsAny(question, "\uAE30\uC220") || containsAny(lower, "technical", "technology")) {
            domains.add("기술이전");
        }
        if (domains.isEmpty()) {
            domains.add("일반 법령 조사");
        }
        if (containsAny(question, "무역안보관리원")) {
            domains.add("무역안보");
        }
        return domains;
    }

    private List<String> inferUncertainties(QuestionType type) {
        if (type == QuestionType.INSUFFICIENT) {
            return List.of("품목 또는 자료의 종류", "제공받는 기관", "목적지 국가", "제공 목적");
        }
        return List.of("구체적인 품목", "제공받는 기관 또는 최종 사용자", "목적지 국가", "검토 기준일");
    }

    private List<String> buildQueries(String question, String action, String object, List<String> domains) {
        List<String> queries = new ArrayList<>();
        if (action != null && object != null) {
            queries.add(action + " " + object);
        }
        if (object != null) {
            queries.add(object);
        }
        if ("제공".equals(action) && "기술자료".equals(object)) {
            queries.add("기술자료 제공");
            queries.add("외부 기관");
            queries.add("해외 수령자");
        }
        if ("수출".equals(action) || domains.contains("수출통제")) {
            if (domains.contains("통관") && !domains.contains("수출통제")) {
                queries.add("관세법 제241조");
                queries.add("수출 신고");
                queries.add("품명 규격 수량 가격");
            } else {
                queries.add("전략물자 수출");
                queries.add("전략물자 판정");
                queries.add("목적지 국가");
                queries.add("최종 사용자");
            }
        }
        if ("이전".equals(action) || domains.contains("방산")) {
            queries.add("방산물자 수출");
            queries.add("방산물자 이전");
            queries.add("탱크 방산물자");
            queries.add("허가 요건");
        }
        if ("전략물자".equals(object)) {
            queries.add("대외무역법 제20조");
            queries.add("전략물자 판정 신청");
        }
        if ("무역안보관리원".equals(object)) {
            queries.add("무역안보관리원");
            queries.add("대외무역법 제25조");
            queries.add("무역안보 업무");
        }
        addKnownLawTerms(question, queries);
        queries.addAll(domains);
        queries.add(question);
        return queries.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private void addKnownLawTerms(String question, List<String> queries) {
        for (String lawTitle : KNOWN_LAW_TITLES) {
            if (containsIgnoringSpaces(question, lawTitle)) {
                queries.add(lawTitle);
            }
        }
        for (Map.Entry<String, String> alias : LAW_TITLE_ALIASES.entrySet()) {
            if (containsIgnoringSpaces(question, alias.getKey())) {
                queries.add(alias.getValue());
            }
        }
        var matcher = ARTICLE_NUMBER_PATTERN.matcher(question);
        while (matcher.find()) {
            queries.add(matcher.group().replace(" ", ""));
        }
    }

    private static Map<String, String> lawTitleAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("대외 무역법", "대외무역법");
        aliases.put("방위 사업법", "방위사업법");
        aliases.put("외환거래법", "외국환거래법");
        aliases.put("외국환 거래법", "외국환거래법");
        aliases.put("국가첨단전략산업법", "국가첨단전략산업 경쟁력 강화 및 보호에 관한 특별조치법");
        aliases.put("국가첨단전략산업 특별법", "국가첨단전략산업 경쟁력 강화 및 보호에 관한 특별조치법");
        aliases.put("산업기술보호법", "산업기술의 유출방지 및 보호에 관한 법률");
        aliases.put("산업기술 유출방지법", "산업기술의 유출방지 및 보호에 관한 법률");
        aliases.put("국방과학기술혁신법", "국방과학기술혁신 촉진법");
        aliases.put("국방과학기술 혁신법", "국방과학기술혁신 촉진법");
        aliases.put("군수품 관리법", "군수품관리법");
        return Map.copyOf(aliases);
    }

    private boolean containsIgnoringSpaces(String value, String candidate) {
        return compact(value).contains(compact(candidate));
    }

    private String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\s·ㆍ\\-_/()]+", "");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
