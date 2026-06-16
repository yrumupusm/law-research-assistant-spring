package com.example.lawassistant.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ArticleContentHashTest {

    @Test
    void hashIsDeterministicAndHexEncoded() {
        String hash = article("Article body\n\n  content  ").getContentHash();

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(article("Article body\n\n  content  ").getContentHash()).isEqualTo(hash);
    }

    @Test
    void crlfLfAndCrProduceSameHash() {
        String lf = "Article\nbody content\nnext line";
        String crlf = "Article\r\nbody content\r\nnext line";
        String crOnly = "Article\rbody content\rnext line";

        assertThat(article(crlf).getContentHash()).isEqualTo(article(lf).getContentHash());
        assertThat(article(crOnly).getContentHash()).isEqualTo(article(lf).getContentHash());
    }

    @Test
    void trailingWhitespaceAndConsecutiveBlankLinesDoNotAffectHash() {
        String compact = "first line\n\nsecond line";
        String noisy = "first line  \n\n\n\nsecond line\t";

        assertThat(article(noisy).getContentHash()).isEqualTo(article(compact).getContentHash());
    }

    @Test
    void bomAndOuterWhitespaceDoNotAffectHash() {
        String normal = "Article body";
        String noisy = "\uFEFF\n\nArticle body\n  \n";

        assertThat(article(noisy).getContentHash()).isEqualTo(article(normal).getContentHash());
    }

    @Test
    void meaningfulInternalWhitespaceStillAffectsHash() {
        String singleSpace = "Article body content";
        String doubleSpace = "Article body  content";

        assertThat(article(doubleSpace).getContentHash()).isNotEqualTo(article(singleSpace).getContentHash());
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertThat(article("Article body A").getContentHash()).isNotEqualTo(article("Article body B").getContentHash());
    }

    private Article article(String content) {
        SnapshotVersion snapshot = new SnapshotVersion(
                "hash-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        Law law = new Law("law:hash-test", "Hash Test Law", LawType.LAW, "LAW-HASH", snapshot);
        return new Article(law, "Article 1", "Purpose", content, 1);
    }
}
