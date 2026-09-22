"""Reading and filtering upcoming Codeforces contests.

Uses only the documented public method ``contest.list``
(https://codeforces.com/apiHelp/methods). It needs no authentication.
"""

from __future__ import annotations

import logging
import re
import time

import requests

from .models import Contest, SkipReason

LOGGER = logging.getLogger(__name__)

CONTEST_LIST_URL = "https://codeforces.com/api/contest.list"

REQUEST_TIMEOUT_SECONDS = 20

_COMBINED_DIVISION = re.compile(
    r"\bdiv\.?\s*1\s*(?:\+|&|and|/)\s*div\.?\s*2\b", re.IGNORECASE
)

_DIVISION_PATTERNS = (
    ("DIV_1", re.compile(r"\bdiv\.?\s*1\b", re.IGNORECASE)),
    ("DIV_2", re.compile(r"\bdiv\.?\s*2\b", re.IGNORECASE)),
    ("DIV_3", re.compile(r"\bdiv\.?\s*3\b", re.IGNORECASE)),
    ("DIV_4", re.compile(r"\bdiv\.?\s*4\b", re.IGNORECASE)),
)


class ContestFetchError(RuntimeError):
    """The contest list could not be read."""


def classify_division(name: str) -> str:
    """Derive a division from a contest name.

    Matches the Android app's ``ContestClassifier`` so both sides agree on which contests
    a division rule selects. A division says nothing about whether a round is rated.
    """
    if _COMBINED_DIVISION.search(name):
        return "DIV_1_2"
    for division, pattern in _DIVISION_PATTERNS:
        if pattern.search(name):
            return division
    return "OTHER"


def _to_contest(raw: dict) -> Contest | None:
    contest_id = raw.get("id")
    name = raw.get("name")
    if not isinstance(contest_id, int) or not isinstance(name, str) or not name.strip():
        return None

    start = raw.get("startTimeSeconds")
    return Contest(
        contest_id=contest_id,
        name=name,
        phase=str(raw.get("phase") or "UNKNOWN"),
        start_time_seconds=start if isinstance(start, int) else None,
        duration_seconds=int(raw.get("durationSeconds") or 0),
        division=classify_division(name),
    )


def fetch_contests(session: requests.Session | None = None) -> list[Contest]:
    """Return every non-gym contest Codeforces currently lists."""
    http = session or requests.Session()
    try:
        response = http.get(
            CONTEST_LIST_URL,
            params={"gym": "false"},
            timeout=REQUEST_TIMEOUT_SECONDS,
            headers={"User-Agent": "ContestPilot-Automation/1.0"},
        )
        response.raise_for_status()
        payload = response.json()
    except requests.RequestException as exc:
        raise ContestFetchError(f"Could not reach the Codeforces API: {exc}") from exc
    except ValueError as exc:
        raise ContestFetchError("The Codeforces API returned a response that is not JSON") from exc

    if payload.get("status") != "OK":
        comment = payload.get("comment") or "unknown error"
        raise ContestFetchError(f"Codeforces API reported a failure: {comment}")

    result = payload.get("result")
    if not isinstance(result, list):
        raise ContestFetchError("The Codeforces API response had no contest list")

    contests = [c for c in (_to_contest(item) for item in result) if c is not None]
    LOGGER.info("Codeforces returned %d contests", len(contests))
    return contests


def select_eligible(
    contests: list[Contest],
    divisions: tuple[str, ...],
    window_days: int,
    *,
    auto_register_enabled: bool = True,
    now_epoch_seconds: int | None = None,
) -> tuple[list[Contest], list[tuple[Contest, SkipReason]]]:
    """Split ``contests`` into those to register for and those to skip, with a reason.

    A contest qualifies when it has not started, begins within ``window_days`` (the exact
    boundary counts as inside), and its division is one the user selected.
    """
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    window_end = now + window_days * 24 * 60 * 60

    eligible: list[Contest] = []
    skipped: list[tuple[Contest, SkipReason]] = []

    for contest in contests:
        if not auto_register_enabled:
            skipped.append((contest, SkipReason.AUTO_REGISTRATION_DISABLED))
            continue
        if contest.start_time_seconds is None:
            skipped.append((contest, SkipReason.NO_START_TIME))
            continue
        if contest.phase != "BEFORE" or contest.start_time_seconds <= now:
            skipped.append((contest, SkipReason.NOT_UPCOMING))
            continue
        if contest.start_time_seconds > window_end:
            skipped.append((contest, SkipReason.OUTSIDE_WINDOW))
            continue
        if contest.division not in divisions:
            skipped.append((contest, SkipReason.DIVISION_NOT_SELECTED))
            continue
        eligible.append(contest)

    eligible.sort(key=lambda c: (c.start_time_seconds or 0, c.contest_id))
    return eligible, skipped
