package com.example.lawassistant.config;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class ReferenceDataInitializer implements CommandLineRunner {

    private final SnapshotVersionRepository snapshotVersionRepository;
    private final LawRepository lawRepository;
    private final ArticleRepository articleRepository;
    private final boolean enabled;

    public ReferenceDataInitializer(
            SnapshotVersionRepository snapshotVersionRepository,
            LawRepository lawRepository,
            ArticleRepository articleRepository,
            @Value("${app.reference-data.enabled:true}") boolean enabled
    ) {
        this.snapshotVersionRepository = snapshotVersionRepository;
        this.lawRepository = lawRepository;
        this.articleRepository = articleRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (lawRepository.count() > 0) {
            return;
        }

        SnapshotVersion snapshot = snapshotVersionRepository.save(new SnapshotVersion(
                "law-domain-2026-001",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "public-reference-data"
        ));

        Law foreignTrade = saveLaw("law:foreign-trade-act", "\uB300\uC678\uBB34\uC5ED\uBC95", "LAW-001", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law defenseAcquisition = saveLaw("law:defense-acquisition-program-act", "\uBC29\uC704\uC0AC\uC5C5\uBC95", "LAW-002", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law customs = saveLaw("law:customs-act", "\uAD00\uC138\uBC95", "LAW-003", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law foreignExchange = saveLaw("law:foreign-exchange-transactions-act", "\uC678\uAD6D\uD658\uAC70\uB798\uBC95", "LAW-004", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law nationalAdvancedStrategy = saveLaw("law:national-advanced-strategic-industry-act", "\uAD6D\uAC00\uCCA8\uB2E8\uC804\uB7B5\uC0B0\uC5C5 \uACBD\uC7C1\uB825 \uAC15\uD654 \uBC0F \uBCF4\uD638\uC5D0 \uAD00\uD55C \uD2B9\uBCC4\uC870\uCE58\uBC95", "LAW-005", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law industrialTechnology = saveLaw("law:industrial-technology-protection-act", "\uC0B0\uC5C5\uAE30\uC220\uC758 \uC720\uCD9C\uBC29\uC9C0 \uBC0F \uBCF4\uD638\uC5D0 \uAD00\uD55C \uBC95\uB960", "LAW-006", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law defenseScience = saveLaw("law:defense-science-technology-innovation-act", "\uAD6D\uBC29\uACFC\uD559\uAE30\uC220\uD601\uC2E0 \uCD09\uC9C4\uBC95", "LAW-007", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        Law militarySupplies = saveLaw("law:military-supplies-management-act", "\uAD70\uC218\uD488\uAD00\uB9AC\uBC95", "LAW-008", snapshot, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));

        saveArticle(
                foreignTrade,
                "\uC81C19\uC870",
                "\uC804\uB7B5\uBB3C\uC790\uC758 \uACE0\uC2DC \uBC0F \uC218\uCD9C\uD1B5\uC81C",
                "전략물자와 관련 기술은 수출통제 검토 대상이 될 수 있습니다. 수출 전 품목 분류, 목적지 국가, 최종 사용자, 거래 목적을 확인해야 합니다.",
                1
        );
        Article oldForeignTradePermit = saveArticle(
                foreignTrade,
                "\uC81C19\uC870\uC7582",
                "\uC804\uB7B5\uBB3C\uC790\uC758 \uC218\uCD9C\uD5C8\uAC00",
                "전략물자 수출 허가 여부는 품목 분류와 목적지 국가를 중심으로 검토합니다. 최종 사용자와 거래 목적 정보가 부족하면 추가 확인이 필요합니다.",
                2,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 12, 31),
                "개정 전",
                null
        );
        saveArticle(
                foreignTrade,
                "\uC81C19\uC870\uC7582",
                "\uC804\uB7B5\uBB3C\uC790\uC758 \uC218\uCD9C\uD5C8\uAC00",
                "전략물자를 수출하려는 경우 관계 기관의 허가 또는 검토가 필요할 수 있습니다. 수출 허가 요건, 최종 사용자, 목적지 국가, 거래 목적을 함께 검토해야 합니다.",
                2,
                LocalDate.of(2026, 1, 1),
                null,
                "개정",
                oldForeignTradePermit.getId()
        );
        saveArticle(
                foreignTrade,
                "\uC81C25\uC870",
                "\uBB34\uC5ED\uC548\uBCF4\uAD00\uB9AC\uC6D0",
                "무역안보관리원 역할과 무역안보 업무 흐름을 설명하기 위한 근거 조문입니다. 전략물자 관리, 수출통제 지원, 무역안보 관련 질문에 활용합니다.",
                3
        );
        saveArticle(
                foreignTrade,
                "제20조",
                "전략물자 판정",
                "전략물자 판정은 물품 또는 기술이 전략물자에 해당하는지 확인하기 위한 절차입니다. 판정 신청, 품목 설명, 기술 사양, 수출 목적과 최종 사용자를 함께 검토해야 합니다.",
                4
        );
        saveArticle(
                foreignTrade,
                "제19조의4",
                "전략물자 등의 경유 또는 환적허가",
                "전략물자 등의 경유 또는 환적은 목적지 국가, 환적 경로, 최종 사용자를 기준으로 허가 또는 검토가 필요할 수 있습니다.",
                5
        );
        saveArticle(
                foreignTrade,
                "제19조의5",
                "전략물자 등의 중개허가",
                "전략물자를 제3국에서 다른 제3국으로 중개하는 거래는 중개허가 대상인지 확인해야 합니다. 거래 당사자, 목적지, 최종 사용자를 함께 확인합니다.",
                6
        );
        saveArticle(
                foreignTrade,
                "제53조",
                "벌칙",
                "전략물자 수출허가 또는 상황허가 의무를 위반한 경우 벌칙 검토가 필요할 수 있습니다. 위반 여부를 단정하지 말고 허가 대상, 거래 시점, 고의성 등을 확인해야 합니다.",
                7
        );

        Article oldDefenseTransfer = saveArticle(
                defenseAcquisition,
                "\uC81C57\uC870",
                "\uBC29\uC0B0\uBB3C\uC790 \uB4F1\uC758 \uC218\uCD9C \uBC0F \uAE30\uC220\uC774\uC804",
                "방산물자 수출과 기술자료 이전은 대상 물자와 제공받는 기관을 기준으로 검토합니다. 해외 이전 여부와 공개 범위를 함께 확인해야 합니다.",
                1,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 12, 31),
                "개정 전",
                null
        );
        saveArticle(
                defenseAcquisition,
                "\uC81C57\uC870",
                "\uBC29\uC0B0\uBB3C\uC790 \uB4F1\uC758 \uC218\uCD9C \uBC0F \uAE30\uC220\uC774\uC804",
                "방산물자 수출 또는 방산 관련 기술자료의 해외 이전은 검토나 허가가 필요할 수 있습니다. 물자 또는 자료의 종류, 제공받는 기관, 목적지, 공개 범위를 확인해야 합니다.",
                1,
                LocalDate.of(2026, 1, 1),
                null,
                "개정",
                oldDefenseTransfer.getId()
        );
        saveArticle(
                defenseAcquisition,
                "\uC81C3\uC870",
                "\uC815\uC758",
                "방산물자, 군수품, 방위산업 관련 용어와 맥락을 확인하기 위한 근거 조문입니다.",
                2
        );
        saveArticle(
                defenseAcquisition,
                "제57조의2",
                "군수품무역대리업 등록",
                "군수품무역대리업 등록은 방산물자 또는 군수품 거래를 중개하려는 경우 확인해야 할 관리 절차입니다. 등록 대상, 거래 물품, 상대 기관, 목적지를 함께 검토합니다.",
                3
        );

        saveArticle(
                customs,
                "\uC81C241\uC870",
                "\uC218\uCD9C\u318D\uC218\uC785 \uB610\uB294 \uBC18\uC1A1\uC758 \uC2E0\uACE0",
                "수출 신고에서는 품명, 규격, 수량, 가격 등 물품 정보와 신고 내용을 확인해야 합니다.",
                1
        );
        saveArticle(
                customs,
                "\uC81C270\uC870\uC7582",
                "\uAC00\uACA9\uC870\uC791\uC8C4",
                "관세 가격 조작 관련 개정 비교 시나리오를 설명하기 위한 관련 조문입니다.",
                2
        );

        saveArticle(
                foreignExchange,
                "\uC81C18\uC870",
                "\uC790\uBCF8\uAC70\uB798\uC758 \uC2E0\uACE0 \uB4F1",
                "자본거래는 거래 유형, 상대방, 금액, 외국환거래 맥락에 따라 신고 또는 검토가 필요할 수 있습니다.",
                1
        );

        saveArticle(
                nationalAdvancedStrategy,
                "\uC81C12\uC870",
                "\uC804\uB7B5\uAE30\uC220\uC758 \uC218\uCD9C \uC2B9\uC778 \uB4F1",
                "국가첨단전략기술을 수출하려는 경우 승인이 필요할 수 있습니다. 기술의 종류, 제공받는 기관, 목적지, 사용 목적, 보호 조치를 함께 검토해야 합니다.",
                1
        );
        saveArticle(
                nationalAdvancedStrategy,
                "\uC81C15\uC870",
                "\uC804\uB7B5\uAE30\uC220\uC758 \uC720\uCD9C \uBC0F \uCE68\uD574\uD589\uC704 \uAE08\uC9C0",
                "전략기술의 유출 또는 침해 방지와 관련된 질문을 검토하기 위한 근거 조문입니다.",
                2
        );

        saveArticle(
                industrialTechnology,
                "\uC81C9\uC870",
                "\uAD6D\uAC00\uD575\uC2EC\uAE30\uC220\uC758 \uC9C0\uC815\u318D\uBCC0\uACBD \uBC0F \uD574\uC81C",
                "국가핵심기술과 산업기술 보호 관련 질문은 지정 여부, 수출 검토, 제공받는 기관, 공개 목적을 확인해야 합니다.",
                1
        );
        saveArticle(
                industrialTechnology,
                "\uC81C14\uC870",
                "\uC0B0\uC5C5\uAE30\uC220\uC758 \uC720\uCD9C \uBC0F \uCE68\uD574\uD589\uC704 \uAE08\uC9C0",
                "산업기술 유출 방지와 침해 행위 금지 시나리오를 검토하기 위한 근거 조문입니다.",
                2
        );

        saveArticle(
                defenseScience,
                "\uC81C8\uC870",
                "\uAD6D\uBC29\uACFC\uD559\uAE30\uC220\uD601\uC2E0 \uAE30\uBCF8\uACC4\uD68D",
                "국방과학기술 정책, 연구개발, 방위사업 관련 질문을 함께 검토할 때 참고할 수 있는 근거 조문입니다.",
                1
        );

        saveArticle(
                militarySupplies,
                "\uC81C13\uC870",
                "\uAD70\uC218\uD488\uC758 \uAD00\uB9AC",
                "군수품 관리 관련 질문은 품목 구분, 관리 주체, 이전, 보관, 처분 맥락을 확인해야 합니다.",
                1
        );
    }

    private Law saveLaw(String slug, String title, String lawNumber, SnapshotVersion snapshot) {
        return lawRepository.save(new Law(slug, title, LawType.LAW, lawNumber, snapshot));
    }

    private Law saveLaw(
            String slug,
            String title,
            String lawNumber,
            SnapshotVersion snapshot,
            LocalDate enactedAt,
            LocalDate effectiveFrom
    ) {
        Law law = new Law(slug, title, LawType.LAW, lawNumber, snapshot);
        law.applyMetadata(
                enactedAt,
                effectiveFrom,
                effectiveFrom,
                null,
                "reference/" + slug.replace("law:", "") + ".md",
                "{}"
        );
        return lawRepository.save(law);
    }

    private Article saveArticle(Law law, String number, String title, String content, int orderIndex) {
        return articleRepository.save(new Article(law, number, title, content, orderIndex));
    }

    private Article saveArticle(
            Law law,
            String number,
            String title,
            String content,
            int orderIndex,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String amendmentKind,
            Long previousArticleId
    ) {
        return articleRepository.save(new Article(
                law,
                number,
                title,
                content,
                orderIndex,
                effectiveFrom,
                effectiveTo,
                amendmentKind,
                previousArticleId
        ));
    }
}
