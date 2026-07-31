#!/usr/bin/env python3
"""Validate the structure and self-contained nature of a SkillHub weekly report."""

from __future__ import annotations

import argparse
import re
import sys
from html.parser import HTMLParser
from pathlib import Path


OVERVIEW_ORDER = (
    "仓库关键信息",
    "功能迭代信息",
    "生态相关进展",
)
HARD_PLACEHOLDERS = ("{{", "}}", "[TODO", "TODO:")
CHART_CLASSES = {"bars", "segbar", "donut", "ecosystem-signals"}
VOID_ELEMENTS = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}
NON_CONTENT_ELEMENTS = {"caption", "h1", "h2", "h3", "h4", "h5", "h6", "th"}


class ReportHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: set[str] = set()
        self.duplicate_ids: set[str] = set()
        self.h1_count = 0
        self.h2_count = 0
        self.table_count = 0
        self.caption_count = 0
        self.tablist_count = 0
        self.tabs: list[tuple[str, str, str, str]] = []
        self.panels: list[tuple[str, str]] = []
        self.noscript_count = 0
        self.external_assets: list[str] = []
        self.text_parts: list[str] = []
        self.panel_text_parts: dict[str, list[str]] = {}
        self.panel_modules: dict[str, int] = {}
        self.current_panel: str | None = None
        self.panel_depth = 0
        self.module_stack: list[dict[str, object]] = []
        self.module_counts: dict[str, int] = {}
        self.module_panels: dict[str, set[str]] = {}
        self.module_names: set[str] = set()
        self.empty_modules: set[str] = set()
        self.table_stack: list[dict[str, int]] = []
        self.empty_table_count = 0
        self.chart_issues: list[str] = []
        self.ignored_depth = 0
        self.non_content_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        is_void = tag in VOID_ELEMENTS
        if self.current_panel is not None and not is_void:
            self.panel_depth += 1
        if not is_void:
            for module in self.module_stack:
                module["depth"] = int(module["depth"]) + 1
            for table in self.table_stack:
                table["depth"] += 1
        if tag in {"style", "script"}:
            self.ignored_depth += 1
        if tag in NON_CONTENT_ELEMENTS:
            self.non_content_depth += 1
        values = dict(attrs)
        class_names = set((values.get("class") or "").split())
        chart_names = sorted(class_names & CHART_CLASSES)
        if chart_names:
            chart_name = "/".join(chart_names)
            if values.get("role") != "img":
                self.chart_issues.append(f".{chart_name} is missing role=img")
            if not (values.get("aria-label") or "").strip():
                self.chart_issues.append(f".{chart_name} is missing a numeric aria-label")
            if not values.get("data-module") and not self.module_stack:
                self.chart_issues.append(f".{chart_name} is not inside a data-module")
        element_id = values.get("id")
        if element_id:
            if element_id in self.ids:
                self.duplicate_ids.add(element_id)
            self.ids.add(element_id)

        if tag == "h1":
            self.h1_count += 1
        elif tag == "h2":
            self.h2_count += 1
        elif tag == "table":
            self.table_count += 1
            self.table_stack.append({"depth": 1, "data_cells": 0})
        elif tag == "td":
            for table in self.table_stack:
                table["data_cells"] += 1
        elif tag == "caption":
            self.caption_count += 1
        elif tag == "noscript":
            self.noscript_count += 1

        role = values.get("role")
        if role == "tablist":
            self.tablist_count += 1
        elif role == "tab":
            self.tabs.append(
                (
                    tag,
                    values.get("id") or "",
                    values.get("aria-controls") or "",
                    values.get("aria-selected") or "",
                )
            )
        elif role == "tabpanel":
            panel_id = values.get("id") or ""
            self.panels.append((panel_id, values.get("aria-labelledby") or ""))
            self.current_panel = panel_id
            self.panel_depth = 1
            self.panel_text_parts.setdefault(panel_id, [])
            self.panel_modules.setdefault(panel_id, 0)

        module_name = values.get("data-module")
        if module_name:
            self.module_names.add(module_name)
            self.module_counts[module_name] = self.module_counts.get(module_name, 0) + 1
            if self.current_panel:
                self.module_panels.setdefault(module_name, set()).add(self.current_panel)
            if is_void:
                self.empty_modules.add(module_name)
            else:
                self.module_stack.append(
                    {"depth": 1, "name": module_name, "has_meaningful_content": False}
                )
            if self.current_panel:
                self.panel_modules[self.current_panel] = self.panel_modules.get(self.current_panel, 0) + 1

        if tag == "script" and values.get("src"):
            self.external_assets.append(f"script src={values['src']}")
        elif tag == "link" and "stylesheet" in (values.get("rel") or ""):
            self.external_assets.append(f"stylesheet href={values.get('href', '')}")
        elif tag in {"img", "source"}:
            source = values.get("src") or values.get("srcset")
            if source and not source.startswith("data:"):
                self.external_assets.append(f"{tag} src={source}")

    def handle_startendtag(
        self, tag: str, attrs: list[tuple[str, str | None]]
    ) -> None:
        self.handle_starttag(tag, attrs)

    def handle_endtag(self, tag: str) -> None:
        if tag in {"style", "script"} and self.ignored_depth:
            self.ignored_depth -= 1
        if tag in NON_CONTENT_ELEMENTS and self.non_content_depth:
            self.non_content_depth -= 1
        for module in self.module_stack:
            module["depth"] = int(module["depth"]) - 1
        while self.module_stack and int(self.module_stack[-1]["depth"]) == 0:
            module = self.module_stack.pop()
            if not module["has_meaningful_content"]:
                self.empty_modules.add(str(module["name"]))
        for table in self.table_stack:
            table["depth"] -= 1
        while self.table_stack and self.table_stack[-1]["depth"] == 0:
            table = self.table_stack.pop()
            if table["data_cells"] == 0:
                self.empty_table_count += 1
        if self.current_panel is not None:
            self.panel_depth -= 1
            if self.panel_depth == 0:
                self.current_panel = None

    def handle_data(self, data: str) -> None:
        if not self.ignored_depth and data.strip():
            text = data.strip()
            self.text_parts.append(text)
            if not self.non_content_depth:
                for module in self.module_stack:
                    module["has_meaningful_content"] = True
            if self.current_panel is not None:
                self.panel_text_parts[self.current_panel].append(text)

    @property
    def text(self) -> str:
        return " ".join(self.text_parts)

    def panel_text(self, panel_id: str) -> str:
        return " ".join(self.panel_text_parts.get(panel_id, []))


