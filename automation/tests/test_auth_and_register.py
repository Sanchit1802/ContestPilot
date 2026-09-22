"""Sign-in and registration behaviour, exercised against fake pages.

The point of these tests is the safety rules: a CAPTCHA or a second factor must stop the
run, and a registration must never be reported as successful unless Codeforces confirms
it.
"""

from __future__ import annotations

from dataclasses import replace

import pytest

from contestpilot.auth import LoginError, detect_blockers, is_logged_in, log_in
from contestpilot.models import LoginStatus, RegistrationStatus
from contestpilot.register import classify_registration_page, register_for_contest

from .conftest import make_contest

SIGNED_IN_PAGE = """
<html><body>
  <div class="lang-chooser"><a href="/profile/tester">tester</a></div>
  <a href="/logout">Logout</a>
</body></html>
"""

LOGIN_FORM_PAGE = """
<html><body>
  <form><input id="handleOrEmail"><input id="password">
  <input type="submit" value="Login"></form>
</body></html>
"""


class FakePage:
    """The slice of the Playwright page API these modules actually use."""

    def __init__(self, contents):
        self._contents = list(contents)
        self.visited = []
        self.filled = {}
        self.clicked = []
        self.goto_error = None
        self.fill_error = None

    def screenshot(self, path, full_page=False):
        with open(path, "wb") as handle:
            handle.write(b"fake-png")

    def goto(self, url, **_kwargs):
        self.visited.append(url)
        if self.goto_error is not None:
            raise self.goto_error

    def content(self):
        if len(self._contents) > 1:
            return self._contents.pop(0)
        return self._contents[0]

    def fill(self, selector, value, **_kwargs):
        if self.fill_error is not None:
            raise self.fill_error
        self.filled[selector] = value

    def click(self, selector, **_kwargs):
        self.clicked.append(selector)

    def wait_for_load_state(self, *_args, **_kwargs):
        return None

    def locator(self, _selector):
        return FakeLocator()


class FakeLocator:
    def __init__(self, count=1, visible=True, checked=False):
        self._count = count
        self._visible = visible
        self._checked = checked
        self.checked_calls = 0
        self.click_calls = 0

    @property
    def first(self):
        return self

    def count(self):
        return self._count

    def is_visible(self):
        return self._visible

    def is_checked(self):
        return self._checked

    def check(self, **_kwargs):
        self.checked_calls += 1

    def click(self, **_kwargs):
        self.click_calls += 1


@pytest.mark.parametrize(
    ("content", "expected"),
    [
        ('<div class="g-recaptcha"></div>', LoginStatus.CAPTCHA_REQUIRED),
        ("<p>Please complete the captcha</p>", LoginStatus.CAPTCHA_REQUIRED),
        ("<div class='cf-turnstile'></div>", LoginStatus.CAPTCHA_REQUIRED),
        ("<p>Enter your two-factor code</p>", LoginStatus.TWO_FACTOR_REQUIRED),
        ("<p>Open your authenticator app</p>", LoginStatus.TWO_FACTOR_REQUIRED),
        ("<span>Invalid handle or password</span>", LoginStatus.INVALID_CREDENTIALS),
        (SIGNED_IN_PAGE, None),
    ],
)
def test_blockers_are_detected_from_page_text(content, expected):
    assert detect_blockers(content) == expected


def test_a_signed_in_page_is_recognised():
    assert is_logged_in(SIGNED_IN_PAGE, "tester")
    assert not is_logged_in(SIGNED_IN_PAGE, "someone-else")
    assert not is_logged_in(LOGIN_FORM_PAGE, "tester")


def test_a_successful_sign_in_fills_the_form(config):
    page = FakePage([LOGIN_FORM_PAGE, SIGNED_IN_PAGE])

    log_in(page, config)

    assert page.filled["#handleOrEmail"] == "tester"
    assert page.filled["#password"] == config.password
    assert page.visited == ["https://codeforces.com/enter"]


def test_a_captcha_stops_the_run_without_an_attempt_to_solve_it(config):
    page = FakePage(['<div class="g-recaptcha"></div>'])

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert excinfo.value.status is LoginStatus.CAPTCHA_REQUIRED
    assert "bypass" in excinfo.value.message
    assert page.filled == {}


def test_a_second_factor_prompt_after_submitting_stops_the_run(config):
    page = FakePage([LOGIN_FORM_PAGE, "<p>Enter your verification code</p>"])

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert excinfo.value.status is LoginStatus.TWO_FACTOR_REQUIRED


def test_rejected_credentials_point_at_the_repository_secrets(config):
    page = FakePage([LOGIN_FORM_PAGE, "<span>Invalid handle or password</span>"])

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert excinfo.value.status is LoginStatus.INVALID_CREDENTIALS
    assert "CODEFORCES_PASSWORD" in excinfo.value.message


