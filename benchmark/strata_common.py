from __future__ import annotations

from pathlib import Path

# Strata source types mapped onto the deliberately smaller MVP taxonomy used
# by the Spring Boot application.
TYPE_MAP = {
    "policy_contract": "POLICY",
    "policy_declarations": "POLICY",
    "policy_endorsements": "POLICY",
    "policy_schedule": "POLICY",
    "fnol": "CLAIM_FORM",
    "fnol_scanned": "CLAIM_FORM",
    "accident_statement": "CLAIM_FORM",
    "accident_statement_scanned": "CLAIM_FORM",
    "adjuster_report": "ADJUSTER_REPORT",
    "estimate": "ESTIMATE",
    "settlement_letter": "SETTLEMENT_LETTER",
    "settlement_letter_scanned": "SETTLEMENT_LETTER",
    "denial_letter": "DENIAL_LETTER",
    "denial_letter_scanned": "DENIAL_LETTER",
    "id_card": "IDENTITY_DOCUMENT",
    "id_card_scanned": "IDENTITY_DOCUMENT",
}


def supported(document: dict) -> bool:
    return (
        document.get("doc_type") in TYPE_MAP
        and document.get("format") in {"pdf", "jpg", "jpeg", "png"}
    )


def expected_type(document: dict) -> str:
    return TYPE_MAP[document["doc_type"]]


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]
