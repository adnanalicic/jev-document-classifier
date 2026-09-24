# Strata benchmark

This folder turns the public **Strata Insurance Corpus** sample into a deterministic benchmark for the Jev document-classifier prototype.

The benchmark evaluates:

1. **Page/document classification** — does each page receive the expected MVP document type?
2. **Document boundary detection** — after multiple source documents are concatenated, does the backend recover the original logical document boundaries?

## Recommended: run fully isolated with Docker

From the repository root, create your local environment file:

```bash
cp .env.example .env
```

Set:

```text
OPENROUTER_API_KEY=sk-or-v1-...
```

Start the app:

```bash
docker compose up --build -d
```

Run the complete Strata pipeline inside Docker:

```bash
docker compose --profile benchmark run --rm benchmark
```

This performs all three steps inside the benchmark container:

1. download and SHA-256 verify the supported Strata sample,
2. generate deterministic mixed stacks and ground truth,
3. call the Spring Boot backend over the internal Docker network and calculate metrics.

The downloaded source documents and benchmark results are stored in Docker named volumes, not in Python/Java installations on your host.

## Jev provider

The backend calls Jev through OpenRouter's Decisions API using:

```text
POST https://openrouter.ai/api/alpha/decisions
model: typesafe/jev-1.13
```

The model is pinned by default for repeatable benchmark results. Override `JEV_MODEL` if you want to test another Jev version or `~typesafe/jev-latest`.

Without `OPENROUTER_API_KEY`, the backend falls back to its deterministic heuristic engine. That makes it useful to run the same stacks twice and compare baseline vs Jev.

## Source

Strata is a synthetic insurance corpus by Nikolai Sachok. Its manifest supplies document IDs, document types, paths, scan metadata, entity IDs and SHA-256 hashes. The scripts download only document families that map cleanly to the prototype taxonomy.

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

## Metrics

- **Boundary precision** — predicted splits that were true splits.
- **Boundary recall** — true splits that were recovered.
- **Boundary F1** — balanced boundary metric.
- **Page classification accuracy** — expected vs predicted type per page.
- **Exact document reconstruction rate** — ground-truth page ranges recovered exactly.
- **Exact stack reconstruction rate** — all boundaries in a stack are correct.
- **Type accuracy on exactly reconstructed documents** — classification quality after grouping is correct.

Exact document/stack reconstruction is intentionally strict and is the most useful measure for the upload → split → classify use case.

## Running scripts directly

Direct Python execution is still supported for development:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r benchmark/requirements.txt

python benchmark/download_strata_sample.py
python benchmark/generate_stacks.py --stacks 8 --documents-per-stack 4 --seed 42 --mode mixed
python benchmark/evaluate.py --api http://localhost:8080/api/classifications
```

Docker Compose is preferred because it makes the benchmark reproducible without installing Python, Java or Tesseract on the host.
