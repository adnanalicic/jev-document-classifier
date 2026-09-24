package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.DocumentType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class HeuristicDecisionEngine implements DecisionEngine {

    @Override
    public ClassificationDecision classify(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);

        if (containsAny(value, "invoice", "rechnung", "invoice number", "amount due")) {
            return new ClassificationDecision(DocumentType.INVOICE, 0.82);
        }
        if (containsAny(value, "first notification of loss", "fnol", "schadenmeldung")) {
            return new ClassificationDecision(DocumentType.CLAIM_FORM, 0.88);
        }
        if (containsAny(value, "medical report", "arztbericht", "diagnosis", "patient")) {
            return new ClassificationDecision(DocumentType.MEDICAL_REPORT, 0.80);
        }
        if (containsAny(value, "adjuster", "loss adjuster", "gutachterbericht")) {
            return new ClassificationDecision(DocumentType.ADJUSTER_REPORT, 0.81);
        }
        if (containsAny(value, "estimate", "kostenvoranschlag", "repair estimate")) {
            return new ClassificationDecision(DocumentType.ESTIMATE, 0.82);
        }
        if (containsAny(value, "settlement", "vergleich", "claim settlement")) {
            return new ClassificationDecision(DocumentType.SETTLEMENT_LETTER, 0.79);
        }
        if (containsAny(value, "denial", "claim denied", "ablehnung")) {
            return new ClassificationDecision(DocumentType.DENIAL_LETTER, 0.79);
        }
        if (containsAny(value, "policy", "police", "versicherungsschein", "policy number")) {
            return new ClassificationDecision(DocumentType.POLICY, 0.84);
        }
        if (containsAny(value, "passport", "identity card", "personalausweis")) {
            return new ClassificationDecision(DocumentType.IDENTITY_DOCUMENT, 0.85);
        }
        if (containsAny(value, "dear", "sincerely", "mit freundlichen grüßen", "regards")) {
            return new ClassificationDecision(DocumentType.CORRESPONDENCE, 0.70);
        }

        return new ClassificationDecision(DocumentType.OTHER, 0.50);
    }

    @Override
    public double sameDocument(String previousPageText, String currentPageText) {
        if (previousPageText == null || currentPageText == null) {
            return 0.0;
        }

        String previous = previousPageText.toLowerCase(Locale.ROOT);
        String current = currentPageText.toLowerCase(Locale.ROOT);

        if (current.matches("(?s).*page\\s+([2-9]|[1-9][0-9]+)\\s+(of|/)\\s*[0-9]+.*")) {
            return 0.92;
        }

        var previousType = classify(previous).type();
        var currentType = classify(current).type();
        if (previousType == currentType && previousType != DocumentType.OTHER) {
            return 0.80;
        }

        return 0.30;
    }

    @Override
    public String name() {
        return "heuristic";
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
