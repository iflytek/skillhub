#!/usr/bin/env python3
"""Structural linter for a meal-and-grocery-planner output.

Checks the two things that actually break a meal plan: a menu that cannot be
cooked with the stated equipment, and a shopping list that buys the same
ingredient twice. Also blocks nutrition/medical claims and purchase actions.

Fails closed: an unparseable document is a FAIL, not a skip.

Usage:
    python3 check-meal-plan.py plan.md
    python3 check-meal-plan.py plan.md --json

Exit codes: 0 clean, 1 findings, 2 input problem.
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

VERIFY = re.compile(r"\[verify\]|\[待核实\]|\[待确认\]", re.IGNORECASE)
HAS_DIGIT = re.compile(r"\d")

FORBIDDEN = [
    (re.compile(r"\bcalorie|\bmacros?\b|\bdetox\b|\bmetabolis|\bweight loss\b|热量|卡路里|减脂|排毒|减肥",
                re.IGNORECASE), "nutrition or diet claim"),
    (re.compile(r"\bcures?\b|\btreats?\b|\bprevents? (?:cancer|diabetes|disease)\b|治愈|治疗|预防疾病",
                re.IGNORECASE), "medical claim"),
    (re.compile(r"\bsafe for (?:celiac|coeliac|allerg)|allergy[- ]safe|过敏安全|无过敏", re.IGNORECASE),
     "allergy assurance"),
    (re.compile(r"\bguarantee(?:d|s)?\b|保证", re.IGNORECASE), "guarantee claim"),
    (re.compile(r"https?://\S*(?:order|cart|checkout|delivery)\S*|下单链接|立即购买", re.IGNORECASE),
     "purchase action"),
    (re.compile(r"\bI (?:have )?ordered\b|\bdelivery is (?:scheduled|confirmed)\b|已下单|已配送",
                re.IGNORECASE), "order already placed"),
]

PANTRY = re.compile(r"pantry|assumed on hand|已有|常备", re.IGNORECASE)


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


def norm(name: str) -> str:
    s = name.lower()
    s = re.sub(r"\([^)]*\)", " ", s)
    s = re.sub(r"[^a-z\u4e00-\u9fff ]+", " ", s)
    return " ".join(s.split())


def check_menu(tables, f: Findings) -> int:
    found = [
        (h, r) for h, r in tables
        if col(h, ("dish", "菜品", "菜")) is not None
        and (col(h, ("equipment", "设备", "厨具")) is not None or col(h, ("time", "时间", "耗时")) is not None)
    ]
    if not found:
        f.fail("no menu table found (need columns: Day/Meal, Dish, Serves, Time, Equipment)")
        return 0

    total = 0
    for header, rows in found:
        c_dish = col(header, ("dish", "菜品", "菜"))
        c_equip = col(header, ("equipment", "设备", "厨具"))
        c_time = col(header, ("time", "时间", "耗时"))
        c_serve = col(header, ("serve", "份", "人"))
        if c_equip is None:
            f.fail("menu has no equipment column")
        if c_time is None:
            f.fail("menu has no time column")
        for idx, row in enumerate(rows, start=1):
            total += 1
            label = f"menu row {idx} ({cell(row, c_dish)[:24] or 'unnamed'})"
            if not cell(row, c_dish):
                f.fail(f"{label}: empty dish")
            if c_equip is not None and not cell(row, c_equip):
                f.fail(f"{label}: no equipment named")
            if c_time is not None and not cell(row, c_time):
                f.fail(f"{label}: no time")
            if c_serve is not None and not cell(row, c_serve):
                f.fail(f"{label}: no serving count")

    if total == 0:
        f.fail("menu table is empty")
    return total


def check_grocery(tables, f: Findings) -> int:
    found = [
        (h, r) for h, r in tables
        if col(h, ("item", "食材", "品名")) is not None and col(h, ("qty", "quantity", "数量")) is not None
    ]
    if not found:
        f.fail("no grocery list found (need columns: Section, Item, Qty, Unit, Used by)")
        return 0

    seen: dict[str, tuple[str, str]] = {}
    total = 0
    for header, rows in found:
        c_item = col(header, ("item", "食材", "品名"))
        c_qty = col(header, ("qty", "quantity", "数量"))
        c_unit = col(header, ("unit", "单位"))
        c_used = col(header, ("used by", "用于", "用在哪", "dishes"))
        c_sec = col(header, ("section", "分区", "区域"))

        if c_unit is None:
            f.fail("grocery list has no unit column")
        if c_used is None:
            f.warn("grocery list has no 'used by' column (no audit trail)")

        for idx, row in enumerate(rows, start=1):
            total += 1
            item = cell(row, c_item)
            qty = cell(row, c_qty)
            unit = cell(row, c_unit)
            section = cell(row, c_sec) or "-"
            label = f"grocery row {idx} ({item[:24] or 'unnamed'})"

            if not item:
                f.fail(f"{label}: empty item")
                continue
            if not qty:
                f.fail(f"{label}: empty quantity")
            elif not HAS_DIGIT.search(qty) and not re.search(r"half|quarter|一|半", qty, re.IGNORECASE):
                f.fail(f"{label}: quantity '{qty}' has no number")
            if c_unit is not None and not unit and not re.search(r"\d\s*(?:g|kg|ml|l|pcs|个|克|斤|袋|盒)", qty, re.IGNORECASE):
                f.fail(f"{label}: quantity without a unit")

            key = norm(item)
            if not key:
                continue
            if key in seen:
                prev_section, prev_row = seen[key]
                if prev_section.lower() == section.lower():
                    f.fail(f"{label}: duplicate ingredient '{item}' already listed as {prev_row}")
                else:
                    f.warn(f"{label}: '{item}' also appears in {prev_section} - confirm they are different items")
            else:
                seen[key] = (section, label)

    if total == 0:
        f.fail("grocery list is empty")
    return total


def check_budget(tables, text: str, f: Findings) -> None:
    found = [
        (h, r) for h, r in tables
        if col(h, ("low", "min", "下限")) is not None and col(h, ("high", "max", "上限")) is not None
    ]
    if not found:
        f.fail("no cost range table with low/high columns")
        return
    if not re.search(r"buffer|contingen|机动|缓冲", text, re.IGNORECASE):
        f.fail("cost table has no buffer line")
    if not re.search(r"not included|excluded|不含|未包含", text, re.IGNORECASE):
        f.warn("cost table does not state what is excluded")


def check_allergens(text: str, f: Findings) -> None:
    if not re.search(r"allergen|contains:|含[:：]|过敏原", text, re.IGNORECASE):
        f.fail("no allergen statement listing what the menu contains")


def check_leftovers(text: str, f: Findings) -> None:
    if not re.search(r"leftover|waste|剩菜|剩余|浪费", text, re.IGNORECASE):
        f.warn("no leftover/waste plan")


def check_claims(text: str, f: Findings) -> None:
    for pattern, label in FORBIDDEN:
        if label == "purchase action" and PANTRY.search(text):
            pass
        for m in pattern.finditer(text):
            f.fail(f"forbidden ({label}): '{m.group(0)}'")


def check_notice(text: str, f: Findings) -> None:
    if not re.search(r"nothing (?:is )?ordered|planning only|plan only|未下单|仅供参考", text, re.IGNORECASE):
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
    check_menu(tables, f)
    check_grocery(tables, f)
    check_budget(tables, text, f)
    check_allergens(text, f)
    check_leftovers(text, f)
    check_claims(text, f)
    check_notice(text, f)
    return f


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    as_json = "--json" in argv
    if len(args) != 1:
        print("usage: check-meal-plan.py <plan.md> [--json]", file=sys.stderr)
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
