package com.example.lawassistant.api;

import com.example.lawassistant.dto.ArticleDiffResponse;
import com.example.lawassistant.dto.ArticleHistoryResponse;
import com.example.lawassistant.dto.ArticleResponse;
import com.example.lawassistant.dto.LawDetailResponse;
import com.example.lawassistant.dto.LawListResponse;
import com.example.lawassistant.dto.LawRevisionListResponse;
import com.example.lawassistant.service.LawQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Law", description = "Law and article lookup APIs")
public class LawController {

    private final LawQueryService lawQueryService;

    public LawController(LawQueryService lawQueryService) {
        this.lawQueryService = lawQueryService;
    }

    @GetMapping("/laws")
    @Operation(summary = "List laws", description = "Returns law metadata. Optional keyword/q filters law titles.")
    public LawListResponse laws(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String effectiveKeyword = keyword == null || keyword.isBlank() ? q : keyword;
        return lawQueryService.findLaws(effectiveKeyword, page, size);
    }

    @GetMapping("/laws/{id}")
    @Operation(summary = "Get law detail", description = "Returns law metadata and current articles.")
    public LawDetailResponse law(@PathVariable Long id) {
        return lawQueryService.findLaw(id);
    }

    @GetMapping("/laws/{id}/revisions")
    @Operation(summary = "Get law revisions", description = "Returns law-level revision groups derived from article effective dates.")
    public LawRevisionListResponse lawRevisions(@PathVariable Long id) {
        return lawQueryService.findLawRevisions(id);
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Get article by id")
    public ArticleResponse article(@PathVariable Long id) {
        return lawQueryService.findArticle(id);
    }

    @GetMapping("/articles/{id}/history")
    @Operation(summary = "Get article history", description = "Returns all versions for the same law and article number.")
    public ArticleHistoryResponse articleHistory(@PathVariable Long id) {
        return lawQueryService.findArticleHistory(id);
    }

    @GetMapping("/articles/{id}/diff")
    @Operation(summary = "Compare two article versions", description = "Compares two versions of the same law article.")
    public ArticleDiffResponse articleDiff(@PathVariable Long id, @RequestParam Long compareWith) {
        return lawQueryService.compareArticles(id, compareWith);
    }
}
