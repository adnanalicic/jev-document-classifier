#!/usr/bin/env python3
from __future__ import annotations

import argparse
import io
import json
import random
import shutil
from pathlib import Path

from PIL import Image
from pypdf import PdfReader, PdfWriter

from strata_common import expected_type, supported

DEFAULT_DATASET = Path(__file__).resolve().parent / "data" / "strata-sample"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "out"


def load_pages(source: Path) -> list:
    suffix = source.suffix.lower()

    if suffix == ".pdf":
        return list(PdfReader(str(source)).pages)

    if suffix in {".jpg", ".jpeg", ".png"}:
        with Image.open(source) as image:
            rgb = image.convert("RGB")
            buffer = io.BytesIO()
            rgb.save(buffer, format="PDF")
            buffer.seek(0)
            return list(PdfReader(buffer).pages)

    raise ValueError(f"Unsupported source format: {source}")


def eligible_documents(manifest: dict, mode: str) -> list[dict]:
    documents = [d for d in manifest["documents"] if supported(d)]

    if mode == "digital":
        return [d for d in documents if d.get("format") == "pdf" and not d.get("is_scanned", False)]
    if mode == "scanned":
        return [d for d in documents if d.get("is_scanned", False)]
    return documents


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create deterministic mixed insurance-document PDFs with exact page-level ground truth."
    )
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--stacks", type=int, default=8)
    parser.add_argument("--documents-per-stack", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--mode",
        choices=["digital", "scanned", "mixed"],
        default="mixed",
        help="digital = born-digital PDFs only; scanned = scanned images only; mixed = both",
    )
    args = parser.parse_args()

    dataset = args.dataset.resolve()
    output = args.output.resolve()

    manifest = json.loads((dataset / "manifest.json").read_text(encoding="utf-8"))
    documents = eligible_documents(manifest, args.mode)

    needed = args.stacks * args.documents_per_stack
    if needed > len(documents):
        raise SystemExit(
            f"Need {needed} unique documents but only {len(documents)} are eligible. "
            "Reduce --stacks/--documents-per-stack or change --mode."
        )

    rng = random.Random(args.seed)
    rng.shuffle(documents)
    selected = documents[:needed]

    if output.exists():
        shutil.rmtree(output)
    (output / "stacks").mkdir(parents=True)

    ground_truth = {
        "dataset": {
            "name": "Strata Insurance Corpus",
            "source": "NikolaiSachok/strata-insurance-corpus",
            "sample": True,
        },
        "seed": args.seed,
        "mode": args.mode,
        "stack_count": args.stacks,
        "documents_per_stack": args.documents_per_stack,
        "stacks": [],
    }

    cursor = 0
    for stack_index in range(1, args.stacks + 1):
        writer = PdfWriter()
        logical_documents = []
        page_cursor = 1

        stack_docs = selected[cursor : cursor + args.documents_per_stack]
        cursor += args.documents_per_stack

        for document in stack_docs:
            source = dataset / document["path"]
            pages = load_pages(source)
            start_page = page_cursor

            for page in pages:
                writer.add_page(page)
                page_cursor += 1

            end_page = page_cursor - 1
            logical_documents.append(
                {
                    "doc_id": document["doc_id"],
                    "strata_doc_type": document["doc_type"],
                    "expected_type": expected_type(document),
                    "source_path": document["path"],
                    "is_scanned": bool(document.get("is_scanned", False)),
                    "entity_ids": document.get("entity_ids", []),
                    "start_page": start_page,
                    "end_page": end_page,
                    "page_count": len(pages),
                }
            )

        stack_name = f"stack-{stack_index:03d}.pdf"
        stack_path = output / "stacks" / stack_name
        with stack_path.open("wb") as handle:
            writer.write(handle)

        ground_truth["stacks"].append(
            {
                "stack_id": f"stack-{stack_index:03d}",
                "path": f"stacks/{stack_name}",
                "page_count": page_cursor - 1,
                "documents": logical_documents,
            }
        )

        ranges = ", ".join(
            f"{d['doc_id']}:{d['start_page']}-{d['end_page']}"
            for d in logical_documents
        )
        print(f"{stack_name}: {page_cursor - 1} pages | {ranges}")

    ground_truth_path = output / "ground-truth.json"
    ground_truth_path.write_text(json.dumps(ground_truth, indent=2), encoding="utf-8")
    print(f"Wrote {ground_truth_path}")


if __name__ == "__main__":
    main()
