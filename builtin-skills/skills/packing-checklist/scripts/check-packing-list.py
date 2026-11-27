#!/usr/bin/env python3
"""Structural linter for a packing-checklist output.

Checks the document's shape, not the trip. Fails closed: if the checklist table
cannot be parsed, that is a FAIL rather than a skip - a linter that always
passes trains people to ignore it.

Usage:
    python3 check-packing-list.py list.md
    python3 check-packing-list.py list.md --json

Exit codes: 0 clean, 1 findings, 2 input problem.
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

WEIGHTS = {"must", "nice", "conditional"}
VERIFY = re.compile(r"\[verify\]|\[待核实\]|\[待确认\]", re.IGNORECASE)

# Items that must carry a quantity formula, keyed by vocabulary in the item name.
FORMULA_REQUIRED = re.compile(
    r"t-?shirt|shirt|top|sock|underwear|boxer|brief|trousers|pants|shorts|"
    r"sock|bra|base layer|thermal|contact lens|sunscreen|shampoo|toothpaste|"
    r"mask|nappy|diaper|wipe|pad|tampon|"
    r"衬衫|衣|袜|内裤|裤|防晒|牙膏|洗发|口罩|尿布",
    re.IGNORECASE,
)
FORMULA_HINT = re.compile(
    r"\(\s*[^)]*(?:\d|/|÷|x|×|worn|wear|buy|rent)[^)]*\)|（[^）]*）",
    re.IGNORECASE,
)

CONDITIONAL_TRIGGER = re.compile(r"\bif\b|\bwhen\b|若|如果|下雨|rain|snow|雪", re.IGNORECASE)

FORBIDDEN = [
    (re.compile(r"https?://|\bbuy here\b|\bshop now\b|\bin stock\b|立即购买|下单链接", re.IGNORECASE),
     "purchase link or stock claim"),
    (re.compile(r"\bguarantee(?:d|s)?\b|保证", re.IGNORECASE), "guarantee claim"),
    (re.compile(r"\bTSA approved\b|\bairline approved\b|已获批准", re.IGNORECASE), "approval claim"),
    (re.compile(r"\bdefinitely allowed\b|\bno problem at security\b|一定能带上飞机", re.IGNORECASE),
     "regulatory assurance"),
]

REQUIRED_SECTIONS = [
    ("packing brief", ("packing brief", "清单简报", "概要")),
    ("wear-on-the-day", ("wear-on-the-day", "wear on the day", "当天穿戴", "身上穿戴")),
    ("notice", ("notice", "说明", "提示")),
]


@dataclass
class Findings:
    fails: list[str] = field(default_factory=list)
    warns: list[str] = field(default_factory=list)

    def fail(self, m: str) -> None:
        self.fails.append(m)

    def warn(self, m: str) -> None:
        self.warns.append(m)


def split_row(line: str) -> list[str]:
    body = line.strip()
    if body.startswith("|"):
        body = body[1:]
    if body.endswith("|"):
        body = body[:-1]
    return [c.strip() for c in body.split("|")]


def is_separator(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r":?-{2,}:?", c or "") for c in cells)


def parse_tables(text: str) -> list[tuple[list[str], list[list[str]]]]:
    tables = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if lines[i].strip().startswith("|") and i + 1 < len(lines) and is_separator(split_row(lines[i + 1])):
            header = [c.lower() for c in split_row(lines[i])]
            rows = []
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            tables.append((header, rows))
            continue
        i += 1
    return tables


def col(header: list[str], needles: tuple[str, ...]) -> int | None:
    for idx, name in enumerate(header):
        if any(n in name for n in needles):
            return idx
    return None


def check_checklist(tables, f: Findings) -> int:
    packs = [
        (h, r) for h, r in tables
        if col(h, ("weight", "权重", "优先级")) is not None and col(h, ("item", "物品", "名称")) is not None
    ]
    if not packs:
        f.fail("no checklist table found (need columns: Item, Qty, Weight, Reason)")
        return 0

    total = 0
    for header, rows in packs:
        c_item = col(header, ("item", "物品", "名称"))
        c_qty = col(header, ("qty", "quantity", "数量"))
        c_wt = col(header, ("weight", "权重", "优先级"))
        c_reason = col(header, ("reason", "理由", "依据"))
        c_note = col(header, ("note", "备注", "说明"))

        if c_qty is None:
            f.fail("checklist table has no quantity column")
        if c_reason is None:
            f.fail("checklist table has no reason column")

        for idx, row in enumerate(rows, start=1):
            total += 1

            def cell(c):
                return row[c].strip() if c is not None and c < len(row) else ""

            item = cell(c_item)
            qty = cell(c_qty)
            weight = cell(c_wt).lower()
            reason = cell(c_reason)
            note = cell(c_note)
            label = f"row {idx} ({item or 'unnamed'})"

            if not item:
                f.fail(f"{label}: empty item name")
            if not reason:
                f.fail(f"{label}: empty reason")
            if not qty:
                f.fail(f"{label}: empty quantity")

            if weight not in WEIGHTS:
                f.fail(f"{label}: weight '{weight}' is not one of must/nice/conditional")

            if weight == "conditional" and not CONDITIONAL_TRIGGER.search(f"{reason} {note}"):
                f.fail(f"{label}: conditional item without a stated trigger")

            if FORMULA_REQUIRED.search(item) and qty and not FORMULA_HINT.search(qty):
                f.warn(f"{label}: quantity '{qty}' has no formula in parentheses")

    if total == 0:
        f.fail("checklist table is empty")
    return total


def check_regulated(tables, text: str, f: Findings) -> None:
    reg = [
        (h, r) for h, r in tables
        if col(h, ("rule area", "规则", "category", "类别")) is not None
        and col(h, ("authority", "权威", "问谁", "check")) is not None
    ]
    if not reg:
        f.warn("no regulated-items table - verify one is genuinely unnecessary")
        return
    for header, rows in reg:
        for idx, row in enumerate(rows, start=1):
            joined = " ".join(row)
            if not VERIFY.search(joined):
                f.fail(f"regulated row {idx}: no [verify] marker")
            if not col(header, ("risk", "后果")) is not None:
                f.warn("regulated table has no risk column")


def check_sections(text: str, f: Findings) -> None:
    low = text.lower()
    for name, needles in REQUIRED_SECTIONS:
        if not any(n in low for n in needles):
            f.fail(f"missing section: {name}")


def check_claims(text: str, f: Findings) -> None:
    for pattern, label in FORBIDDEN:
        for m in pattern.finditer(text):
            f.fail(f"forbidden ({label}): '{m.group(0)}'")


def lint(text: str) -> Findings:
    f = Findings()
    if not text.strip():
        f.fail("document is empty")
        return f
    tables = parse_tables(text)
    if not tables:
        f.fail("no markdown tables - structure not parseable")
        return f
    check_checklist(tables, f)
    check_regulated(tables, text, f)
    check_sections(text, f)
    check_claims(text, f)
    if not VERIFY.search(text):
        f.warn("no [verify] markers at all")
    return f


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    as_json = "--json" in argv
    if len(args) != 1:
        print("usage: check-packing-list.py <list.md> [--json]", file=sys.stderr)
        return 2
    path = Path(args[0])
    if not path.is_file():
        print(f"input not found: {path}", file=sys.stderr)
        return 2
    findings = lint(path.read_text(encoding="utf-8", errors="replace"))

    if as_json:
        print(json.dumps({"ok": not findings.fails, "fails": findings.fails, "warns": findings.warns},
                         ensure_ascii=False, indent=2))
    else:
        for m in findings.fails:
            print(f"FAIL {m}")
        for m in findings.warns:
            print(f"WARN {m}")
        if not findings.fails:
            print(f"OK {path.name}: structure present, {len(findings.warns)} warning(s)")
    return 1 if findings.fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
