#!/usr/bin/env python3
"""Structural linter for an event-planner output.

Checks that the plan is executable on paper: every timeline line has one owner
and a definition of done, every quantity has a driver, the budget has ranges and
a contingency, and the emergency sheet has enough real rows.

Fails closed: an unparseable document is a FAIL, not a skip.

Usage:
    python3 check-event-plan.py plan.md
    python3 check-event-plan.py plan.md --json

Exit codes: 0 clean, 1 findings, 2 input problem.
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

VERIFY = re.compile(r"\[verify\]|\[待核实\]|\[待确认\]", re.IGNORECASE)
NON_OWNER = {"team", "everyone", "all", "tbd", "tbc", "-", "n/a", "团队", "大家", "待定"}

FORBIDDEN = [
    (re.compile(r"\bI (?:have )?(?:sent|booked|reserved|paid)\b|\binvitations? (?:have been |were )?sent\b|"
                r"已发送|已预订|已付款|已下单", re.IGNORECASE), "action already performed"),
    (re.compile(r"\bguarantee(?:d|s)?\b|保证", re.IGNORECASE), "guarantee claim"),
    (re.compile(r"\b(fire|occupancy) (?:code|limit) (?:is|allows)\b|\blicen[cs]e (?:is )?(?:not )?required\b|"
                r"无需许可|消防限员为", re.IGNORECASE), "regulatory ruling"),
    (re.compile(r"https?://\S*(?:book|reserve|checkout|pay)\S*", re.IGNORECASE), "booking link"),
]

MIN_EMERGENCY_ROWS = 4
MIN_TIMELINE_ROWS = 4


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


def cell(row: list[str], idx: int | None) -> str:
    return row[idx].strip() if idx is not None and idx < len(row) else ""


def check_timeline(tables, f: Findings) -> int:
    found = [
        (h, r) for h, r in tables
        if col(h, ("owner", "负责人", "谁")) is not None and col(h, ("task", "任务", "事项")) is not None
    ]
    if not found:
        f.fail("no countdown timeline found (need columns: When, Task, Owner, Depends on, Done when)")
        return 0

    total = 0
    for header, rows in found:
        c_task = col(header, ("task", "任务", "事项"))
        c_owner = col(header, ("owner", "负责人", "谁"))
        c_done = col(header, ("done", "完成标准", "验收"))
        c_dep = col(header, ("depend", "依赖", "前置"))
        if c_done is None:
            f.fail("timeline has no 'done when' column")
        if c_dep is None:
            f.fail("timeline has no 'depends on' column")
        for idx, row in enumerate(rows, start=1):
            total += 1
            task = cell(row, c_task)
            owner = cell(row, c_owner)
            label = f"timeline row {idx} ({task[:24] or 'unnamed'})"
            if not task:
                f.fail(f"{label}: empty task")
            if not owner:
                f.fail(f"{label}: no owner")
            elif owner.lower().strip("* ") in NON_OWNER:
                f.fail(f"{label}: owner '{owner}' is not a role")
            if c_done is not None and not cell(row, c_done):
                f.fail(f"{label}: no definition of done")

    if total < MIN_TIMELINE_ROWS:
        f.fail(f"timeline has {total} row(s); fewer than {MIN_TIMELINE_ROWS} cannot cover a runway")
    return total


def check_materials(tables, f: Findings) -> None:
    found = [
        (h, r) for h, r in tables
        if col(h, ("qty", "quantity", "数量")) is not None and col(h, ("item", "物料", "物品")) is not None
    ]
    if not found:
        f.fail("no materials table found")
        return
    for header, rows in found:
        c_qty = col(header, ("qty", "quantity", "数量"))
        c_basis = col(header, ("basis", "driver", "依据", "驱动"))
        c_item = col(header, ("item", "物料", "物品"))
        if c_basis is None:
            f.fail("materials table has no unit-basis column")
            continue
        for idx, row in enumerate(rows, start=1):
            label = f"materials row {idx} ({cell(row, c_item)[:24] or 'unnamed'})"
            if not cell(row, c_qty):
                f.fail(f"{label}: empty quantity")
            if not cell(row, c_basis):
                f.fail(f"{label}: quantity without a driver")


def check_budget(tables, text: str, f: Findings) -> None:
    found = [
        (h, r) for h, r in tables
        if col(h, ("low", "min", "下限")) is not None and col(h, ("high", "max", "上限")) is not None
    ]
    if not found:
        f.fail("no budget table with low/high columns")
        return
    if not re.search(r"contingenc|buffer|机动|预备", text, re.IGNORECASE):
        f.fail("budget has no contingency line")
    if not re.search(r"known[- ]miss|excluded|not included|不含|未包含|遗漏", text, re.IGNORECASE):
        f.warn("budget does not list known misses / exclusions")
    if not re.search(r"overrun", text, re.IGNORECASE):
        f.warn("budget does not mark overrun risk per line")


def check_emergency(tables, f: Findings) -> None:
    found = [
        (h, r) for h, r in tables
        if col(h, ("fallback", "备选", "兜底")) is not None
        and col(h, ("signal", "信号", "触发")) is not None
    ]
    if not found:
        f.fail("no emergency sheet (need columns: Failure, First signal, Who acts, Fallback, Cost cap)")
        return
    for header, rows in found:
        c_cap = col(header, ("cost cap", "成本", "上限"))
        if c_cap is None:
            f.fail("emergency sheet has no cost-cap column")
        if len(rows) < MIN_EMERGENCY_ROWS:
            f.fail(f"emergency sheet has {len(rows)} row(s); at least {MIN_EMERGENCY_ROWS} required")
        for idx, row in enumerate(rows, start=1):
            if not cell(row, col(header, ("fallback", "备选", "兜底"))):
                f.fail(f"emergency row {idx}: empty fallback")
            if c_cap is not None and not cell(row, c_cap):
                f.fail(f"emergency row {idx}: no cost cap")


def check_run_of_show(tables, f: Findings) -> None:
    found = [
        (h, r) for h, r in tables
        if col(h, ("time", "时间")) is not None
        and col(h, ("what", "内容", "事项")) is not None
        and col(h, ("who", "负责")) is not None
    ]
    if not found:
        f.warn("no run-of-show table found (Time, What happens, Who owns)")
        return
    for header, rows in found:
        if col(header, ("slip", "延误", "变化")) is None:
            f.warn("run of show has no 'if it slips' column")


def check_claims(text: str, f: Findings) -> None:
    for pattern, label in FORBIDDEN:
        for m in pattern.finditer(text):
            f.fail(f"forbidden ({label}): '{m.group(0)}'")


def check_notice(text: str, f: Findings) -> None:
    if not re.search(r"nothing (?:is )?(?:sent|booked)|planning only|plan only|未发送|未预订|仅供参考", text, re.IGNORECASE):
        f.fail("missing the planning-only notice")


def lint(text: str) -> Findings:
    f = Findings()
    if not text.strip():
        f.fail("document is empty")
        return f
    tables = parse_tables(text)
    if not tables:
        f.fail("no markdown tables - structure not parseable")
        return f
    check_timeline(tables, f)
    check_materials(tables, f)
    check_budget(tables, text, f)
    check_emergency(tables, f)
    check_run_of_show(tables, f)
    check_claims(text, f)
    check_notice(text, f)
    return f


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    as_json = "--json" in argv
    if len(args) != 1:
        print("usage: check-event-plan.py <plan.md> [--json]", file=sys.stderr)
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
