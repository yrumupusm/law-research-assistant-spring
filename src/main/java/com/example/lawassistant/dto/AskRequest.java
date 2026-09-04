package com.example.lawassistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import com.example.lawassistant.domain.enums.ResearchArea;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AskRequest(
        @NotBlank
        @Size(max = 4000)
        String question,
        @JsonAlias("as_of")
        LocalDate asOf,
        @JsonAlias("research_areas")
        List<ResearchArea> researchAreas
) {
    public AskRequest {
        researchAreas = researchAreas == null ? List.of() : List.copyOf(researchAreas);
    }
}
