#!/usr/bin/env python3
"""Structural linter for a trip-planning-brief output.

This checks the *document*, not the trip. It cannot tell whether a plan is good,
sensible or affordable - only whether the required structure is present and
whether a few known-failure patterns appear.

Design note: a linter that always exits 0 is worse than no linter. Every check
below fails closed - if the expected structure cannot be parsed, that is a
FAIL, not a skip. Findings are printed to stdout and the exit code is 1 when
any finding exists.

Usage:
    python3 check-itinerary.py plan.md
    python3 check-itinerary.py plan.md --json

Exit codes: 0 clean, 1 findings, 2 input problem.
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

TIME_RANGE = re.compile(r"(\d{1,2})[:.](\d{2})\s*(?:-|~|–|—|to)\s*(\d{1,2})[:.](\d{2})")
MONEY = re.compile(r"(?<![\w.])(?:[A-Z]{3}|\$|¥|€|£)\s?\d|(?<![\w.])\d{1,3}(?:[,\s]\d{3})*(?:\.\d+)?\s?(?:JPY|USD|CNY|EUR|GBP|KRW|THB|SGD|HKD)")
EST_MARK = re.compile(r"\best\.?\b|\bapprox\.?\b|\bestimate\b|约|估", re.IGNORECASE)
VERIFY_MARK = re.compile(r"\[verify\]|\[待核实\]|\[待确认\]", re.IGNORECASE)

# Claims that must never appear unqualified in a planning-only document.
FORBIDDEN_CLAIMS = [
    (re.compile(r"\bguarantee(?:d|s)?\b|保证", re.IGNORECASE), "guarantee claim"),
    (re.compile(r"\bI (?:have )?booked\b|\breserved for you\b|已预订|已为您预定", re.IGNORECASE), "booking claim"),
    (re.compile(r"\bconfirmation number\b|确认号|订单号[:：]", re.IGNORECASE), "booking artifact"),
    (re.compile(r"\bI (?:have )?paid\b|\bpaid in full\b|\bpayment (?:received|confirmed)\b|已付款|已支付", re.IGNORECASE), "payment claim"),
]

FALLBACK_HEADERS = ("fallback", "备选", "备用")
VERIFY_HEADERS = ("verify", "核实", "待确认", "check")
DAY_HEADERS = ("day", "天", "日期")


@dataclass
class Findings:
    fails: list[str] = field(default_factory=list)
    warns: list[str] = field(default_factory=list)

    def fail(self, msg: str) -> None:
        self.fails.append(msg)

    def warn(self, msg: str) -> None:
        self.warns.append(msg)


def split_row(line: str) -> list[str]:
    body = line.strip()
    if body.startswith("|"):
        body = body[1:]
    if body.endswith("|"):
        body = body[:-1]
    return [c.strip() for c in body.split("|")]


def is_separator(cells: list[str]) -> bool:
    if not cells:
        return False
    return all(re.fullmatch(r":?-{2,}:?", c or "") for c in cells)


def parse_tables(text: str) -> list[tuple[list[str], list[list[str]]]]:
    """Return [(header, rows)] for every markdown table in the document."""
    tables: list[tuple[list[str], list[list[str]]]] = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip().startswith("|") and i + 1 < len(lines) and is_separator(split_row(lines[i + 1])):
            header = [c.lower() for c in split_row(line)]
            rows: list[list[str]] = []
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


def to_minutes(h: str, m: str) -> int:
    return int(h) * 60 + int(m)


def check_day_plan(tables, f: Findings) -> int:
    day_tables = []
    for header, rows in tables:
        if col(header, DAY_HEADERS) is not None and col(header, FALLBACK_HEADERS) is not None:
            day_tables.append((header, rows))

    if not day_tables:
        f.fail("no day-plan table found (need a markdown table with a day column and a fallback column)")
        return 0

    total_rows = 0
    for header, rows in day_tables:
        c_day = col(header, DAY_HEADERS)
        c_block = col(header, ("block", "time", "时段", "时间"))
        c_place = col(header, ("place", "activity", "地点", "活动"))
        c_fall = col(header, FALLBACK_HEADERS)
        c_ver = col(header, VERIFY_HEADERS)

        if c_block is None:
            f.fail("day-plan table has no time-block column")
        if c_ver is None:
            f.fail("day-plan table has no verify column")

        for r_idx, row in enumerate(rows, start=1):
            total_rows += 1
            label = f"day-plan row {r_idx}"

            def cell(idx):
                return row[idx] if idx is not None and idx < len(row) else ""

            if c_day is not None and not cell(c_day):
                f.fail(f"{label}: empty day cell")
            if c_place is not None and not cell(c_place):
                f.fail(f"{label}: empty place/activity cell")

            fb = cell(c_fall).strip().lower()
            if not fb:
                f.fail(f"{label}: empty fallback")
            elif fb in {"rest", "freestyle", "free time", "休息", "自由活动", "tbd", "n/a", "-"}:
                f.fail(f"{label}: fallback is not a real fallback ('{fb}')")

            if c_ver is not None and not cell(c_ver).strip():
                f.fail(f"{label}: empty verify cell")

            block = cell(c_block)
            if c_block is not None and not block.strip():
                f.fail(f"{label}: empty time block")

        # overlapping blocks, per day
        by_day: dict[str, list[tuple[int, int, str]]] = {}
        for row in rows:
            day = row[c_day].strip() if c_day is not None and c_day < len(row) else ""
            block = row[c_block].strip() if c_block is not None and c_block < len(row) else ""
            m = TIME_RANGE.search(block)
            if not m:
                continue
            start = to_minutes(m.group(1), m.group(2))
            end = to_minutes(m.group(3), m.group(4))
            if end <= start:
                f.fail(f"time block '{block}' in day {day or '?'} does not move forward")
                continue
            by_day.setdefault(day, []).append((start, end, block))
        for day, spans in by_day.items():
            spans.sort()
            for (s1, e1, b1), (s2, e2, b2) in zip(spans, spans[1:]):
                if s2 < e1:
                    f.fail(f"overlapping blocks in day {day or '?'}: '{b1}' and '{b2}'")

    if total_rows == 0:
        f.fail("day-plan table is empty")
    return total_rows


def check_budget(tables, text: str, f: Findings) -> None:
    budget_tables = [
        (h, r) for h, r in tables
        if (col(h, ("low", "min", "下限")) is not None and col(h, ("high", "max", "上限")) is not None)
    ]
    if not budget_tables:
        f.fail("no budget table with low/high columns found")

    if not re.search(r"\bbuffer\b|缓冲|机动", text, re.IGNORECASE):
        f.fail("no buffer line in the budget")

    if not re.search(r"\bexclud|\bnot included\b|不含|未包含", text, re.IGNORECASE):
        f.warn("budget does not state what is excluded")

    for header, rows in budget_tables:
        for row in rows:
            joined = " ".join(row)
            for m in MONEY.finditer(joined):
                seg = joined[max(0, m.start() - 8): m.end() + 8]
                if not (EST_MARK.search(seg) or VERIFY_MARK.search(seg)):
                    f.warn(f"money figure without est./[verify] marker: '{m.group(0)}'")


def check_claims(text: str, f: Findings) -> None:
    for pattern, label in FORBIDDEN_CLAIMS:
        for m in pattern.finditer(text):
            f.fail(f"planning-only violation ({label}): '{m.group(0)}'")


def check_verify_coverage(text: str, f: Findings) -> None:
    if not VERIFY_MARK.search(text):
        f.fail("no [verify] markers - unverified hours/prices/closures were asserted as fact")
    n = len(VERIFY_MARK.findall(text))
    if n < 5:
        f.warn(f"only {n} [verify] marker(s); typical plans need more")


def check_notice(text: str, f: Findings) -> None:
    if not re.search(r"nothing (?:is )?booked|not booked|planning only|plan only|未预订|仅供参考", text, re.IGNORECASE):
        f.fail("missing the planning-only notice")


def check_sections(text: str, f: Findings) -> None:
    low = text.lower()
    for name, needles in (
        ("brief", ("trip brief", "行程简报", "概要")),
        ("constraint check", ("constraint", "约束检查", "校验")),
        ("open questions", ("open question", "待确认问题", "开放问题")),
    ):
        if not any(nd in low for nd in needles):
            f.warn(f"section not found: {name}")


def lint(text: str) -> Findings:
    f = Findings()
    if not text.strip():
        f.fail("document is empty")
        return f
    tables = parse_tables(text)
    if not tables:
        f.fail("no markdown tables at all - output structure is not parseable")
        return f
    check_day_plan(tables, f)
    check_budget(tables, text, f)
    check_claims(text, f)
    check_verify_coverage(text, f)
    check_notice(text, f)
    check_sections(text, f)
    return f


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    as_json = "--json" in argv
    if len(args) != 1:
        print("usage: check-itinerary.py <plan.md> [--json]", file=sys.stderr)
        return 2
    path = Path(args[0])
    if not path.is_file():
        print(f"input not found: {path}", file=sys.stderr)
        return 2
    findings = lint(path.read_text(encoding="utf-8", errors="replace"))

    if as_json:
        print(json.dumps(
            {"ok": not findings.fails, "fails": findings.fails, "warns": findings.warns},
            ensure_ascii=False, indent=2,
        ))
    else:
        for msg in findings.fails:
            print(f"FAIL {msg}")
        for msg in findings.warns:
            print(f"WARN {msg}")
        if not findings.fails:
            print(f"OK {path.name}: structure present, {len(findings.warns)} warning(s)")
    return 1 if findings.fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
