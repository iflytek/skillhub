"""A minimal Python client for the SkillHub REST API.

This is a dependency-light reference client (only ``requests``) that mirrors
the operations the ClawHub CLI performs: search, inspect, resolve, download
and publish skills. It is meant as a copy-pasteable starting point for Python
integrations, not (yet) an officially published package.

API reference: https://iflytek.github.io/skillhub/ (Developer Docs -> API)

Endpoints used (see docs/04-developer/api):
    Public (no auth):
        GET  /api/v1/skills?keyword=&namespace=&page=&size=
        GET  /api/v1/skills/{namespace}/{slug}
        GET  /api/v1/skills/{namespace}/{slug}/versions
        GET  /api/v1/skills/{namespace}/{slug}/resolve?version=&tag=
        GET  /api/v1/skills/{namespace}/{slug}/download
        GET  /api/v1/skills/{namespace}/{slug}/versions/{version}/download
    Authenticated (Bearer token):
        GET  /api/v1/whoami
        POST /api/v1/publish            (multipart: file, namespace)
        POST /api/v1/skills/{namespace}/{slug}/star
        POST /api/v1/skills/{namespace}/{slug}/rating   (json: {"score": 1-5})
"""

from __future__ import annotations

import os
from typing import Any, Dict, Optional

import requests


class SkillHubError(RuntimeError):
    """Raised when the API returns a non-zero business code."""

    def __init__(self, code: Any, message: str, request_id: Optional[str] = None):
        self.code = code
        self.request_id = request_id
        super().__init__(f"SkillHub API error {code}: {message}"
                         + (f" (requestId={request_id})" if request_id else ""))


class SkillHubClient:
    """Thin wrapper over the SkillHub REST API.

    Args:
        base_url: Registry base URL, e.g. ``https://skill.example.com``.
        token: Optional API token for authenticated calls (Bearer).
        timeout: Per-request timeout in seconds.
        session: Optional pre-configured ``requests.Session``.
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        token: Optional[str] = None,
        timeout: int = 30,
        session: Optional[requests.Session] = None,
    ):
        base_url = base_url or os.environ.get("SKILLHUB_URL")
        if not base_url:
            raise ValueError(
                "base_url is required (pass it explicitly or set SKILLHUB_URL)"
            )
        self.base_url = base_url.rstrip("/")
        self.token = token or os.environ.get("SKILLHUB_TOKEN")
        self.timeout = timeout
        self.session = session or requests.Session()

    # -- internals -------------------------------------------------------

    def _headers(self, extra: Optional[Dict[str, str]] = None) -> Dict[str, str]:
        headers: Dict[str, str] = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if extra:
            headers.update(extra)
        return headers

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def _unwrap(self, resp: requests.Response) -> Any:
        """Return the payload, unwrapping the ``{code,msg,data}`` envelope.

        Native ``/api/v1`` endpoints wrap responses in a unified envelope,
        while the CLI-compat endpoints return the object directly. This
        handles both.
        """
        resp.raise_for_status()
        payload = resp.json()
        if isinstance(payload, dict) and "code" in payload and "data" in payload:
            if payload.get("code") not in (0, None):
                raise SkillHubError(
                    payload.get("code"), payload.get("msg", ""), payload.get("requestId")
                )
            return payload["data"]
        return payload

    # -- public API ------------------------------------------------------

    def search(
        self,
        keyword: Optional[str] = None,
        namespace: Optional[str] = None,
        page: int = 1,
        size: int = 20,
    ) -> Any:
        """Search public skills."""
        params = {"keyword": keyword, "namespace": namespace, "page": page, "size": size}
        params = {k: v for k, v in params.items() if v is not None}
        return self._unwrap(
            self.session.get(
                self._url("/api/v1/skills"),
                params=params,
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def get_skill(self, namespace: str, slug: str) -> Any:
        """Fetch a single skill's detail."""
        return self._unwrap(
            self.session.get(
                self._url(f"/api/v1/skills/{namespace}/{slug}"),
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def list_versions(self, namespace: str, slug: str) -> Any:
        """List all versions of a skill."""
        return self._unwrap(
            self.session.get(
                self._url(f"/api/v1/skills/{namespace}/{slug}/versions"),
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def resolve(
        self,
        namespace: str,
        slug: str,
        version: Optional[str] = None,
        tag: Optional[str] = None,
    ) -> Any:
        """Resolve a version constraint / tag to a concrete version."""
        params = {"version": version, "tag": tag}
        params = {k: v for k, v in params.items() if v is not None}
        return self._unwrap(
            self.session.get(
                self._url(f"/api/v1/skills/{namespace}/{slug}/resolve"),
                params=params,
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def download(
        self,
        namespace: str,
        slug: str,
        version: Optional[str] = None,
        dest: Optional[str] = None,
    ) -> str:
        """Download a skill package (zip). Returns the written file path.

        If ``version`` is omitted the ``latest`` package is downloaded. If
        ``dest`` is omitted a file named ``{slug}-{version}.zip`` (or
        ``{slug}.zip``) is written to the current directory.
        """
        if version:
            path = f"/api/v1/skills/{namespace}/{slug}/versions/{version}/download"
        else:
            path = f"/api/v1/skills/{namespace}/{slug}/download"
        if dest is None:
            dest = f"{slug}-{version}.zip" if version else f"{slug}.zip"
        with self.session.get(
            self._url(path), headers=self._headers(), timeout=self.timeout, stream=True
        ) as resp:
            resp.raise_for_status()
            with open(dest, "wb") as fh:
                for chunk in resp.iter_content(chunk_size=8192):
                    if chunk:
                        fh.write(chunk)
        return dest

    # -- authenticated API ----------------------------------------------

    def whoami(self) -> Any:
        """Return the authenticated principal (requires a token)."""
        return self._unwrap(
            self.session.get(
                self._url("/api/v1/whoami"),
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def publish(
        self, zip_path: str, namespace: str, request_id: Optional[str] = None
    ) -> Any:
        """Publish a skill package (zip) to a namespace. Requires a token.

        Pass ``request_id`` (a UUID) to make the publish idempotent via the
        ``X-Request-Id`` header.
        """
        extra = {"X-Request-Id": request_id} if request_id else None
        with open(zip_path, "rb") as fh:
            files = {"file": (os.path.basename(zip_path), fh, "application/zip")}
            data = {"namespace": namespace}
            return self._unwrap(
                self.session.post(
                    self._url("/api/v1/publish"),
                    files=files,
                    data=data,
                    headers=self._headers(extra),
                    timeout=self.timeout,
                )
            )

    def star(self, namespace: str, slug: str) -> Any:
        """Star a skill. Requires a token."""
        return self._unwrap(
            self.session.post(
                self._url(f"/api/v1/skills/{namespace}/{slug}/star"),
                headers=self._headers(),
                timeout=self.timeout,
            )
        )

    def rate(self, namespace: str, slug: str, score: int) -> Any:
        """Rate a skill from 1 to 5. Requires a token."""
        if not 1 <= score <= 5:
            raise ValueError("score must be between 1 and 5")
        return self._unwrap(
            self.session.post(
                self._url(f"/api/v1/skills/{namespace}/{slug}/rating"),
                json={"score": score},
                headers=self._headers(),
                timeout=self.timeout,
            )
        )
