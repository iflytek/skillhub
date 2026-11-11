#!/usr/bin/env python3
"""Generate upstream OpenSpec skills with a thin, checked SkillHub reading entry.

Uses an isolated OpenSpec profile; never changes global profiles, hook trust,
project requirements, or task state. The manifest protects local edits on update.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

OPENSPEC_VERSION = "1.17.0"
WORKFLOWS = ["propose", "explore", "apply", "update", "sync", "archive", "verify"]
MANIFEST = Path(".dev/agent-workflow/manifest.json")
MARKER = "<!-- skillhub:engineering-context:v1 -->"
POLICY = ".trellis/spec/workflow/openspec.md"
STAGES = {"openspec-apply-change": "apply", "openspec-verify-change": "verify"}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(root, relative):
    relative = Path(relative)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError(f"Path escapes project: {relative}")
    path = root / relative
    if not path.is_relative_to(root):
        raise ValueError(f"Path escapes project: {relative}")
    for candidate in (path, *path.parents):
        if candidate.is_symlink():
            raise ValueError(f"Refusing symlink: {candidate}")
    return path


def add_project_entry(text, stage):
    if not text.startswith("---\n") or "\n---\n" not in text[4:]:
        raise ValueError("Upstream skill has no supported frontmatter")
    end = text.index("\n---\n", 4) + len("\n---\n")
    entry = (
        f"\n{MARKER}\n## SkillHub engineering context\n\n"
        f"Before performing this **{stage}** workflow, read `{POLICY}` in full "
        f"and follow its **{stage}** procedure. Read the applicable engineering "
        "topic bodies it selects, not only paths or indexes. Keep the upstream "
        "OpenSpec lifecycle and task source below.\n"
    )
    if stage == "verify":
        entry += "Verification is report-only: do not repair product code or archive.\n"
    return text[:end] + entry + "\n" + text[end:]


def generate_skills():
    executable = shutil.which("openspec")
    if executable is None:
        raise ValueError(f"Install @fission-ai/openspec@{OPENSPEC_VERSION} first")
    version = subprocess.run(
        [executable, "--version"], capture_output=True, text=True, check=True, timeout=20
    ).stdout.strip()
    if version != OPENSPEC_VERSION:
        raise ValueError(f"Expected OpenSpec {OPENSPEC_VERSION}, found {version}; review a version update before regenerating")
    with tempfile.TemporaryDirectory(prefix="skillhub-openspec-") as temporary:
        temporary = Path(temporary)
        config = temporary / "config/openspec"
        config.mkdir(parents=True)
        (config / "config.json").write_text(json.dumps({
            "profile": "custom", "delivery": "skills", "workflows": WORKFLOWS,
        }), encoding="utf-8")
        project = temporary / "project"
        project.mkdir()
        env = dict(os.environ, XDG_CONFIG_HOME=str(temporary / "config"),
                   OPENSPEC_TELEMETRY="0", OPENSPEC_NO_UPDATE_CHECK="1")
        result = subprocess.run(
            [executable, "init", "--tools", "codex", "--profile", "custom",
             "--no-animation", str(project)],
            capture_output=True, text=True, env=env, timeout=60,
        )
        if result.returncode:
            raise ValueError("Upstream OpenSpec generation failed; no project files were changed")
        generated = {}
        originals = {}
        for path in sorted((project / ".agents/skills").glob("openspec-*/SKILL.md")):
            relative = path.relative_to(project)
            raw = path.read_bytes()
            originals[str(relative)] = raw
            stage = STAGES.get(path.parent.name)
            generated[str(relative)] = add_project_entry(raw.decode("utf-8"), stage).encode("utf-8") if stage else raw
        if len(generated) != len(WORKFLOWS) or not all(
            f".agents/skills/{name}/SKILL.md" in generated for name in STAGES
        ):
            raise ValueError("Unexpected upstream skill inventory; no project files were changed")
        marker = ".agents/skills/.openspec-target"
        originals[marker] = generated[marker] = b"codex\n"
        return generated, originals


def synchronize(root, generated, originals, mode):
    root = Path(os.path.abspath(root))
    if not safe_path(root, POLICY).is_file():
        raise ValueError(f"Missing shared policy: {POLICY}")
    manifest_path = safe_path(root, MANIFEST)
    previous = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    if not isinstance(previous, dict) or not isinstance(previous.get("files", {}), dict):
        raise ValueError("Invalid integration manifest; preserve it and review before updating")
    known = previous.get("files", {})
    changes = []
    errors = []
    for relative, expected in generated.items():
        path = safe_path(root, relative)
        current = path.read_bytes() if path.exists() else None
        if current == expected:
            continue
        if mode == "check":
            errors.append(f"Missing or changed generated entry: {relative}")
        elif current is not None and digest(current) != known.get(relative) and current != originals[relative]:
            errors.append(f"Local edits conflict: {relative}; preserve and review them before updating")
        else:
            changes.append(relative)
    if mode == "check" and previous.get("version") != OPENSPEC_VERSION:
        errors.append("Missing or outdated project integration manifest")
    if errors:
        raise ValueError("\n".join(errors))
    report = {"mode": mode, "version": OPENSPEC_VERSION,
              "skills": len(generated) - 1, "changes": changes,
              "boundary": "Checks generated entry integrity, not runtime body delivery or compliance"}
    if mode != "apply":
        return report
    backup = None
    existing_changes = [relative for relative in changes if (root / relative).exists()]
    if existing_changes:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        backup = safe_path(root, Path(".dev/agent-workflow/backups") / stamp)
        for relative in existing_changes:
            destination = backup / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(root / relative, destination)
    for relative in changes:
        path = safe_path(root, relative)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(generated[relative])
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps({
        "version": OPENSPEC_VERSION, "workflows": WORKFLOWS,
        "files": {name: digest(data) for name, data in generated.items()},
    }, indent=2) + "\n", encoding="utf-8")
    if backup:
        report["backup"] = str(backup.relative_to(root))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--apply", action="store_true", help="Install or refresh unchanged managed entries")
    group.add_argument("--check", action="store_true", help="Fail on missing or drifted entries; write nothing")
    args = parser.parse_args()
    mode = "apply" if args.apply else "check" if args.check else "preview"
    try:
        generated, originals = generate_skills()
        report = synchronize(args.root, generated, originals, mode)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"Agent workflow setup failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
