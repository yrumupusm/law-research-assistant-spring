package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lawassistant.domain.enums.LawType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarkdownLawParserTest {

    private final MarkdownLawParser parser = new MarkdownLawParser();

    @Test
    void parsesLegalizeKrFrontmatterAndArticles() {
        var parsed = parser.parse(markdown("""
                # 대외무역법

                ##### 제1조 (목적)
                이 법은 대외무역을 진흥하고 공정한 거래질서를 확립하는 것을 목적으로 한다.

                ##### 제19조의2 (전략물자의 수출허가)
                전략물자를 수출하려는 자는 허가를 받아야 한다.
                """), "kr/대외무역법/법률.md");

        assertThat(parsed.title()).isEqualTo("대외무역법");
        assertThat(parsed.lawType()).isEqualTo(LawType.LAW);
        assertThat(parsed.lawNumber()).isEqualTo("285575");
        assertThat(parsed.effectiveFrom()).hasToString("2026-10-22");
        assertThat(parsed.articles()).hasSize(2);
        assertThat(parsed.articles().get(1).articleNumber()).isEqualTo("제19조의2");
        assertThat(parsed.articles().get(1).articleTitle()).isEqualTo("전략물자의 수출허가");
    }

    @Test
    void duplicateBareArticleNumbersAreRestoredWithUnusedSuffixes() {
        var parsed = parser.parse(markdown("""
                ##### 제1조 (첫번째)
                본문 1

                ##### 제1조 (두번째)
                본문 2

                ##### 제1조 (세번째)
                본문 3
                """), "kr/test/법률.md");

        assertThat(parsed.articles())
                .extracting(article -> article.articleNumber())
                .containsExactly("제1조", "제1조의2", "제1조의3");
    }

    @Test
    void explicitSuffixIsPreservedAndNextBareDuplicateUsesNextAvailableSuffix() {
        var parsed = parser.parse(markdown("""
                ##### 제1조 (base)
                본문 1

                ##### 제1조의2 (explicit)
                본문 2

                ##### 제1조 (bare duplicate)
                본문 3
                """), "kr/test/법률.md");

        assertThat(parsed.articles())
                .extracting(article -> article.articleNumber())
                .containsExactly("제1조", "제1조의2", "제1조의3");
    }

    @Test
    void invalidMetadataDateFailsInsteadOfBeingSilentlyDropped() {
        String content = markdown("""
                ##### 제1조 (목적)
                본문
                """).replace("공포일자: 2026-04-21", "공포일자: not-a-date");

        assertThatThrownBy(() -> parser.parse(content, "kr/test/invalid-date.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid law metadata date");
    }

    @ParameterizedTest
    @CsvSource({
            "법률, LAW",
            "대통령령, ENFORCEMENT_DECREE",
            "시행령, ENFORCEMENT_DECREE",
            "총리령, ENFORCEMENT_RULE",
            "시행규칙, ENFORCEMENT_RULE",
            "국토교통부령, ENFORCEMENT_RULE",
            "보건복지부령, ENFORCEMENT_RULE"
    })
    void resolvesSupportedLawTypeValues(String lawTypeText, LawType expected) {
        var parsed = parser.parse(markdown(lawTypeText, """
                ##### 제1조 (목적)
                본문
                """), "kr/test/법령.md");

        assertThat(parsed.lawType()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "중앙선거관리위원회규칙",
            "조례",
            "조약"
    })
    void skipsOutOfScopeLawTypeValues(String lawTypeText) {
        var parsed = parser.parse(markdown(lawTypeText, """
                ##### 제1조 (목적)
                본문
                """), "kr/test/법령.md");

        assertThat(parsed).isNull();
    }

    private String markdown(String body) {
        return markdown("법률", body);
    }

    private String markdown(String lawType, String body) {
        return """
                ---
                제목: 대외무역법
                법령구분: %s
                법령MST: 285575
                공포일자: 2026-04-21
                시행일자: 2026-10-22
                상태: 시행
                ---

                %s
                """.formatted(lawType, body);
    }
}
