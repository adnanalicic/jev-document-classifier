package com.example.jevdocumentclassifier.model;

public record PageAnalysis(
        int pageIndex,
        String sourceName,
        String extractedText,
        DocumentType documentType,
        double typeConfidence,
        double sameAsPreviousProbability
) {}
