"""Codeforces sign-in via Playwright.

Codeforces publishes no authentication API for registration, so the only honest way to
register is a real browser session that behaves like a person using the site.

Anti-automation protections are detected and reported, never circumvented. If Codeforces
presents a CAPTCHA or asks for a second factor, this module stops and says so; the user
then registers manually. That is a deliberate limit, not a gap.
"""

from __future__ import annotations

import logging

from .config import Config, redact
from .models import LoginStatus

LOGGER = logging.getLogger(__name__)

LOGIN_URL = "https://codeforces.com/enter"

#: Markers that mean a human has to finish the job. Matched case-insensitively against
#: the page's text and DOM.
CAPTCHA_MARKERS = (
    "recaptcha",
    "g-recaptcha",
    "hcaptcha",
    "cf-turnstile",
    "i'm not a robot",
    "captcha",
)

TWO_FACTOR_MARKERS = (
    "two-factor",
    "two factor",
    "2fa",
    "one-time code",
    "verification code",
    "authenticator",
)

INVALID_CREDENTIAL_MARKERS = (
    "invalid handle or password",
    "invalid handle/email or password",
    "incorrect password",
)


class LoginError(RuntimeError):
    """Sign-in did not complete. ``status`` says why."""

    def __init__(self, status: LoginStatus, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.message = message


def _contains_any(haystack: str, needles: tuple[str, ...]) -> str | None:
    lowered = haystack.lower()
    for needle in needles:
        if needle in lowered:
            return needle
    return None


def detect_blockers(page_content: str) -> LoginStatus | None:
    """Return the blocking status a page implies, or ``None`` if it looks clear.

    Split out from the browser work so the detection rules can be tested offline.
    """
    if _contains_any(page_content, CAPTCHA_MARKERS):
        return LoginStatus.CAPTCHA_REQUIRED
    if _contains_any(page_content, TWO_FACTOR_MARKERS):
        return LoginStatus.TWO_FACTOR_REQUIRED
    if _contains_any(page_content, INVALID_CREDENTIAL_MARKERS):
        return LoginStatus.INVALID_CREDENTIALS
    return None


def is_logged_in(page_content: str, handle: str) -> bool:
    """A signed-in Codeforces page carries a logout link and the user's handle."""
    lowered = page_content.lower()
    return "/logout" in lowered and handle.lower() in lowered


def log_in(page, config: Config) -> None:
    """Sign ``page`` in as the configured user.

    Raises :class:`LoginError` with a specific status when Codeforces refuses or asks for
    something only a human can supply. The password is typed into the form and never
    logged, stored or included in any message.
    """
    LOGGER.info("Signing in to Codeforces as %s", config.handle)
    page.goto(LOGIN_URL, wait_until="domcontentloaded", timeout=config.timeout_ms)

    blocker = detect_blockers(page.content())
    if blocker is not None:
        raise LoginError(
            blocker,
            "Codeforces presented an additional verification step on the sign-in page. "
            "ContestPilot does not attempt to bypass it - please register manually.",
        )

    try:
        page.fill("#handleOrEmail", config.handle, timeout=config.timeout_ms)
        page.fill("#password", config.password, timeout=config.timeout_ms)
        page.click("input[type='submit'][value='Login']", timeout=config.timeout_ms)
        page.wait_for_load_state("domcontentloaded", timeout=config.timeout_ms)
    except Exception as exc:  # noqa: BLE001 - Playwright raises several unrelated types
        raise LoginError(
            LoginStatus.ERROR,
            "The Codeforces sign-in form did not behave as expected: "
            + redact(str(exc)),
        ) from exc

    content = page.content()

    blocker = detect_blockers(content)
    if blocker is not None:
        raise LoginError(
            blocker,
            {
                LoginStatus.CAPTCHA_REQUIRED: (
                    "Codeforces asked for a CAPTCHA. ContestPilot will not try to solve "
                    "it - please register manually for now."
                ),
                LoginStatus.TWO_FACTOR_REQUIRED: (
                    "Codeforces asked for a second factor. ContestPilot cannot supply "
                    "it - please register manually for now."
                ),
                LoginStatus.INVALID_CREDENTIALS: (
                    "Codeforces rejected the handle or password. Check the "
                    "CODEFORCES_HANDLE and CODEFORCES_PASSWORD repository secrets."
                ),
            }[blocker],
        )

    if not is_logged_in(content, config.handle):
        raise LoginError(
            LoginStatus.ERROR,
            "Sign-in finished but the session does not look authenticated.",
        )

    LOGGER.info("Signed in successfully")
