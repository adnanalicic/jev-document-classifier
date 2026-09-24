package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.ClassificationResponse;
import com.example.jevdocumentclassifier.model.DocumentGroup;
import com.example.jevdocumentclassifier.model.DocumentType;
import com.example.jevdocumentclassifier.model.PageAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClassificationService {

    private final PageExtractionService pageExtractionService;
    private final HeuristicDecisionEngine heuristicDecisionEngine;
    private final JevDecisionEngine jevDecisionEngine;
    private final double splitThreshold;

    public ClassificationService(
            PageExtractionService pageExtractionService,
            HeuristicDecisionEngine heuristicDecisionEngine,
            JevDecisionEngine jevDecisionEngine,
            @Value("${classification.same-document-threshold:0.75}") double splitThreshold
    ) {
        this.pageExtractionService = pageExtractionService;
        this.heuristicDecisionEngine = heuristicDecisionEngine;
        this.jevDecisionEngine = jevDecisionEngine;
        this.splitThreshold = splitThreshold;
    }

    public ClassificationResponse classify(List<MultipartFile> files) throws IOException {
        List<PageExtractionService.ExtractedPage> extractedPages = pageExtractionService.extract(files);
        DecisionEngine engine = jevDecisionEngine.configured() ? jevDecisionEngine : heuristicDecisionEngine;

        List<PageAnalysis> analyses = new ArrayList<>();

        for (int i = 0; i < extractedPages.size(); i++) {
            var page = extractedPages.get(i);
            var decision = engine.classify(page.text());

            double sameAsPrevious = i == 0
                    ? 0.0
                    : engine.sameDocument(extractedPages.get(i - 1).text(), page.text());

            analyses.add(new PageAnalysis(
                    page.globalPageIndex(),
                    page.sourceName(),
                    page.text(),
                    decision.type(),
                    decision.confidence(),
                    sameAsPrevious
            ));
        }

        List<DocumentGroup> documents = groupPages(analyses);

        return new ClassificationResponse(
                engine.name(),
                analyses.size(),
                documents.size(),
                documents
        );
    }

    private List<DocumentGroup> groupPages(List<PageAnalysis> pages) {
        List<DocumentGroup> groups = new ArrayList<>();
        List<PageAnalysis> current = new ArrayList<>();

        for (PageAnalysis page : pages) {
            boolean startNew = !current.isEmpty()
                    && page.sameAsPreviousProbability() < splitThreshold;

            if (startNew) {
                groups.add(toGroup(groups.size() + 1, current));
                current = new ArrayList<>();
            }

            current.add(page);
        }

        if (!current.isEmpty()) {
            groups.add(toGroup(groups.size() + 1, current));
        }

        return groups;
    }

    private DocumentGroup toGroup(int index, List<PageAnalysis> pages) {
        DocumentType type = pages.stream()
                .max((a, b) -> Double.compare(a.typeConfidence(), b.typeConfidence()))
                .map(PageAnalysis::documentType)
                .orElse(DocumentType.OTHER);

        double confidence = pages.stream()
                .filter(page -> page.documentType() == type)
                .mapToDouble(PageAnalysis::typeConfidence)
                .average()
                .orElse(0.5);

        return new DocumentGroup(index, type, confidence, List.copyOf(pages));
    }
}
