"""Orchestrates one automation run.

Order of business: read the contest list, decide what qualifies, sign in only if there is
something to do, register each contest, and publish a status document whatever happens.
A run that fails still publishes — silence would look identical to "nothing scheduled".
"""

from __future__ import annotations

import logging
import time

from .auth import LoginError, log_in
from .config import Config, redact
from .contests import ContestFetchError, fetch_contests, select_eligible
from .models import (
    Contest,
    LoginStatus,
    RegistrationResult,
    RegistrationStatus,
    RunStatus,
    SkipReason,
)
from .register import register_for_contest
from .status import build_status_document

LOGGER = logging.getLogger(__name__)

#: Skip reasons worth reporting to the app. The others are routine noise — most of the
#: contest list is simply outside the window.
REPORTED_SKIP_REASONS = (
    SkipReason.DIVISION_NOT_SELECTED,
    SkipReason.AUTO_REGISTRATION_DISABLED,
)


def _skip_results(
    skipped: list[tuple[Contest, SkipReason]],
    window_days: int,
    now: int,
) -> list[RegistrationResult]:
    """Report only the skips the user would want an explanation for."""
    window_end = now + window_days * 24 * 60 * 60
    reported: list[RegistrationResult] = []

    for contest, reason in skipped:
        if reason not in REPORTED_SKIP_REASONS:
            continue
        # Still limited to the visible window, so the report matches the app's list.
        start = contest.start_time_seconds
        if start is None or start <= now or start > window_end:
            continue
        reported.append(
            RegistrationResult(contest, RegistrationStatus.SKIPPED, reason.value)
        )

    return reported


def run(
    config: Config,
    browser_factory=None,
    *,
    now_epoch_seconds: int | None = None,
) -> dict:
    """Execute a run and return the status document.

    ``browser_factory`` takes a :class:`Config` and returns a context manager yielding a
    Playwright ``Page``. It is injected so the orchestration can be tested without a real
    browser; production passes :func:`playwright_page_factory`.

    ``now_epoch_seconds`` pins the reference instant, so tests never depend on the clock.
    """
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    login_status = LoginStatus.NOT_ATTEMPTED
    results: list[RegistrationResult] = []
    message: str | None = None
    browser_failed = False

    try:
        contests = fetch_contests()
    except ContestFetchError as exc:
        LOGGER.error("%s", exc)
        return build_status_document(
            config,
            LoginStatus.NOT_ATTEMPTED,
            [],
            message=str(exc),
            run_status=RunStatus.FAILED,
            now_epoch_seconds=now,
        )

    eligible, skipped = select_eligible(
        contests,
        divisions=config.divisions,
        window_days=config.window_days,
        auto_register_enabled=config.auto_register_enabled,
        now_epoch_seconds=now,
    )
    results.extend(_skip_results(skipped, config.window_days, now))

    LOGGER.info(
        "%d contest(s) qualify for auto-registration in the next %d day(s)",
        len(eligible),
        config.window_days,
    )

    if config.check_only:
        results.extend(
            RegistrationResult(
                contest,
                RegistrationStatus.PENDING,
                "Qualifies for auto-registration on the next registration run.",
            )
            for contest in eligible
        )
        return build_status_document(
            config,
            login_status,
            results,
            message=f"Check only: {len(eligible)} contest(s) qualify.",
            now_epoch_seconds=now,
        )

    if not eligible:
        message = (
            "No contest currently qualifies for auto-registration."
            if config.auto_register_enabled
            else "Auto-registration is switched off."
        )
        return build_status_document(
            config, login_status, results, message=message, now_epoch_seconds=now
        )

    if browser_factory is None:
        browser_factory = playwright_page_factory

    try:
        with browser_factory(config) as page:
            try:
                log_in(page, config)
                login_status = LoginStatus.OK
            except LoginError as exc:
                LOGGER.error("Sign-in stopped: %s", exc.message)
                login_status = exc.status
                message = exc.message
                return build_status_document(
                    config, login_status, results, message=message, now_epoch_seconds=now
                )

            for contest in eligible:
                results.append(register_for_contest(page, contest, config))
    except Exception as exc:  # noqa: BLE001 - the browser can fail in many ways
        LOGGER.exception("The browser session failed")
        login_status = (
            login_status if login_status is LoginStatus.OK else LoginStatus.ERROR
        )
        message = "The browser session failed: " + redact(str(exc))
        browser_failed = True

    registered = sum(1 for r in results if r.succeeded)
    if message is None:
        message = f"{registered} of {len(eligible)} eligible contest(s) registered."

    return build_status_document(
        config,
        login_status,
        results,
        message=message,
        run_status=RunStatus.FAILED if browser_failed and not results else None,
        now_epoch_seconds=now,
    )


class playwright_page_factory:  # noqa: N801 - used as a context-manager factory
    """Opens a Chromium page for the duration of a run.

    Written as a class so :func:`run` can accept any callable with the same shape,
    including a fake, without importing Playwright in tests.
    """

    def __init__(self, config: Config) -> None:
        self._config = config
        self._playwright = None
        self._browser = None
        self._context = None

    def __enter__(self):
        from playwright.sync_api import sync_playwright

        self._playwright = sync_playwright().start()
        self._browser = self._playwright.chromium.launch(headless=self._config.headless)
        self._context = self._browser.new_context(
            user_agent=(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
            ),
            locale="en-US",
        )
        self._context.set_default_timeout(self._config.timeout_ms)
        return self._context.new_page()

    def __exit__(self, exc_type, exc, traceback) -> None:
        # The browser context holds the session cookie, so it is torn down here and
        # never written to disk. No storage state is saved.
        for closable in (self._context, self._browser):
            if closable is not None:
                try:
                    closable.close()
                except Exception:  # noqa: BLE001 - teardown must not mask the real error
                    LOGGER.debug("Ignoring error while closing the browser", exc_info=True)
        if self._playwright is not None:
            try:
                self._playwright.stop()
            except Exception:  # noqa: BLE001
                LOGGER.debug("Ignoring error while stopping Playwright", exc_info=True)
