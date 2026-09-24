package com.example.jevdocumentclassifier.model;

import java.util.List;

public record DocumentGroup(
        int documentIndex,
        DocumentType documentType,
        double confidence,
        List<PageAnalysis> pages
) {}
