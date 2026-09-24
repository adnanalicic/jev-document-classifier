#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

from strata_common import supported

RAW_BASE = "https://raw.githubusercontent.com/NikolaiSachok/strata-insurance-corpus/main/sample"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "data" / "strata-sample"


def download(url: str, target: Path) -> bytes:
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url) as response:
        content = response.read()
    target.write_bytes(content)
    return content


def sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Download the supported subset of the public Strata sample corpus."
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--all-supported",
        action="store_true",
        help="Download every supported sample document. This is the default behavior today and is kept as an explicit flag for scripts.",
    )
    args = parser.parse_args()

    output = args.output.resolve()
    manifest_bytes = download(f"{RAW_BASE}/manifest.json", output / "manifest.json")
    manifest = json.loads(manifest_bytes)

    documents = [d for d in manifest["documents"] if supported(d)]
    print(f"Strata sample contains {len(manifest['documents'])} documents.")
    print(f"Downloading {len(documents)} benchmark-compatible PDF/image documents to {output}")

    failures: list[str] = []
    for index, document in enumerate(documents, start=1):
        relative_path = document["path"]
        target = output / relative_path
        content = download(f"{RAW_BASE}/{relative_path}", target)

        expected_sha = document.get("sha256")
        actual_sha = sha256(content)
        if expected_sha and actual_sha != expected_sha:
            failures.append(
                f"{document['doc_id']}: sha256 mismatch expected={expected_sha} actual={actual_sha}"
            )

        print(f"[{index:02d}/{len(documents):02d}] {document['doc_id']} -> {relative_path}")

    if failures:
        raise SystemExit("\n".join(failures))

    summary = {
        "source": "NikolaiSachok/strata-insurance-corpus sample",
        "manifest_sha256": sha256(manifest_bytes),
        "downloaded_documents": len(documents),
    }
    (output / "download-summary.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )
    print("Download complete; SHA-256 verification passed.")


if __name__ == "__main__":
    main()
