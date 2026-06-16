package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.QuestionType;
import java.util.List;

public record QuestionInterpretationDto(
        String action,
        String object,
        List<String> domainCandidates,
        List<String> uncertainties,
        List<String> generatedQueries,
        QuestionType questionType
) {
}
