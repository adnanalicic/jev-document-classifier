# Jev Document Classifier

Prototype for classifying and grouping mixed insurance-document pages with the Jev decision model.

## Architecture

The default runtime is fully containerized with Docker Compose:

```text
Browser :3000
    |
    v
nginx / React
    |
    | /api
    v
Spring Boot + PDFBox + local Tesseract OCR
    |
    v
OpenRouter Decisions API -> TypeSafe Jev

Python Strata benchmark
    |
    +---- internal Docker network ----> Spring Boot
```

The host needs Docker (Docker Desktop, Colima, Rancher Desktop, Podman with Compose compatibility, etc.). Java, Maven, Node, Python, nginx and Tesseract run inside containers.

## Quick start

1. Copy the environment template:

```bash
cp .env.example .env
```

2. Put your OpenRouter key in `.env`:

```text
OPENROUTER_API_KEY=sk-or-v1-...
```

3. Start the application:

```bash
docker compose up --build -d
```

Open:

```text
http://localhost:3000
```

Only the frontend/nginx service is published to the host. The Spring Boot backend and benchmark communicate on the internal Docker network.

Stop everything:

```bash
docker compose down
```

## Jev through OpenRouter

The backend uses OpenRouter's Decisions API:

```text
POST https://openrouter.ai/api/alpha/decisions
```

Default model:

```text
typesafe/jev-1.13
```

It is pinned by default so benchmark runs are reproducible. To follow the newest Jev model instead:

```bash
JEV_MODEL=~typesafe/jev-latest
```

If `OPENROUTER_API_KEY` is empty, the backend uses the deterministic heuristic fallback so the upload/UI flow can still be tested offline.

## Local OCR

Tesseract is installed inside the backend image together with English and German language data. Nothing needs to be installed on the host.

PDFBox first tries embedded PDF text. Pages with too little embedded text and uploaded images are OCRed locally inside the backend container.

## Strata benchmark

The Python benchmark also runs in Docker. It downloads the supported synthetic Strata sample, verifies SHA-256 values, generates mixed-document stacks, calls the backend, and computes:

- boundary precision / recall / F1
- page classification accuracy
- exact document reconstruction rate
- exact stack reconstruction rate
- type accuracy on exactly reconstructed documents

Run:

```bash
docker compose --profile benchmark run --rm benchmark
```

or:

```bash
make benchmark
```

The downloaded corpus and generated outputs live in Docker named volumes rather than your host filesystem.

Inspect the benchmark container interactively:

```bash
make benchmark-shell
```

## Services

- `frontend`: React compiled to static assets and served by nginx.
- `backend`: Java 25 / Spring Boot / PDFBox / Tesseract / OpenRouter Jev client.
- `benchmark`: Python 3.13 Strata downloader, stack generator and evaluator.

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

## Development without Docker

Running Java/Node/Python directly on the host is still possible, but is no longer the recommended path. See `benchmark/README.md` for the benchmark scripts.
