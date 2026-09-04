package com.example.lawassistant.domain.enums;

import java.util.List;

/**
 * A user-selected research priority. This is not a legal classification of a product or technology.
 */
public enum ResearchArea {
    STRATEGIC_GOODS(
            List.of("전략물자 수출", "대외무역법 전략물자"),
            List.of("대외무역법", "관세법", "외국환거래법")
    ),
    DEFENSE_MATERIALS(
            List.of("방산물자 수출", "방위사업법 수출허가", "국방과학기술 수출"),
            List.of("방위사업법", "국방과학기술혁신 촉진법", "군수품관리법")
    );

    private final List<String> retrievalQueries;
    private final List<String> preferredLawTitles;

    ResearchArea(List<String> retrievalQueries, List<String> preferredLawTitles) {
        this.retrievalQueries = List.copyOf(retrievalQueries);
        this.preferredLawTitles = List.copyOf(preferredLawTitles);
    }

    public List<String> retrievalQueries() {
        return retrievalQueries;
    }

    public boolean matchesLawTitle(String lawTitle) {
        return preferredLawTitles.stream().anyMatch(title -> title.equals(lawTitle));
    }
}
