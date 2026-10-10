#!/usr/bin/env python3
"""Validate and reproducibly package SkillHub's reviewed built-in Skills."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import zipfile
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE_ROOT = REPO_ROOT / "builtin-skills" / "skills"
DEFAULT_CATALOG = REPO_ROOT / "builtin-skills" / "catalog.json"
DEFAULT_EVALS = REPO_ROOT / "builtin-skills" / "evals.json"
DEFAULT_OUTPUT = REPO_ROOT / "builtin-skills" / "dist"
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
MAX_FILE_COUNT = 500
MAX_SINGLE_FILE_SIZE = 10 * 1024 * 1024
MAX_TOTAL_PACKAGE_SIZE = 100 * 1024 * 1024
ALLOWED_EXTENSIONS = {
    ".md", ".txt", ".json", ".yaml", ".yml", ".html", ".css", ".csv", ".pdf",
    ".toml", ".xml", ".xsd", ".xsl", ".dtd", ".ini", ".cfg", ".env",
    ".js", ".cjs", ".mjs", ".ts", ".py", ".sh", ".rb", ".go", ".rs", ".java",
    ".kt", ".lua", ".sql", ".r", ".bat", ".ps1", ".zsh", ".bash",
    ".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp", ".ico",
    ".doc", ".xls", ".ppt", ".docx", ".xlsx", ".pptx",
}
SLUG_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")


class BuildError(RuntimeError):
    """Raised when reviewed source no longer satisfies the release contract."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE_ROOT)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--evals", type=Path, default=DEFAULT_EVALS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise BuildError(f"cannot read JSON from {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise BuildError(f"{path} must contain a JSON object")
    return value


def require_text(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise BuildError(f"{field} must be a non-empty string")
    return value.strip()


def load_catalog(path: Path) -> list[dict[str, Any]]:
    catalog = load_json(path)
    if catalog.get("schemaVersion") != 1:
        raise BuildError(f"{path} has an unsupported schemaVersion")
    skills = catalog.get("skills")
    if not isinstance(skills, list) or not skills:
        raise BuildError(f"{path} must contain a non-empty skills array")

    seen: set[str] = set()
    normalized: list[dict[str, Any]] = []
    for index, raw in enumerate(skills):
        if not isinstance(raw, dict):
            raise BuildError(f"catalog skill {index} must be an object")
        slug = require_text(raw.get("slug"), f"catalog skill {index}.slug")
        version = require_text(raw.get("version"), f"catalog skill {index}.version")
        license_id = require_text(raw.get("license"), f"catalog skill {index}.license")
        if not SLUG_PATTERN.fullmatch(slug):
            raise BuildError(f"invalid catalog slug: {slug}")
        if not VERSION_PATTERN.fullmatch(version):
            raise BuildError(f"invalid catalog version for {slug}: {version}")
        if slug in seen:
            raise BuildError(f"duplicate catalog slug: {slug}")
        seen.add(slug)

        upstream = raw.get("upstream")
        if not isinstance(upstream, dict):
            raise BuildError(f"catalog upstream for {slug} must be an object")
        repository = require_text(upstream.get("repository"), f"{slug}.upstream.repository")
        commit = require_text(upstream.get("commit"), f"{slug}.upstream.commit")
        upstream_path = require_text(upstream.get("path"), f"{slug}.upstream.path")
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise BuildError(f"catalog upstream commit for {slug} must be a full Git SHA")
        normalized.append(
            {
                "slug": slug,
                "version": version,
                "license": license_id,
                "upstream": {
                    "repository": repository,
                    "commit": commit,
                    "path": upstream_path,
                },
            }
        )

    slugs = [item["slug"] for item in normalized]
    if slugs != sorted(slugs):
        raise BuildError("catalog skills must be sorted by slug")
    return normalized


def validate_evals(path: Path, expected_slugs: set[str]) -> None:
    evals = load_json(path)
    if evals.get("schemaVersion") != 1:
        raise BuildError(f"{path} has an unsupported schemaVersion")
    cases = evals.get("cases")
    if not isinstance(cases, list):
        raise BuildError(f"{path} must contain a cases array")

    seen: set[str] = set()
    for index, case in enumerate(cases):
        if not isinstance(case, dict):
            raise BuildError(f"eval case {index} must be an object")
        slug = require_text(case.get("slug"), f"eval case {index}.slug")
        require_text(case.get("prompt"), f"eval case {slug}.prompt")
        for field in ("acceptance", "forbidden"):
            values = case.get(field)
            if (
                not isinstance(values, list)
                or not values
                or any(not isinstance(item, str) or not item.strip() for item in values)
            ):
                raise BuildError(f"eval case {slug}.{field} must be a non-empty string array")
        if slug in seen:
            raise BuildError(f"duplicate eval case for {slug}")
        seen.add(slug)

    if seen != expected_slugs:
        missing = sorted(expected_slugs - seen)
        extra = sorted(seen - expected_slugs)
        raise BuildError(f"eval inventory mismatch; missing={missing}, extra={extra}")


def parse_frontmatter(path: Path) -> dict[str, str]:
    try:
        content = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise BuildError(f"cannot read {path}: {exc}") from exc
    lines = content.splitlines()
    if not lines or lines[0].strip() != "---":
        raise BuildError(f"{path} is missing opening frontmatter delimiter")
    try:
        closing_index = next(
            index for index, line in enumerate(lines[1:], start=1) if line.strip() == "---"
        )
    except StopIteration as exc:
        raise BuildError(f"{path} is missing closing frontmatter delimiter") from exc

    fields: dict[str, str] = {}
    for line in lines[1:closing_index]:
        if line.startswith((" ", "\t", "#")) or ":" not in line:
            continue
        key, raw_value = line.split(":", 1)
        key = key.strip()
        value = raw_value.strip()
        if key and value:
            fields[key] = value.strip("\"'")
    return fields


def collect_files(skill_dir: Path) -> list[Path]:
    files: list[Path] = []
    for root, directory_names, file_names in os.walk(skill_dir, followlinks=False):
        root_path = Path(root)
        for name in directory_names:
            directory = root_path / name
            if directory.is_symlink():
                raise BuildError(f"symbolic links are not allowed: {directory}")
        for name in file_names:
            path = root_path / name
            if path.is_symlink():
                raise BuildError(f"symbolic links are not allowed: {path}")
            if not path.is_file():
                raise BuildError(f"unsupported package entry: {path}")
            files.append(path)
    return sorted(files, key=lambda path: path.relative_to(skill_dir).as_posix())


def validate_skill(skill: dict[str, Any], source_root: Path) -> list[Path]:
    slug = skill["slug"]
    skill_dir = source_root / slug
    if not skill_dir.is_dir():
        raise BuildError(f"missing skill directory: {skill_dir}")

    for name in ("SKILL.md", "LICENSE.txt", "NOTICE.md"):
        if not (skill_dir / name).is_file():
            raise BuildError(f"{slug} is missing required file {name}")

    fields = parse_frontmatter(skill_dir / "SKILL.md")
    for field in ("name", "description", "version", "license"):
        if not fields.get(field):
            raise BuildError(f"{slug}/SKILL.md is missing top-level {field}")
    if fields["name"] != slug:
        raise BuildError(f"{slug}/SKILL.md name must equal its directory slug")
    if fields["version"] != skill["version"]:
        raise BuildError(
            f"{slug}/SKILL.md version {fields['version']} does not match catalog {skill['version']}"
        )
    if fields["license"] != skill["license"]:
        raise BuildError(
            f"{slug}/SKILL.md license {fields['license']} does not match catalog {skill['license']}"
        )

    notice = (skill_dir / "NOTICE.md").read_text(encoding="utf-8")
    upstream = skill["upstream"]
    for expected in (upstream["repository"], upstream["commit"], skill["license"]):
        if expected not in notice:
            raise BuildError(f"{slug}/NOTICE.md is missing provenance value: {expected}")

    files = collect_files(skill_dir)
    if len(files) > MAX_FILE_COUNT:
        raise BuildError(f"{slug} has {len(files)} files; maximum is {MAX_FILE_COUNT}")
    total_size = 0
    for path in files:
        relative = path.relative_to(skill_dir).as_posix()
        if path.suffix.lower() not in ALLOWED_EXTENSIONS:
            raise BuildError(f"{slug} contains a disallowed extension: {relative}")
        size = path.stat().st_size
        if size > MAX_SINGLE_FILE_SIZE:
            raise BuildError(f"{slug}/{relative} exceeds the single-file size limit")
        total_size += size
    if total_size > MAX_TOTAL_PACKAGE_SIZE:
        raise BuildError(f"{slug} exceeds the total package size limit")
    return files


def write_zip(skill_dir: Path, files: list[Path], destination: Path) -> None:
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as archive:
        for path in files:
            relative = path.relative_to(skill_dir).as_posix()
            info = zipfile.ZipInfo(relative, FIXED_ZIP_TIME)
            info.create_system = 3
            mode = 0o755 if path.stat().st_mode & stat.S_IXUSR else 0o644
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.compress_type = zipfile.ZIP_STORED
            archive.writestr(info, path.read_bytes())
    temporary.replace(destination)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ensure_inventory(source_root: Path, expected_slugs: set[str]) -> None:
    try:
        children = list(source_root.iterdir())
    except OSError as exc:
        raise BuildError(f"cannot read source root {source_root}: {exc}") from exc
    actual_slugs = {
        path.name for path in children if path.is_dir() and not path.name.startswith(".")
    }
    if actual_slugs != expected_slugs:
        missing = sorted(expected_slugs - actual_slugs)
        extra = sorted(actual_slugs - expected_slugs)
        raise BuildError(f"source inventory mismatch; missing={missing}, extra={extra}")


def build(args: argparse.Namespace) -> None:
    skills = load_catalog(args.catalog.resolve())
    expected_slugs = {skill["slug"] for skill in skills}
    source_root = args.source_root.resolve()
    output = args.output.resolve()
    ensure_inventory(source_root, expected_slugs)
    validate_evals(args.evals.resolve(), expected_slugs)

    output.mkdir(parents=True, exist_ok=True)
    expected_names = {f"{skill['slug']}-{skill['version']}.zip" for skill in skills}
    stale = sorted(path.name for path in output.glob("*.zip") if path.name not in expected_names)
    if stale:
        raise BuildError(f"output contains stale ZIP files; remove them explicitly: {stale}")

    artifacts: list[dict[str, Any]] = []
    for skill in skills:
        slug = skill["slug"]
        files = validate_skill(skill, source_root)
        filename = f"{slug}-{skill['version']}.zip"
        destination = output / filename
        write_zip(source_root / slug, files, destination)
        artifacts.append(
            {
                "slug": slug,
                "version": skill["version"],
                "file": filename,
                "sha256": sha256(destination),
                "size": destination.stat().st_size,
            }
        )

    (output / "artifacts.json").write_text(
        json.dumps({"schemaVersion": 1, "artifacts": artifacts}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Built {len(artifacts)} reviewed Skill packages in {output}")


def main() -> int:
    try:
        build(parse_args())
    except BuildError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
