"""The machine-readable report the Android app reads.

This file is the entire cloud API: GitHub Actions writes it, the app fetches it over
plain HTTPS. It is published publicly, so it must never contain anything secret — no
password, no cookie, no token, no Playwright storage state.
"""

from __future__ import annotations

import json
import time
from pathlib import Path

from .config import Config, redact
from .models import LoginStatus, RegistrationResult, RegistrationStatus, RunStatus

#: Bumped only on a breaking change. The app refuses a version it does not understand
#: instead of mis-parsing it.
SCHEMA_VERSION = 1


def _iso(epoch_seconds: int) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(epoch_seconds))


def determine_run_status(
    login_status: LoginStatus,
    results: list[RegistrationResult],
) -> RunStatus:
    """Summarise a run in one word.

    A CAPTCHA or second-factor prompt is reported as ``BLOCKED`` rather than ``FAILED``,
    because it means "a human must act", not "something went wrong".
    """
    if login_status in (
        LoginStatus.CAPTCHA_REQUIRED,
        LoginStatus.TWO_FACTOR_REQUIRED,
    ):
        return RunStatus.BLOCKED
    if login_status in (LoginStatus.INVALID_CREDENTIALS, LoginStatus.ERROR):
        return RunStatus.FAILED

    failed = [r for r in results if r.status is RegistrationStatus.FAILED]
    if not failed:
        return RunStatus.SUCCESS
    if len(failed) == len(results):
        return RunStatus.FAILED
    return RunStatus.PARTIAL


def build_status_document(
    config: Config,
    login_status: LoginStatus,
    results: list[RegistrationResult],
    message: str | None = None,
    *,
    run_status: RunStatus | None = None,
    now_epoch_seconds: int | None = None,
) -> dict:
    """Assemble the status document.

    ``run_status`` overrides the summary for failures that produce no registration
    results at all, such as the contest list being unreachable; without it such a run
    would look indistinguishable from a clean one with nothing to do.

    Every free-text field is passed through :func:`redact` so a secret that somehow
    reached an exception message cannot be published.
    """
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    if run_status is None:
        run_status = determine_run_status(login_status, results)

    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": _iso(now),
        "generatedAtEpochSeconds": now,
        "run": {
            "status": run_status.value,
            "message": redact(message) if message else None,
            "workflowRunUrl": config.workflow_run_url,
        },
        "codeforces": {
            # The handle is public. No other account detail is ever published.
            "handle": config.handle or None,
            "loginStatus": login_status.value,
        },
        "registrations": [
            {
                "platform": "CODEFORCES",
                "contestId": str(result.contest.contest_id),
                "contestName": result.contest.name,
                "startTimeSeconds": result.contest.start_time_seconds,
                "division": result.contest.division,
                "status": result.status.value,
                "message": redact(result.message) if result.message else None,
                "updatedAtEpochSeconds": now,
            }
            for result in results
        ],
    }


def write_status_document(document: dict, path: str | Path) -> Path:
    """Write ``document`` as pretty-printed JSON, creating parent directories."""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(document, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return target
