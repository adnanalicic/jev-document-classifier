package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JevDecisionEngine implements DecisionEngine {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestClient restClient;

    public JevDecisionEngine(
            @Value("${jev.api-key:}") String apiKey,
            @Value("${jev.base-url:https://api.typesafe.ai}") String baseUrl,
            @Value("${jev.model:jev-latest}") String model
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClassificationDecision classify(String text) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", "Classify this insurance document page by its primary document type.");
        question.put("choices", List.of(
                "POLICY",
                "CLAIM_FORM",
                "INVOICE",
                "MEDICAL_REPORT",
                "ADJUSTER_REPORT",
                "ESTIMATE",
                "SETTLEMENT_LETTER",
                "DENIAL_LETTER",
                "CORRESPONDENCE",
                "IDENTITY_DOCUMENT",
                "OTHER"
        ));

        Map<String, Object> response = call(Map.of(
                "model", model,
                "state", Map.of("page_text", truncate(text)),
                "questions", Map.of("document_type", question)
        ));

        Object answer = extractAnswer(response, "document_type");
        if (answer instanceof Map<?, ?> answerMap) {
            String selected = firstString(answerMap, "choice", "value", "answer", "selected");
            double confidence = firstDouble(answerMap, 0.5, "confidence", "probability", "score");

            if (selected != null) {
                try {
                    return new ClassificationDecision(DocumentType.valueOf(selected), confidence);
                } catch (IllegalArgumentException ignored) {
                    // handled below
                }
            }
        }

        return new ClassificationDecision(DocumentType.OTHER, 0.5);
    }

    @Override
    public double sameDocument(String previousPageText, String currentPageText) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "noul");
        question.put("instructions",
                "Do these two adjacent pages belong to the same logical document? " +
                "The same policy or claim number alone is not sufficient; consider document boundaries, headers, page numbering, and semantic continuation.");

        Map<String, Object> response = call(Map.of(
                "model", model,
                "state", Map.of(
                        "previous_page", truncate(previousPageText),
                        "current_page", truncate(currentPageText)
                ),
                "questions", Map.of("same_document", question)
        ));

        Object answer = extractAnswer(response, "same_document");
        if (answer instanceof Number n) {
            return clamp(n.doubleValue());
        }
        if (answer instanceof Map<?, ?> answerMap) {
            return clamp(firstDouble(answerMap, 0.5,
                    "probability", "yes_probability", "confidence", "score"));
        }

        return 0.5;
    }

    @Override
    public String name() {
        return "jev";
    }

    private Map<String, Object> call(Map<String, Object> payload) {
        if (!configured()) {
            throw new IllegalStateException("JEV_API_KEY is not configured");
        }

        return restClient.post()
                .uri("/v1/systemone")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    private Object extractAnswer(Map<String, Object> response, String questionName) {
        if (response == null) {
            return null;
        }

        Object answers = response.get("answers");
        if (answers instanceof Map<?, ?> map) {
            return map.get(questionName);
        }

        return response.get(questionName);
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private double firstDouble(Map<?, ?> map, double fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        }
        return fallback;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }

        int maxChars = 10_000;
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
