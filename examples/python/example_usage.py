"""Runnable examples for the SkillHub Python client.

Configure the target registry via environment variables:

    export SKILLHUB_URL=https://skill.example.com
    export SKILLHUB_TOKEN=<your-api-token>   # only needed for write operations

Then run:

    python example_usage.py                       # search + inspect + download
    python example_usage.py publish ./my-skill.zip my-namespace
"""

from __future__ import annotations

import os
import sys

from skillhub_client import SkillHubClient, SkillHubError


def _pick(obj, *keys, default=None):
    """Best-effort field access across slightly different response shapes."""
    for key in keys:
        if isinstance(obj, dict) and obj.get(key) is not None:
            return obj[key]
    return default


def demo_read(client: SkillHubClient) -> None:
    print(f"Searching {client.base_url} for skills matching 'email'...\n")
    result = client.search(keyword="email", size=5)

    # The search payload may expose the list under 'items' or 'results'.
    items = _pick(result, "items", "results", default=result if isinstance(result, list) else [])
    if not items:
        print("No skills found. Try a different keyword or registry.")
        return

    for skill in items:
        name = _pick(skill, "name", "slug", default="(unnamed)")
        ns = _pick(skill, "namespace", default="")
        version = _pick(skill, "version", "latestVersion", default="?")
        downloads = _pick(skill, "downloadCount", "downloads", default=0)
        coord = f"{ns}/{name}" if ns else name
        print(f"  - {coord}  v{version}  ({downloads} downloads)")

    # Download the first result's latest package.
    first = items[0]
    ns = _pick(first, "namespace", default="")
    slug = _pick(first, "slug", "name")
    if ns and slug:
        print(f"\nResolving latest version of {ns}/{slug}...")
        resolved = client.resolve(ns, slug)
        version = _pick(resolved, "version", default=None)
        print(f"  resolved version: {version}")

        dest = client.download(ns, slug, version=version)
        size = os.path.getsize(dest)
        print(f"  downloaded -> {dest} ({size} bytes)")


def demo_publish(client: SkillHubClient, zip_path: str, namespace: str) -> None:
    if not client.token:
        sys.exit("Publishing requires SKILLHUB_TOKEN to be set.")
    print(f"Publishing {zip_path} to namespace '{namespace}'...")
    result = client.publish(zip_path, namespace)
    print(f"  published: {result}")


def main() -> None:
    try:
        client = SkillHubClient()
    except ValueError as exc:
        sys.exit(str(exc))

    args = sys.argv[1:]
    try:
        if args and args[0] == "publish":
            if len(args) != 3:
                sys.exit("usage: python example_usage.py publish <zip_path> <namespace>")
            demo_publish(client, args[1], args[2])
        else:
            demo_read(client)
    except SkillHubError as exc:
        sys.exit(f"API error: {exc}")


if __name__ == "__main__":
    main()
