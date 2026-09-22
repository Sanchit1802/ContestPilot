"""Failure diagnostics for the browser automation.

A Playwright failure in GitHub Actions is otherwise a black box: the log shows an
exception, but not what page Codeforces actually served. This module saves a screenshot
and the page's HTML alongside the status document so a real failure can be inspected
after the fact instead of guessed at.

Capture is always best-effort: a diagnostics failure must never mask or replace the
original error it was trying to explain.
"""

from __future__ import annotations

import logging
from pathlib import Path

LOGGER = logging.getLogger(__name__)


def capture(page, directory: str | Path, name: str) -> list[str]:
    """Save a screenshot and the current page HTML under ``directory``.

    Returns the paths actually written. Never raises: every step is wrapped so a browser
    already in a bad state (mid-navigation, closed, crashed) cannot turn a diagnostics
    attempt into a second, more confusing failure.
    """
    out = Path(directory)
    written: list[str] = []

    try:
        out.mkdir(parents=True, exist_ok=True)
    except OSError:
        LOGGER.debug("Could not create diagnostics directory %s", out, exc_info=True)
        return written

    screenshot_path = out / f"{name}.png"
    try:
        page.screenshot(path=str(screenshot_path), full_page=True)
        written.append(str(screenshot_path))
    except Exception:  # noqa: BLE001 - diagnostics must never raise
        LOGGER.debug("Could not capture a screenshot for %s", name, exc_info=True)

    html_path = out / f"{name}.html"
    try:
        html_path.write_text(page.content(), encoding="utf-8")
        written.append(str(html_path))
    except Exception:  # noqa: BLE001 - diagnostics must never raise
        LOGGER.debug("Could not capture page HTML for %s", name, exc_info=True)

    if written:
        LOGGER.info("Saved failure diagnostics: %s", ", ".join(written))

    return written
