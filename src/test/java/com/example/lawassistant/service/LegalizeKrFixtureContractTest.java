package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalizeKrFixtureContractTest {

    private final MarkdownLawParser parser = new MarkdownLawParser();

    @Test
    void parsesSelectedLegalizeKrLawFilesWhenRepositoryIsAvailable() throws Exception {
        Path root = Path.of("C:/dev/legalize-kr/kr");
        assumeTrue(Files.isDirectory(root), "legalize-kr repository is not available locally");

        List<ExpectedFile> expectedFiles = List.of(
                new ExpectedFile("대외무역법", "법률.md", 76),
                new ExpectedFile("대외무역법", "시행령.md", 120),
                new ExpectedFile("방위사업법", "법률.md", 88),
                new ExpectedFile("방위사업법", "시행령.md", 113),
                new ExpectedFile("방위사업법", "시행규칙.md", 72),
                new ExpectedFile("관세법", "법률.md", 424),
                new ExpectedFile("관세법", "시행령.md", 440),
                new ExpectedFile("관세법", "시행규칙.md", 144),
                new ExpectedFile("외국환거래법", "법률.md", 39),
                new ExpectedFile("외국환거래법", "시행령.md", 66),
                new ExpectedFile("국가첨단전략산업경쟁력강화및보호에관한특별조치법", "법률.md", 56),
                new ExpectedFile("국가첨단전략산업경쟁력강화및보호에관한특별조치법", "시행령.md", 56),
                new ExpectedFile("국가첨단전략산업경쟁력강화및보호에관한특별조치법", "시행규칙.md", 15),
                new ExpectedFile("산업기술의유출방지및보호에관한법률", "법률.md", 54),
                new ExpectedFile("산업기술의유출방지및보호에관한법률", "시행령.md", 54),
                new ExpectedFile("산업기술의유출방지및보호에관한법률", "시행규칙.md", 21),
                new ExpectedFile("국방과학기술혁신촉진법", "법률.md", 21),
                new ExpectedFile("국방과학기술혁신촉진법", "시행령.md", 20),
                new ExpectedFile("국방과학기술혁신촉진법", "시행규칙.md", 13),
                new ExpectedFile("군수품관리법", "법률.md", 40),
                new ExpectedFile("군수품관리법", "시행령.md", 71),
                new ExpectedFile("군수품관리법", "시행규칙.md", 50)
        );

        int total = 0;
        for (ExpectedFile entry : expectedFiles) {
            Path file = root.resolve(entry.lawName()).resolve(entry.fileName());
            var parsed = parser.parse(Files.readString(file), "kr/" + entry.lawName() + "/" + entry.fileName());

            assertThat(parsed).isNotNull();
            assertThat(normalize(parsed.title())).startsWith(entry.lawName());
            assertThat(parsed.articles()).hasSize(entry.articleCount());
            total += parsed.articles().size();
        }

        assertThat(total).isEqualTo(2053);
    }

    private String normalize(String value) {
        return value.replace(" ", "");
    }

    private record ExpectedFile(String lawName, String fileName, int articleCount) {
    }
}
