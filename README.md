# Jev Document Classifier

Prototype for classifying and grouping mixed insurance-document pages with the Jev AI model.

## Goal

A user uploads PDFs/images containing a mix of logical insurance documents. The backend:

1. expands uploads into individual pages,
2. extracts text locally with PDFBox and Tesseract OCR fallback,
3. asks Jev for the page document type and whether adjacent pages belong together,
4. groups pages into logical documents,
5. returns the classified groups to the React UI.

## Stack

- Java 25
- Spring Boot 4.1.1
- Apache PDFBox 3.0.8
- Tesseract OCR (local, optional fallback)
- TypeSafe Jev API
- React 19.3 + Vite 8.1

## Jev configuration

Create an API key in TypeSafe and export it before starting the backend:

```bash
export JEV_API_KEY=your_key
```

The backend calls `https://api.typesafe.ai/v1/systemone` using model alias `jev-latest`.

If no API key is configured, the app starts in a deterministic heuristic fallback mode so the UI and grouping flow can still be developed locally.

## Local development

### Backend

Requirements: JDK 25, Maven, and optionally Tesseract.

```bash
cd backend
mvn spring-boot:run
```

Backend: http://localhost:8080

### Frontend

Requirements: Node.js 24+.

```bash
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:5173

The Vite dev server proxies `/api` to Spring Boot.

## API

`POST /api/classifications`

Multipart form field: `files` (one or more PDF/JPG/PNG files, in page order).

Response contains logical document groups, their predicted type/confidence, and page-level boundary probabilities.

## MVP document types

- POLICY
- CLAIM_FORM
- INVOICE
- MEDICAL_REPORT
- ADJUSTER_REPORT
- ESTIMATE
- SETTLEMENT_LETTER
- DENIAL_LETTER
- CORRESPONDENCE
- IDENTITY_DOCUMENT
- OTHER

## Next steps

- Add Strata Insurance Corpus test-stack generator.
- Add merged-PDF download for each logical document.
- Add benchmark metrics: boundary precision/recall/F1, type accuracy, exact reconstruction.
- Add a comparison runner for rules vs Jev vs LLM.


## Strata benchmark

A reproducible benchmark harness is available under `benchmark/`.

It downloads the supported subset of the synthetic Strata Insurance Corpus sample, verifies SHA-256 checksums, generates deterministic mixed multi-document PDFs with exact page-level ground truth, calls the local classifier API, and reports:

- boundary precision / recall / F1
- page classification accuracy
- exact document reconstruction rate
- exact stack reconstruction rate
- type accuracy on exactly reconstructed documents

See [benchmark/README.md](benchmark/README.md) for commands.
