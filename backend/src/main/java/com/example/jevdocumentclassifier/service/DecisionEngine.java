package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.DocumentType;

public interface DecisionEngine {

    ClassificationDecision classify(String text);

    double sameDocument(String previousPageText, String currentPageText);

    String name();

    record ClassificationDecision(DocumentType type, double confidence) {}
}
