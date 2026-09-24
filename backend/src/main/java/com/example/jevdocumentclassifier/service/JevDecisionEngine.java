package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JevDecisionEngine implements DecisionEngine {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public JevDecisionEngine(
            @Value("${jev.api-key:}") String apiKey,
            @Value("${jev.base-url:https://openrouter.ai}") String baseUrl,
            @Value("${jev.model:typesafe/jev-1.13}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-OpenRouter-Title", "Jev Document Classifier")
                .build();
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClassificationDecision classify(String text) {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("POLICY", "Insurance policy contract, declarations, schedule, certificate, or endorsement.");
        criteria.put("CLAIM_FORM", "First notification of loss, accident statement, or other claim submission form.");
        criteria.put("INVOICE", "Invoice or bill requesting payment.");
        criteria.put("MEDICAL_REPORT", "Medical, clinical, diagnostic, treatment, or patient report.");
        criteria.put("ADJUSTER_REPORT", "Loss adjuster, surveyor, assessor, or claims investigation report.");
        criteria.put("ESTIMATE", "Repair, damage, replacement, or cost estimate/quotation.");
        criteria.put("SETTLEMENT_LETTER", "Letter communicating claim settlement, payment, or settlement terms.");
        criteria.put("DENIAL_LETTER", "Letter denying, rejecting, or declining an insurance claim.");
        criteria.put("CORRESPONDENCE", "General insurance-related letter or correspondence not covered by another class.");
        criteria.put("IDENTITY_DOCUMENT", "Identity card, passport, driver's licence, or similar identity document.");
        criteria.put("OTHER", "None of the listed insurance document types applies.");

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions",
                "Classify this page by its primary insurance document type. " +
                "Use the content and purpose of the page, not just isolated keywords.");
        question.put("criteria", criteria);

        Map<String, Object> response = call(Map.of(
                "model", model,
                "state", Map.of("page_text", truncate(text)),
                "questions", Map.of("document_type", question)
        ));

        Object answer = extractAnswer(response, "document_type");
        if (answer instanceof Map<?, ?> answerMap) {
            String selected = firstString(answerMap, "choice");
            double confidence = firstDouble(answerMap, 0.5, "confidence");

            if (selected != null) {
                try {
                    return new ClassificationDecision(DocumentType.valueOf(selected), confidence);
                } catch (IllegalArgumentException ignored) {
                    // Fall through to OTHER.
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
                "The same policy number, claim number, customer, or insurer alone is not sufficient. " +
                "Consider page numbering, headers, signatures, topic continuity, document purpose, " +
                "and whether the current page starts a distinct document.");

        Map<String, Object> response = call(Map.of(
                "model", model,
                "state", Map.of(
                        "previous_page", truncate(previousPageText),
                        "current_page", truncate(currentPageText)
                ),
                "questions", Map.of("same_document", question)
        ));

        Object answer = extractAnswer(response, "same_document");
        if (answer instanceof Map<?, ?> answerMap) {
            return clamp(firstDouble(answerMap, 0.5, "noul"));
        }

        return 0.5;
    }

    @Override
    public String name() {
        return "jev-openrouter:" + model;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(Map<String, Object> payload) {
        if (!configured()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
        }

        return restClient.post()
                .uri("/api/alpha/decisions")
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

        return null;
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