def check_order(text: str, errors: list[str]) -> None:
    repository_index = text.find(OVERVIEW_ORDER[0])
    if repository_index < 0:
        errors.append(f"missing required chapter: {OVERVIEW_ORDER[0]}")
        return
    cursor = repository_index
    for heading in OVERVIEW_ORDER[1:]:
        index = text.find(heading)
        if index >= 0 and index < cursor:
            errors.append(f"overview chapter is out of order: {heading}")
        if index >= 0:
            cursor = index


def check_placeholders(text: str, errors: list[str], warnings: list[str]) -> None:
    for token in HARD_PLACEHOLDERS:
        if token in text:
            errors.append(f"unresolved template token: {token}")
    if "待补充" in text:
        warnings.append("report still contains 待补充; acceptable only for maintainer promotion input")


def validate_html(path: Path, strict: bool) -> int:
    source = path.read_text(encoding="utf-8")
    parser = ReportHTMLParser()
    parser.feed(source)

    errors: list[str] = []
    warnings: list[str] = []
    overview_text = parser.panel_text("panel-overview")
    check_order(overview_text or parser.text, errors)
    check_placeholders(source, errors, warnings)

    if parser.h1_count != 1:
        errors.append(f"expected exactly one h1, found {parser.h1_count}")
    if parser.h2_count < 1:
        errors.append("expected at least one h2 heading")
    if parser.tablist_count != 1:
        errors.append(f"expected exactly one tablist, found {parser.tablist_count}")
    if not 2 <= len(parser.tabs) <= 4 or len(parser.tabs) != len(parser.panels):
        errors.append(
            f"expected two to four matching tabs and panels, found {len(parser.tabs)} tab(s) "
            f"and {len(parser.panels)} panel(s)"
        )
    tab_ids = {tab_id for _, tab_id, _, _ in parser.tabs}
    panel_ids = {panel_id for panel_id, _ in parser.panels}
    controlled_panels = {controls for _, _, controls, _ in parser.tabs}
    labelled_tabs = {labelled_by for _, labelled_by in parser.panels}
    if any(tag != "button" for tag, _, _, _ in parser.tabs):
        errors.append("every role=tab element must be a button")
    if any(not tab_id or not controls for _, tab_id, controls, _ in parser.tabs):
        errors.append("every tab must define id and aria-controls")
    if any(not panel_id or not labelled_by for panel_id, labelled_by in parser.panels):
        errors.append("every tabpanel must define id and aria-labelledby")
    if controlled_panels != panel_ids:
        errors.append("tab aria-controls values do not match tabpanel ids")
    if labelled_tabs != tab_ids:
        errors.append("tabpanel aria-labelledby values do not match tab ids")
    if sum(selected == "true" for _, _, _, selected in parser.tabs) != 1:
        errors.append("exactly one tab must have aria-selected=true")
    if not {"panel-overview", "panel-method"}.issubset(panel_ids):
        errors.append("overview and data panels are required")
    if "repository-summary" not in parser.module_names:
        errors.append("missing required repository-summary module")
    elif parser.module_panels.get("repository-summary") != {"panel-overview"}:
        errors.append("repository-summary must appear in panel-overview")
    duplicate_modules = sorted(
        name for name, count in parser.module_counts.items() if count > 1
    )
    if duplicate_modules:
        errors.append(f"duplicate module names: {', '.join(duplicate_modules)}")
    if parser.empty_modules:
        errors.append(
            "modules without meaningful content: "
            f"{', '.join(sorted(parser.empty_modules))}"
        )
    if parser.empty_table_count:
        errors.append(f"empty tables without data cells: {parser.empty_table_count}")
    if parser.module_stack:
        errors.append("unclosed data-module element")
    if parser.table_stack:
        errors.append("unclosed table element")
    empty_panels = sorted(
        panel_id for panel_id, module_count in parser.panel_modules.items() if module_count == 0
    )
    if empty_panels:
        errors.append(f"panels without modules: {', '.join(empty_panels)}")
    if parser.noscript_count < 1:
        errors.append("expected a no-JavaScript fallback")
    if "location.hash" not in source:
        errors.append("expected direct-hash tab activation")
    if parser.duplicate_ids:
        errors.append(f"duplicate ids: {', '.join(sorted(parser.duplicate_ids))}")
    if parser.external_assets:
        errors.append("external file dependencies: " + "; ".join(parser.external_assets))
    if parser.chart_issues:
        errors.extend(parser.chart_issues)
    if parser.caption_count < parser.table_count:
        warnings.append(
            f"{parser.table_count - parser.caption_count} table(s) have no caption; "
            "a nearby heading may be sufficient, but verify accessibility"
        )
    if "期后进展" in parser.text and "不计入" not in parser.text:
        errors.append("post-period progress is present without an explicit exclusion from weekly totals")
    if "下周关注" not in parser.text:
        warnings.append("report has no 下周关注 block")
    overview_characters = len(re.findall(r"[\u4e00-\u9fff]", overview_text))
    if overview_characters > 1800:
        warnings.append(
            f"overview contains about {overview_characters} Chinese characters; "
            "compact overviews should usually stay under 1800"
        )

    return emit(path, errors, warnings, strict)


