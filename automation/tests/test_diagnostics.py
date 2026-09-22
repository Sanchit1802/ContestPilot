"""Diagnostics capture must always help and never hurt.

The whole point of this module is to explain a failure, so it can never become a second
failure of its own.
"""

from __future__ import annotations

from contestpilot import diagnostics


class FakePage:
    def __init__(self, html="<html>hello</html>", screenshot_error=None, content_error=None):
        self._html = html
        self._screenshot_error = screenshot_error
        self._content_error = content_error
        self.screenshot_path = None

    def screenshot(self, path, full_page=False):
        if self._screenshot_error is not None:
            raise self._screenshot_error
        self.screenshot_path = path
        with open(path, "wb") as handle:
            handle.write(b"\x89PNG fake")

    def content(self):
        if self._content_error is not None:
            raise self._content_error
        return self._html


def test_a_screenshot_and_the_page_html_are_written(tmp_path):
    page = FakePage(html="<html>the page codeforces actually served</html>")

    written = diagnostics.capture(page, tmp_path, "login-form-error")

    assert (tmp_path / "login-form-error.png").exists()
    assert (tmp_path / "login-form-error.html").exists()
    assert len(written) == 2
    assert "the page codeforces actually served" in (
        tmp_path / "login-form-error.html"
    ).read_text(encoding="utf-8")


def test_the_directory_is_created_when_missing(tmp_path):
    target = tmp_path / "nested" / "debug"

    diagnostics.capture(FakePage(), target, "login-form-error")

    assert (target / "login-form-error.html").exists()


def test_a_failed_screenshot_still_leaves_the_html(tmp_path):
    page = FakePage(screenshot_error=RuntimeError("browser already closed"))

    written = diagnostics.capture(page, tmp_path, "login-form-error")

    assert not (tmp_path / "login-form-error.png").exists()
    assert (tmp_path / "login-form-error.html").exists()
    assert len(written) == 1


def test_capture_never_raises_even_when_everything_fails(tmp_path):
    page = FakePage(
        screenshot_error=RuntimeError("target crashed"),
        content_error=RuntimeError("target crashed"),
    )

    # No exception, and an honest empty result rather than a bogus path.
    assert diagnostics.capture(page, tmp_path, "login-form-error") == []


def test_a_page_missing_the_playwright_api_is_tolerated(tmp_path):
    class NotAPage:
        pass

    assert diagnostics.capture(NotAPage(), tmp_path, "login-form-error") == []


def test_each_failure_gets_its_own_file(tmp_path):
    page = FakePage()

    diagnostics.capture(page, tmp_path, "register-1234-submit-failed")
    diagnostics.capture(page, tmp_path, "register-5678-unconfirmed")

    assert (tmp_path / "register-1234-submit-failed.html").exists()
    assert (tmp_path / "register-5678-unconfirmed.html").exists()
