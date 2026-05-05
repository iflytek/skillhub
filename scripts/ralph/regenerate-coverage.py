#!/usr/bin/env python3
"""
Regenerate coverage tracking files from module-level JaCoCo CSV reports.
Source of truth: server/*/target/site/jacoco/jacoco.csv
"""
import csv
import glob
import os
from collections import defaultdict

MODULES = [
    "skillhub-app",
    "skillhub-auth",
    "skillhub-domain",
    "skillhub-infra",
    "skillhub-notification",
    "skillhub-search",
    "skillhub-storage",
]

# Classes explicitly excluded from the production coverage gate scope.
# These are mock/dev/local-only classes not on the MySQL main path.
EXCLUDED_FROM_GATE = {
    "com.iflytek.skillhub.controller.MockUassController",
    "com.iflytek.skillhub.controller.DeviceAuthController",
    "com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest",
    "com.iflytek.skillhub.controller.DeviceAuthWebController",
    "com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest",
    "com.iflytek.skillhub.dto.MockUassLoginRequest",
    "com.iflytek.skillhub.dto.MockUassLoginResponse",
    "com.iflytek.skillhub.bootstrap.LocalDevDataInitializer",
    "com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer",
    "com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer.IndexInspection",
    "com.iflytek.skillhub.auth.mock.MockAuthFilter",
    "com.iflytek.skillhub.auth.device.DeviceAuthService",
    "com.iflytek.skillhub.auth.device.DeviceCodeData",
    "com.iflytek.skillhub.auth.device.DeviceCodeResponse",
    "com.iflytek.skillhub.auth.device.DeviceCodeStatus",
    "com.iflytek.skillhub.auth.device.DeviceTokenResponse",
}


def parse_module_csv(module):
    path = f"server/{module}/target/site/jacoco/jacoco.csv"
    classes = []
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            pkg = row["PACKAGE"]
            cls = row["CLASS"]
            missed = int(row["LINE_MISSED"])
            covered = int(row["LINE_COVERED"])
            total = missed + covered
            coverage = (covered / total * 100) if total > 0 else 0.0
            fqcn = f"{pkg}.{cls}"
            classes.append({
                "module": module,
                "package": pkg,
                "class": cls,
                "fqcn": fqcn,
                "missed": missed,
                "covered": covered,
                "total": total,
                "coverage": coverage,
                "excluded": fqcn in EXCLUDED_FROM_GATE,
            })
    return classes


