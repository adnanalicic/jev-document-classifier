package com.example.jevdocumentclassifier.service;

import com.example.jevdocumentclassifier.model.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicDecisionEngineTest {

    private final HeuristicDecisionEngine engine = new HeuristicDecisionEngine();

    @Test
    void classifiesInvoice() {
        var result = engine.classify("INVOICE Number 4711 Amount Due EUR 120.00");

        assertThat(result.type()).isEqualTo(DocumentType.INVOICE);
        assertThat(result.confidence()).isGreaterThan(0.7);
    }

    @Test
    void recognizesPageContinuation() {
        double probability = engine.sameDocument(
                "Claim form Page 1 of 3",
                "Claim form Page 2 of 3"
        );

        assertThat(probability).isGreaterThan(0.75);
    }
}
