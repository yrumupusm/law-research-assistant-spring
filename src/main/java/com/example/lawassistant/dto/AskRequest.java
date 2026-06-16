package com.example.lawassistant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AskRequest(
        @NotBlank
        @Size(max = 4000)
        String question,
        @JsonAlias("as_of")
        LocalDate asOf
) {
}
