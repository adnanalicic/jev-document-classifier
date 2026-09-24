package com.example.jevdocumentclassifier.model;

import java.util.List;

public record ClassificationResponse(
        String decisionEngine,
        int pageCount,
        int documentCount,
        List<DocumentGroup> documents
) {}
