# Strata benchmark

This folder turns the public **Strata Insurance Corpus** sample into a deterministic benchmark for the Jev document-classifier prototype.

The benchmark intentionally evaluates two different problems:

1. **Page/document classification** — does each page receive the expected MVP document type?
2. **Document boundary detection** — after multiple source documents are concatenated, does the backend recover the original logical document boundaries?

## Source

Strata is a synthetic insurance corpus by Nikolai Sachok. Its manifest supplies document IDs, document types, paths, scan metadata, entity IDs and SHA-256 hashes. The scripts here download only the document families that map cleanly to the prototype taxonomy.

No real customer/insurance data is used.

## Type mapping

| Strata type | Prototype type |
|---|---|
| policy_contract / policy_declarations / policy_endorsements / policy_schedule | POLICY |
| fnol / fnol_scanned | CLAIM_FORM |
| accident_statement / accident_statement_scanned | CLAIM_FORM |
| adjuster_report | ADJUSTER_REPORT |
| estimate | ESTIMATE |
| settlement_letter / scanned | SETTLEMENT_LETTER |
| denial_letter / scanned | DENIAL_LETTER |
| id_card / id_card_scanned | IDENTITY_DOCUMENT |

Unsupported photos, spreadsheets and knowledge-base files are excluded from this benchmark.

## Setup

From the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r benchmark/requirements.txt
```

### 1. Download the Strata sample subset

```bash
python benchmark/download_strata_sample.py
```

Every downloaded file is checked against the SHA-256 in Strata's manifest.

### 2. Generate deterministic mixed stacks

```bash
python benchmark/generate_stacks.py --stacks 8 --documents-per-stack 4 --seed 42 --mode mixed
```

This creates:

```text
benchmark/out/
├── ground-truth.json
└── stacks/
    ├── stack-001.pdf
    ├── stack-002.pdf
    └── ...
```

Each source document stays contiguous, but multiple unrelated documents are concatenated into one incoming PDF. Ground truth stores the exact page range and expected type for every logical document.

Modes:

- `digital` — born-digital PDFs only
- `scanned` — scanned image variants only
- `mixed` — both

### 3. Start the backend

Without a Jev key the benchmark runs against the heuristic fallback:

```bash
cd backend
mvn spring-boot:run
```

To benchmark Jev:

```bash
export JEV_API_KEY=...
cd backend
mvn spring-boot:run
```

### 4. Evaluate

In another terminal:

```bash
source .venv/bin/activate
python benchmark/evaluate.py
```

The evaluator writes:

- `benchmark/out/results.json` — machine-readable detailed results
- `benchmark/out/results.md` — compact human-readable scorecard

## Metrics

- **Boundary precision** — predicted splits that were true splits.
- **Boundary recall** — true splits that were recovered.
- **Boundary F1** — balanced boundary metric.
- **Page classification accuracy** — expected vs predicted type per page.
- **Exact document reconstruction rate** — ground-truth page ranges recovered exactly.
- **Exact stack reconstruction rate** — all boundaries in a stack are correct.
- **Type accuracy on exactly reconstructed documents** — classification quality after grouping is correct.

Exact document/stack reconstruction is intentionally strict and is the most useful measure for the upload → split → classify use case.
