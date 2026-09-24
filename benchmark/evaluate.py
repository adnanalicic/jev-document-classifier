#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import requests

DEFAULT_INPUT = Path(__file__).resolve().parent / "out"
DEFAULT_API = "http://localhost:8080/api/classifications"


def safe_div(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def range_for_group(group: dict) -> tuple[int, int]:
    page_indexes = sorted(page["pageIndex"] for page in group["pages"])
    return page_indexes[0], page_indexes[-1]


def page_truth(stack: dict) -> dict[int, str]:
    truth: dict[int, str] = {}
    for document in stack["documents"]:
        for page in range(document["start_page"], document["end_page"] + 1):
            truth[page] = document["expected_type"]
    return truth


def evaluate_stack(stack: dict, response: dict) -> dict:
    actual_ranges = [
        (document["start_page"], document["end_page"])
        for document in stack["documents"]
    ]
    predicted_ranges = [range_for_group(group) for group in response["documents"]]

    actual_boundaries = {end for _, end in actual_ranges[:-1]}
    predicted_boundaries = {end for _, end in predicted_ranges[:-1]}

    tp = len(actual_boundaries & predicted_boundaries)
    fp = len(predicted_boundaries - actual_boundaries)
    fn = len(actual_boundaries - predicted_boundaries)

    truth_by_page = page_truth(stack)
    page_type_correct = 0
    page_type_total = 0
    confusion = Counter()

    for group in response["documents"]:
        for page in group["pages"]:
            expected = truth_by_page[page["pageIndex"]]
            predicted = page["documentType"]
            page_type_total += 1
            page_type_correct += int(expected == predicted)
            confusion[(expected, predicted)] += 1

    exact_ranges = set(actual_ranges) & set(predicted_ranges)
    exact_doc_type_correct = 0
    for group in response["documents"]:
        predicted_range = range_for_group(group)
        if predicted_range not in exact_ranges:
            continue

        expected_type = next(
            doc["expected_type"]
            for doc in stack["documents"]
            if (doc["start_page"], doc["end_page"]) == predicted_range
        )
        exact_doc_type_correct += int(expected_type == group["documentType"])

    return {
        "stack_id": stack["stack_id"],
        "decision_engine": response.get("decisionEngine"),
        "page_count": stack["page_count"],
        "actual_ranges": actual_ranges,
        "predicted_ranges": predicted_ranges,
        "exact_stack": actual_ranges == predicted_ranges,
        "boundary_tp": tp,
        "boundary_fp": fp,
        "boundary_fn": fn,
        "page_type_correct": page_type_correct,
        "page_type_total": page_type_total,
        "exact_documents_reconstructed": len(exact_ranges),
        "document_total": len(actual_ranges),
        "exact_document_type_correct": exact_doc_type_correct,
        "confusion": {
            f"{expected} -> {predicted}": count
            for (expected, predicted), count in sorted(confusion.items())
        },
    }


def markdown_summary(summary: dict) -> str:
    lines = [
        "# Strata benchmark results",
        "",
        f"- Engine: **{summary['decision_engine']}**",
        f"- Stacks: **{summary['stacks']}**",
        f"- Pages: **{summary['pages']}**",
        f"- Boundary precision: **{summary['boundary_precision']:.3f}**",
        f"- Boundary recall: **{summary['boundary_recall']:.3f}**",
        f"- Boundary F1: **{summary['boundary_f1']:.3f}**",
        f"- Page classification accuracy: **{summary['page_type_accuracy']:.3f}**",
        f"- Exact document reconstruction: **{summary['exact_document_reconstruction_rate']:.3f}**",
        f"- Exact stack reconstruction: **{summary['exact_stack_rate']:.3f}**",
        f"- Type accuracy on exactly reconstructed documents: **{summary['exact_document_type_accuracy']:.3f}**",
        "",
        "## Per stack",
        "",
        "| Stack | Actual groups | Predicted groups | Exact |",
        "|---|---:|---:|:---:|",
    ]

    for stack in summary["stack_results"]:
        lines.append(
            f"| {stack['stack_id']} | {len(stack['actual_ranges'])} | "
            f"{len(stack['predicted_ranges'])} | {'✅' if stack['exact_stack'] else '❌'} |"
        )

    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run generated Strata stacks through the local classifier and score grouping/classification."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--api", default=DEFAULT_API)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()

    root = args.input.resolve()
    truth = json.loads((root / "ground-truth.json").read_text(encoding="utf-8"))

    stack_results = []
    engines = set()

    for stack in truth["stacks"]:
        path = root / stack["path"]
        print(f"Evaluating {stack['stack_id']} ({stack['page_count']} pages)...")
        with path.open("rb") as handle:
            response = requests.post(
                args.api,
                files={"files": (path.name, handle, "application/pdf")},
                timeout=args.timeout,
            )
        response.raise_for_status()
        payload = response.json()
        engines.add(payload.get("decisionEngine", "unknown"))
        stack_results.append(evaluate_stack(stack, payload))

    tp = sum(r["boundary_tp"] for r in stack_results)
    fp = sum(r["boundary_fp"] for r in stack_results)
    fn = sum(r["boundary_fn"] for r in stack_results)
    precision = safe_div(tp, tp + fp)
    recall = safe_div(tp, tp + fn)
    f1 = safe_div(2 * precision * recall, precision + recall)

    page_correct = sum(r["page_type_correct"] for r in stack_results)
    page_total = sum(r["page_type_total"] for r in stack_results)
    exact_docs = sum(r["exact_documents_reconstructed"] for r in stack_results)
    doc_total = sum(r["document_total"] for r in stack_results)
    exact_doc_type_correct = sum(
        r["exact_document_type_correct"] for r in stack_results
    )
    exact_stacks = sum(int(r["exact_stack"]) for r in stack_results)

    aggregate_confusion = defaultdict(int)
    for result in stack_results:
        for key, count in result["confusion"].items():
            aggregate_confusion[key] += count

    summary = {
        "decision_engine": ", ".join(sorted(engines)),
        "stacks": len(stack_results),
        "pages": sum(r["page_count"] for r in stack_results),
        "boundary_precision": precision,
        "boundary_recall": recall,
        "boundary_f1": f1,
        "page_type_accuracy": safe_div(page_correct, page_total),
        "exact_document_reconstruction_rate": safe_div(exact_docs, doc_total),
        "exact_stack_rate": safe_div(exact_stacks, len(stack_results)),
        "exact_document_type_accuracy": safe_div(
            exact_doc_type_correct, exact_docs
        ),
        "confusion": dict(sorted(aggregate_confusion.items())),
        "stack_results": stack_results,
    }

    results_path = root / "results.json"
    results_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    markdown_path = root / "results.md"
    markdown_path.write_text(markdown_summary(summary), encoding="utf-8")

    print()
    print(markdown_summary(summary))
    print(f"Detailed results: {results_path}")


if __name__ == "__main__":
    main()
