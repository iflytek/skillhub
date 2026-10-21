#!/usr/bin/env python3
"""Read GitHub PR file metadata and summarize possible Flyway version collisions."""

import html
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request


MIGRATION = re.compile(
    r"server/skillhub-app/src/main/resources/db/migration/V([0-9]+)__[^/]+\.sql\Z"
)


class ScanError(Exception):
    pass


def version(path):
    match = MIGRATION.fullmatch(path)
    return int(match[1]) if match else None


def introduced_version(file):
    number = version(file["filename"])
    if file["status"] == "added":
        return number
    if file["status"] == "renamed" and version(file.get("previous_filename", "")) != number:
        return number
    return None


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise ScanError("Invalid repository name")
        self.base = f"https://api.github.com/repos/{repository}"
        self.token = token

    def get(self, path):
        request = urllib.request.Request(self.base + path, headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        })
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise ScanError(f"GitHub API HTTP {error.code}; scan incomplete") from error
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
            # Do not echo server payloads, tokens, request headers or arbitrary URLs.
            raise ScanError(f"GitHub API {type(error).__name__}; scan incomplete") from error

    def pages(self, path, limit=None):
        result = []
        for page in range(1, 1001):
            separator = "&" if "?" in path else "?"
            batch = self.get(f"{path}{separator}per_page=100&page={page}")
            if not isinstance(batch, list):
                raise ScanError("Unexpected GitHub pagination response")
            result.extend(batch)
            if len(batch) < 100:
                return result
            if limit is not None and len(result) >= limit:
                raise ScanError("PR files reached GitHub's 3000-file limit")
        raise ScanError("Pagination safety limit reached")


def scan(api):
    versions = {}
    errors = []
    prs = api.pages("/pulls?state=open&sort=created&direction=asc")
    for pr in prs:
        number = pr["number"]
        try:
            files = api.pages(f"/pulls/{number}/files", limit=3000)
            current = api.get(f"/pulls/{number}")
            if (current["head"]["sha"] != pr["head"]["sha"]
                    or current["base"]["sha"] != pr["base"]["sha"]
                    or current["state"] != "open"):
                raise ScanError("PR changed during scan; rerun required")
            if len(files) != current["changed_files"]:
                raise ScanError("PR file list incomplete; rerun required")
            for file in files:
                value = introduced_version(file)
                if value is not None:
                    versions.setdefault(value, {}).setdefault(number, []).append(file["filename"])
        except ScanError as error:
            errors.append(f"PR #{number}: {error}")
    return {v: entries for v, entries in sorted(versions.items()) if len(entries) > 1}, errors, len(prs)


def safe(value):
    # HTML text in a pre block avoids Markdown/link/table injection from filenames.
    return html.escape(str(value)).replace("\r", "&#13;").replace("\n", "&#10;")


def summary(collisions, errors, count):
    lines = ["## Flyway migration preflight", "",
             "Advisory only: no SQL files are fetched or executed; no PR is changed.", "",
             f"Open PRs discovered: {count}.", ""]
    if errors:
        lines += ["**Warning: scan incomplete. Absence of a reported collision is not clearance.**", "", "<pre>",
                  *(safe(error) for error in errors), "</pre>", ""]
    if collisions:
        lines += ["**Warning: migration versions introduced by multiple open PRs:**", "", "<pre>"]
        for value, entries in collisions.items():
            lines.append(f"V{value}")
            for number, paths in sorted(entries.items()):
                lines.extend(f"  PR #{number}: {safe(path)}" for path in sorted(set(paths)))
        lines += ["</pre>", "", "Authors should coordinate numbering and rerun migration tests before merge."]
    elif not errors:
        lines.append("No cross-PR version collisions found in this snapshot.")
    lines += ["", "Scope: added migrations and renames introducing a different version in the application migration directory. "
              "Same-version renames, modified/deleted files and conflicts with already merged migrations are not checked. "
              "Concurrent PR changes can make this advisory snapshot stale.", ""]
    return "\n".join(lines)


def main():
    try:
        api = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        collisions, errors, count = scan(api)
    except ScanError as error:
        collisions, errors, count = {}, [str(error)], 0
    except (KeyError, TypeError):
        collisions, errors, count = {}, ["Malformed GitHub API response; no clearance issued."], 0
    report = summary(collisions, errors, count)
    destination = os.environ.get("GITHUB_STEP_SUMMARY")
    if destination:
        with Path(destination).open("a", encoding="utf-8") as output:
            output.write(report)
    else:
        print(report)
    return 1 if errors else 0  # Workflow treats errors as advisory, never as scan clearance.


if __name__ == "__main__":
    raise SystemExit(main())
