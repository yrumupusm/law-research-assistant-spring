package com.example.lawassistant.api;

import com.example.lawassistant.service.LawQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Law v1 Compatibility", description = "Original law_research_assistant compatible law lookup APIs")
public class V1LawController {

    private final LawQueryService lawQueryService;
    private final V1ResponseMapper v1ResponseMapper;

    public V1LawController(LawQueryService lawQueryService, V1ResponseMapper v1ResponseMapper) {
        this.lawQueryService = lawQueryService;
        this.v1ResponseMapper = v1ResponseMapper;
    }

    @GetMapping("/laws")
    @Operation(summary = "List laws with original v1 response field names")
    public Map<String, Object> laws(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String effectiveKeyword = keyword == null || keyword.isBlank() ? q : keyword;
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.findLaws(effectiveKeyword, page, size));
    }

    @GetMapping("/laws/{id}")
    @Operation(summary = "Get law detail with original v1 response field names")
    public Map<String, Object> law(@PathVariable Long id) {
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.findLaw(id));
    }

    @GetMapping("/laws/{id}/revisions")
    @Operation(summary = "Get law revisions with original v1 response field names")
    public Map<String, Object> lawRevisions(@PathVariable Long id) {
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.findLawRevisions(id));
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Get article by id with original v1 response field names")
    public Map<String, Object> article(@PathVariable Long id) {
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.findArticle(id));
    }

    @GetMapping("/articles/{id}/history")
    @Operation(summary = "Get article history with original v1 response field names")
    public Map<String, Object> articleHistory(@PathVariable Long id) {
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.findArticleHistory(id));
    }

    @GetMapping("/articles/{id}/diff")
    @Operation(summary = "Compare two article versions with original v1 response field names")
    public Map<String, Object> articleDiff(@PathVariable Long id, @RequestParam Long compareWith) {
        return v1ResponseMapper.toSnakeCaseMap(lawQueryService.compareArticles(id, compareWith));
    }
}