def validate_markdown(path: Path, strict: bool) -> int:
    source = path.read_text(encoding="utf-8")
    text = re.sub(r"[`*_>#|]", "", source)
    errors: list[str] = []
    warnings: list[str] = []
    check_order(text, errors)
    check_placeholders(source, errors, warnings)

    if len(re.findall(r"^# ", source, flags=re.MULTILINE)) != 1:
        errors.append("expected exactly one level-one heading")
    if "期后" in source and "不计入" not in source:
        errors.append("post-period progress is present without an explicit exclusion from weekly totals")
    if "下周关注" not in source:
        warnings.append("report has no 下周关注 block")
    chinese_characters = len(re.findall(r"[\u4e00-\u9fff]", source))
    if chinese_characters > 2500:
        warnings.append(
            f"report contains about {chinese_characters} Chinese characters; "
            "compact reports should usually stay under 2500"
        )

    return emit(path, errors, warnings, strict)


def emit(path: Path, errors: list[str], warnings: list[str], strict: bool) -> int:
    print(f"Validating {path}")
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors or (strict and warnings):
        print(f"FAIL: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"PASS: 0 errors, {len(warnings)} warning(s)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--strict", action="store_true", help="treat warnings as failures")
    args = parser.parse_args()

    if not args.report.is_file():
        parser.error(f"report not found: {args.report}")
    suffix = args.report.suffix.lower()
    if suffix in {".html", ".htm"}:
        return validate_html(args.report, args.strict)
    if suffix in {".md", ".markdown"}:
        return validate_markdown(args.report, args.strict)
    parser.error("report must be Markdown or HTML")
    return 2


if __name__ == "__main__":
    sys.exit(main())
