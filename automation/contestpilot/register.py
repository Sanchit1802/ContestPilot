"""Registering for a single Codeforces contest with Playwright.

Codeforces exposes no registration API, so this drives the same
``/contestRegistration/<id>`` page a person would use. Every outcome — including
"already registered" and "registration is not open" — is reported rather than guessed.
"""

from __future__ import annotations

import logging

from . import diagnostics
from .auth import detect_blockers
from .config import Config, redact
from .models import Contest, RegistrationResult, RegistrationStatus

LOGGER = logging.getLogger(__name__)

#: Text Codeforces shows when the account is already on the participant list.
ALREADY_REGISTERED_MARKERS = (
    "you have already registered",
    "you are already registered",
)

#: Text meaning the round cannot be registered for right now.
NOT_OPEN_MARKERS = (
    "registration is not open",
    "registration has not started",
    "registration is closed",
    "registration for this contest is over",
)

#: The terms checkbox and submit control on the registration form.
TERMS_CHECKBOX_SELECTOR = "input[name='takePartAs'], input#terms, input[type='checkbox']"
SUBMIT_SELECTOR = "input[type='submit'][value*='Register'], input.submit[type='submit']"


def _capture_if_configured(page, config: Config, contest: Contest, name: str) -> None:
    """Named per contest so one failing round never overwrites another's evidence."""
    if config.debug_dir:
        diagnostics.capture(page, config.debug_dir, f"register-{contest.contest_id}-{name}")


def classify_registration_page(content: str) -> RegistrationStatus | None:
    """Read a registration page and say what it means, if anything definitive.

    Returns ``None`` when the page looks like a normal, actionable registration form.
    Kept pure so the wording rules can be tested without a browser.
    """
    lowered = content.lower()

    for marker in ALREADY_REGISTERED_MARKERS:
        if marker in lowered:
            return RegistrationStatus.ALREADY_REGISTERED
    for marker in NOT_OPEN_MARKERS:
        if marker in lowered:
            return RegistrationStatus.SKIPPED
    return None


def register_for_contest(page, contest: Contest, config: Config) -> RegistrationResult:
    """Attempt to register for ``contest``.

    Never raises: a failure for one contest must not abandon the rest of the run.
    """
    LOGGER.info("Opening registration page for contest %s", contest.contest_id)

    try:
        page.goto(
            contest.registration_url,
            wait_until="domcontentloaded",
            timeout=config.timeout_ms,
        )
    except Exception as exc:  # noqa: BLE001 - Playwright raises several unrelated types
        _capture_if_configured(page, config, contest, "navigation-failed")
        return RegistrationResult(
            contest,
            RegistrationStatus.FAILED,
            "Could not open the registration page: " + redact(str(exc)),
        )

    content = page.content()

    blocker = detect_blockers(content)
    if blocker is not None:
        _capture_if_configured(page, config, contest, "blocked")
        return RegistrationResult(
            contest,
            RegistrationStatus.FAILED,
            "Codeforces asked for additional verification on the registration page. "
            "ContestPilot does not attempt to bypass it - please register manually.",
        )

    predetermined = classify_registration_page(content)
    if predetermined is RegistrationStatus.ALREADY_REGISTERED:
        LOGGER.info("Contest %s: already registered", contest.contest_id)
        return RegistrationResult(
            contest,
            RegistrationStatus.ALREADY_REGISTERED,
            "The account was already registered before this run.",
        )
    if predetermined is RegistrationStatus.SKIPPED:
        LOGGER.info("Contest %s: registration is not open", contest.contest_id)
        return RegistrationResult(
            contest,
            RegistrationStatus.PENDING,
            "Registration is not open yet. ContestPilot will try again on the next run.",
        )

    if config.dry_run:
        return RegistrationResult(
            contest,
            RegistrationStatus.PENDING,
            "Dry run: the registration form was reached but not submitted.",
        )

    try:
        checkbox = page.locator(TERMS_CHECKBOX_SELECTOR).first
        if checkbox.count() > 0 and checkbox.is_visible():
            if not checkbox.is_checked():
                checkbox.check(timeout=config.timeout_ms)

        submit = page.locator(SUBMIT_SELECTOR).first
        if submit.count() == 0:
            _capture_if_configured(page, config, contest, "no-submit-control")
            return RegistrationResult(
                contest,
                RegistrationStatus.FAILED,
                "The registration page had no submit control. Codeforces may have "
                "changed the page, or the contest may not be open to this account.",
            )

        submit.click(timeout=config.timeout_ms)
        page.wait_for_load_state("domcontentloaded", timeout=config.timeout_ms)
    except Exception as exc:  # noqa: BLE001 - Playwright raises several unrelated types
        _capture_if_configured(page, config, contest, "submit-failed")
        return RegistrationResult(
            contest,
            RegistrationStatus.FAILED,
            "Submitting the registration form failed: " + redact(str(exc)),
        )

    return _verify_registration(page, contest, config)


def _verify_registration(page, contest: Contest, config: Config) -> RegistrationResult:
    """Confirm the registration actually took, rather than assuming the click worked."""
    try:
        page.goto(
            contest.registration_url,
            wait_until="domcontentloaded",
            timeout=config.timeout_ms,
        )
        content = page.content()
    except Exception as exc:  # noqa: BLE001 - Playwright raises several unrelated types
        _capture_if_configured(page, config, contest, "verification-failed")
        return RegistrationResult(
            contest,
            RegistrationStatus.FAILED,
            "Registration was submitted but could not be confirmed: " + redact(str(exc)),
        )

    if classify_registration_page(content) is RegistrationStatus.ALREADY_REGISTERED:
        LOGGER.info("Contest %s: registered", contest.contest_id)
        return RegistrationResult(
            contest,
            RegistrationStatus.REGISTERED,
            "Registered by the cloud automation.",
        )

    _capture_if_configured(page, config, contest, "unconfirmed")
    return RegistrationResult(
        contest,
        RegistrationStatus.FAILED,
        "The registration form was submitted but Codeforces does not show the account "
        "as registered. Please check manually.",
    )