def generate():
    all_classes = []
    for mod in MODULES:
        all_classes.extend(parse_module_csv(mod))

    # Filter to classes with missed lines for the inventory
    uncovered = [c for c in all_classes if c["missed"] > 0]

    # Compute totals
    total_lines = sum(c["total"] for c in all_classes)
    covered_lines = sum(c["covered"] for c in all_classes)
    missed_lines = sum(c["missed"] for c in all_classes)
    overall_coverage = (covered_lines / total_lines * 100) if total_lines > 0 else 0.0

    # Compute per-module totals
    module_stats = {}
    for mod in MODULES:
        mod_classes = [c for c in all_classes if c["module"] == mod]
        mod_total = sum(c["total"] for c in mod_classes)
        mod_covered = sum(c["covered"] for c in mod_classes)
        mod_missed = sum(c["missed"] for c in mod_classes)
        module_stats[mod] = {
            "total": mod_total,
            "covered": mod_covered,
            "missed": mod_missed,
            "coverage": (mod_covered / mod_total * 100) if mod_total > 0 else 0.0,
        }

    # Baseline file
    baseline_lines = [
        "# MySQL Main Path Coverage Baseline",
        f"Generated: {os.popen('date +%Y-%m-%d').read().strip()}",
        "",
        "## Scope Definition",
        "",
        "- **Profile:** MySQL main path",
        "- **Source of truth:** Module-level JaCoCo CSVs at `server/*/target/site/jacoco/jacoco.csv`",
        "- **Report command:** `mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`",
        "- **Aggregate report path:** `server/skillhub-app/target/site/jacoco-aggregate/index.html`",
        "",
        "### Explicit Exclusions from Production Gate",
        "",
        "The following classes are tracked but explicitly excluded from the production coverage gate because they are mock, dev-only, or platform-specific:",
        "",
    ]
    for fqcn in sorted(EXCLUDED_FROM_GATE):
        baseline_lines.append(f"- `{fqcn}`")
    baseline_lines.extend([
        "",
        "## Overall Line Coverage",
        "| Metric | Value |",
        "|--------|-------|",
        f"| Total Lines | {total_lines} |",
        f"| Covered Lines | {covered_lines} |",
        f"| Missed Lines | {missed_lines} |",
        f"| Line Coverage | {overall_coverage:.2f}% |",
        "",
        "## Per-Module Line Coverage",
        "| Module | Total Lines | Covered | Missed | Coverage |",
        "|--------|-------------|---------|--------|----------|",
    ])
    for mod in MODULES:
        s = module_stats[mod]
        baseline_lines.append(
            f"| {mod} | {s['total']} | {s['covered']} | {s['missed']} | {s['coverage']:.2f}% |"
        )

    baseline_lines.extend([
        "",
        "## Uncovered Classes Inventory (by module, sorted by missed lines desc)",
    ])

    for mod in MODULES:
        mod_uncovered = [c for c in uncovered if c["module"] == mod]
        if not mod_uncovered:
            continue
        mod_uncovered.sort(key=lambda c: (-c["missed"], c["fqcn"]))
        baseline_lines.extend([
            f"",
            f"### {mod}",
            "| Class | Package | Missed Lines | Covered Lines |",
            "|-------|---------|--------------|---------------|",
        ])
        for c in mod_uncovered:
            baseline_lines.append(
                f"| {c['class']} | {c['package']} | {c['missed']} | {c['covered']} |"
            )

    with open("server/coverage-baseline.md", "w") as f:
        f.write("\n".join(baseline_lines) + "\n")

    # Inventory file
    inventory_lines = [
        "# Java Unit Test Line Coverage Inventory",
        "",
        "当前清单基于 `server/*/target/site/jacoco/jacoco.csv` 生成。",
        "",
        "说明：",
        "- 仅列出 `line_missed > 0` 的类",
        "- 指标为 Java 生产代码的 JaCoCo line coverage",
        "- 按模块分组，便于后续拆批补测",
        "- 报告命令：`mvn -f server/pom.xml -pl skillhub-app -am test jacoco:report-aggregate`",
        "- 聚合报告路径：`server/skillhub-app/target/site/jacoco-aggregate/index.html`",
        f"- 当前后端多模块 aggregate line coverage 为 `{overall_coverage:.2f}%`（`{covered_lines}/{total_lines}`）",
        "",
        "### Explicit Exclusions from Production Gate",
        "",
        "以下类虽被跟踪，但因属于 mock / dev-only / platform-specific，明确排除在覆盖率门禁之外：",
        "",
    ]
    for fqcn in sorted(EXCLUDED_FROM_GATE):
        inventory_lines.append(f"- `{fqcn}`")
    inventory_lines.append("")

    for mod in MODULES:
        mod_uncovered = [c for c in uncovered if c["module"] == mod]
        mod_uncovered.sort(key=lambda c: (-c["missed"], c["fqcn"]))
        inventory_lines.extend([
            f"",
            f"## {mod}",
            "",
        ])
        if not mod_uncovered:
            inventory_lines.append("（本模块所有生产类 line_missed = 0）\n")
            continue
        inventory_lines.extend([
            "| Class | Line Missed | Line Covered | Line Coverage |",
            "|------|------:|------:|------:|",
        ])
        for c in mod_uncovered:
            inventory_lines.append(
                f"| `{c['fqcn']}` | {c['missed']} | {c['covered']} | {c['coverage']:.2f}% |"
            )

    with open("docs/prds/java-unit-line-coverage-inventory.md", "w") as f:
        f.write("\n".join(inventory_lines) + "\n")

    print(f"Done. Overall: {covered_lines}/{total_lines} = {overall_coverage:.2f}%")
    print(f"Uncovered classes: {len(uncovered)}")
    print(f"Excluded from gate: {len([c for c in uncovered if c['excluded']])}")


if __name__ == "__main__":
    generate()