def test_an_unauthenticated_session_is_not_treated_as_signed_in(config):
    page = FakePage([LOGIN_FORM_PAGE, "<html><body>Nothing useful</body></html>"])

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert excinfo.value.status is LoginStatus.ERROR


def test_the_password_is_never_included_in_a_login_error(config):
    page = FakePage([LOGIN_FORM_PAGE, "<span>Invalid handle or password</span>"])

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert config.password not in excinfo.value.message


@pytest.mark.parametrize(
    ("content", "expected"),
    [
        ("<p>You have already registered for this contest</p>",
         RegistrationStatus.ALREADY_REGISTERED),
        ("<p>Registration is not open</p>", RegistrationStatus.SKIPPED),
        ("<p>Registration is closed</p>", RegistrationStatus.SKIPPED),
        ("<form><input type='submit' value='Register'></form>", None),
    ],
)
def test_registration_pages_are_classified(content, expected):
    assert classify_registration_page(content) == expected


def test_an_already_registered_contest_is_not_submitted_again(config):
    page = FakePage(["<p>You have already registered for this contest</p>"])

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.ALREADY_REGISTERED
    assert page.clicked == []


def test_a_closed_registration_becomes_pending_for_a_later_run(config):
    page = FakePage(["<p>Registration is not open</p>"])

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.PENDING
    assert "try again" in (result.message or "")


def test_a_dry_run_reaches_the_form_but_never_submits(config):
    page = FakePage(["<form><input type='submit' value='Register'></form>"])

    result = register_for_contest(page, make_contest(), replace(config, dry_run=True))

    assert result.status is RegistrationStatus.PENDING
    assert "not submitted" in (result.message or "")


def test_a_registration_is_only_reported_as_done_once_codeforces_confirms_it(config):
    page = FakePage(
        [
            "<form><input type='submit' value='Register'></form>",
            "<p>You have already registered for this contest</p>",
        ]
    )

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.REGISTERED


def test_an_unconfirmed_registration_is_reported_as_failed(config):
    page = FakePage(
        [
            "<form><input type='submit' value='Register'></form>",
            "<p>Still showing the empty registration form</p>",
        ]
    )

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.FAILED
    assert "check manually" in (result.message or "")


def test_a_captcha_on_the_registration_page_fails_safely(config):
    page = FakePage(['<div class="g-recaptcha"></div>'])

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.FAILED
    assert "bypass" in (result.message or "")


def test_a_navigation_failure_does_not_raise(config):
    page = FakePage(["<html></html>"])
    page.goto_error = RuntimeError("net::ERR_CONNECTION_RESET")

    result = register_for_contest(page, make_contest(), config)

    assert result.status is RegistrationStatus.FAILED


def test_a_login_form_that_never_appears_is_reported_as_an_error(config):
    """The failure seen in CI: the page loaded, matched no known blocker text, and the
    login field never materialised."""
    page = FakePage(["<html><body>something unexpected</body></html>"])
    page.fill_error = RuntimeError(
        'Page.fill: Timeout 30000ms exceeded. '
        'Call log: waiting for locator("#handleOrEmail")'
    )

    with pytest.raises(LoginError) as excinfo:
        log_in(page, config)

    assert excinfo.value.status is LoginStatus.ERROR
    assert "did not behave as expected" in excinfo.value.message


def test_that_failure_captures_what_codeforces_actually_served(config, tmp_path):
    page = FakePage(["<html><body>the page codeforces actually served</body></html>"])
    page.fill_error = RuntimeError('Page.fill: Timeout 30000ms exceeded.')

    with pytest.raises(LoginError) as excinfo:
        log_in(page, replace(config, debug_dir=str(tmp_path)))

    saved = tmp_path / "login-form-error.html"
    assert saved.exists()
    assert "the page codeforces actually served" in saved.read_text(encoding="utf-8")
    assert "Diagnostics saved" in excinfo.value.message


def test_a_captcha_block_is_also_captured_for_inspection(config, tmp_path):
    page = FakePage(['<div class="g-recaptcha"></div>'])

    with pytest.raises(LoginError):
        log_in(page, replace(config, debug_dir=str(tmp_path)))

    assert (tmp_path / "login-blocked-before-fill.html").exists()


def test_no_diagnostics_are_written_when_no_directory_is_configured(config, tmp_path):
    page = FakePage(["<html><body>unexpected</body></html>"])
    page.fill_error = RuntimeError("Page.fill: Timeout")

    with pytest.raises(LoginError):
        log_in(page, replace(config, debug_dir=None))

    assert list(tmp_path.iterdir()) == []


def test_captured_diagnostics_never_contain_the_password(config, tmp_path, monkeypatch):
    monkeypatch.setenv("CODEFORCES_PASSWORD", config.password)
    page = FakePage(["<html><body>unexpected</body></html>"])
    page.fill_error = RuntimeError(f"Page.fill failed with {config.password}")

    with pytest.raises(LoginError) as excinfo:
        log_in(page, replace(config, debug_dir=str(tmp_path)))

    assert config.password not in excinfo.value.message
