from __future__ import annotations

import json
from dataclasses import replace

import pytest

from contestpilot import runner as runner_module
from contestpilot.contests import ContestFetchError
from contestpilot.models import (
    LoginStatus,
    RegistrationResult,
    RegistrationStatus,
    RunStatus,
)
from contestpilot.status import (
    SCHEMA_VERSION,
    build_status_document,
    determine_run_status,
    write_status_document,
)

from .conftest import DAY, NOW, make_contest


def result(status: RegistrationStatus, contest_id: int = 1, message=None):
    return RegistrationResult(make_contest(contest_id=contest_id), status, message)


def test_a_clean_run_is_a_success():
    assert (
        determine_run_status(LoginStatus.OK, [result(RegistrationStatus.REGISTERED)])
        is RunStatus.SUCCESS
    )


def test_a_captcha_is_reported_as_blocked_rather_than_failed():
    for status in (LoginStatus.CAPTCHA_REQUIRED, LoginStatus.TWO_FACTOR_REQUIRED):
        assert determine_run_status(status, []) is RunStatus.BLOCKED


def test_rejected_credentials_make_the_run_a_failure():
    assert determine_run_status(LoginStatus.INVALID_CREDENTIALS, []) is RunStatus.FAILED


def test_a_mixed_run_is_partial():
    results = [
        result(RegistrationStatus.REGISTERED, 1),
        result(RegistrationStatus.FAILED, 2),
    ]

    assert determine_run_status(LoginStatus.OK, results) is RunStatus.PARTIAL


def test_a_run_where_everything_failed_is_a_failure():
    results = [result(RegistrationStatus.FAILED, 1), result(RegistrationStatus.FAILED, 2)]

    assert determine_run_status(LoginStatus.OK, results) is RunStatus.FAILED


def test_the_status_document_matches_the_schema_the_app_reads(config):
    document = build_status_document(
        config,
        LoginStatus.OK,
        [result(RegistrationStatus.REGISTERED, 1234, "Registered by the cloud automation.")],
        message="1 of 1 eligible contest(s) registered.",
        now_epoch_seconds=NOW,
    )

    assert document["schemaVersion"] == SCHEMA_VERSION
    assert document["generatedAtEpochSeconds"] == NOW
    assert document["run"]["status"] == "SUCCESS"
    assert document["codeforces"]["handle"] == "tester"
    assert document["codeforces"]["loginStatus"] == "OK"

    registration = document["registrations"][0]
    assert registration["platform"] == "CODEFORCES"
    assert registration["contestId"] == "1234"
    assert registration["status"] == "REGISTERED"
    assert registration["updatedAtEpochSeconds"] == NOW


def test_the_status_document_never_carries_a_credential(config, monkeypatch):
    monkeypatch.setenv("CODEFORCES_PASSWORD", config.password)

    document = build_status_document(
        config,
        LoginStatus.ERROR,
        [result(RegistrationStatus.FAILED, 1, f"failed with {config.password}")],
        message=f"browser said {config.password}",
        now_epoch_seconds=NOW,
    )

    serialised = json.dumps(document)
    assert config.password not in serialised
    assert "***" in serialised


def test_the_document_is_written_as_json(tmp_path, config):
    document = build_status_document(config, LoginStatus.OK, [], now_epoch_seconds=NOW)

    path = write_status_document(document, tmp_path / "nested" / "status.json")

    assert path.exists()
    assert json.loads(path.read_text(encoding="utf-8"))["schemaVersion"] == SCHEMA_VERSION


class FakePageFactory:
    """Stands in for the Playwright context manager."""

    instances: list["FakePageFactory"] = []

    def __init__(self, config):
        self.config = config
        self.entered = False
        FakePageFactory.instances.append(self)

    def __enter__(self):
        self.entered = True
        return object()

    def __exit__(self, *_args):
        return False


@pytest.fixture(autouse=True)
def reset_factory_instances():
    FakePageFactory.instances.clear()
    yield
    FakePageFactory.instances.clear()


def test_a_contest_fetch_failure_still_publishes_a_status(config, monkeypatch):
    monkeypatch.setattr(
        runner_module,
        "fetch_contests",
        lambda: (_ for _ in ()).throw(ContestFetchError("Codeforces is down")),
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)

    assert document["run"]["status"] == "FAILED"
    assert "Codeforces is down" in document["run"]["message"]
    assert FakePageFactory.instances == []


def test_no_browser_is_started_when_nothing_qualifies(config, monkeypatch):
    monkeypatch.setattr(
        runner_module,
        "fetch_contests",
        lambda: [make_contest(contest_id=1, division="DIV_1")],
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)

    assert FakePageFactory.instances == []
    assert document["run"]["status"] == "SUCCESS"
    assert "No contest currently qualifies" in document["run"]["message"]


def test_an_unselected_division_inside_the_window_is_explained_to_the_user(
    config, monkeypatch
):
    monkeypatch.setattr(
        runner_module,
        "fetch_contests",
        lambda: [make_contest(contest_id=7, division="DIV_1", start_offset=2 * DAY)],
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)
    registrations = document["registrations"]

    assert [r["contestId"] for r in registrations] == ["7"]
    assert registrations[0]["status"] == "SKIPPED"
    assert "division" in registrations[0]["message"]


def test_a_contest_outside_the_window_is_not_reported_at_all(config, monkeypatch):
    monkeypatch.setattr(
        runner_module,
        "fetch_contests",
        lambda: [make_contest(contest_id=8, division="DIV_1", start_offset=60 * DAY)],
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)

    assert document["registrations"] == []


def test_check_only_reports_the_plan_without_opening_a_browser(config, monkeypatch):
    monkeypatch.setattr(runner_module, "fetch_contests", lambda: [make_contest()])

    document = runner_module.run(
        replace(config, check_only=True),
        browser_factory=FakePageFactory,
        now_epoch_seconds=NOW,
    )

    assert FakePageFactory.instances == []
    assert document["codeforces"]["loginStatus"] == "NOT_ATTEMPTED"
    assert document["registrations"][0]["status"] == "PENDING"
    assert "1 contest(s) qualify" in document["run"]["message"]


def test_a_blocked_sign_in_stops_before_any_registration(config, monkeypatch):
    from contestpilot.auth import LoginError

    monkeypatch.setattr(runner_module, "fetch_contests", lambda: [make_contest()])

    def blocked_login(_page, _config):
        raise LoginError(LoginStatus.CAPTCHA_REQUIRED, "Codeforces asked for a CAPTCHA.")

    monkeypatch.setattr(runner_module, "log_in", blocked_login)
    monkeypatch.setattr(
        runner_module,
        "register_for_contest",
        lambda *_args: pytest.fail("registration must not be attempted"),
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)

    assert document["run"]["status"] == "BLOCKED"
    assert document["codeforces"]["loginStatus"] == "CAPTCHA_REQUIRED"
    assert document["registrations"] == []


def test_every_eligible_contest_is_attempted_and_summarised(config, monkeypatch):
    contests = [
        make_contest(contest_id=1, start_offset=DAY),
        make_contest(contest_id=2, start_offset=2 * DAY),
    ]
    monkeypatch.setattr(runner_module, "fetch_contests", lambda: contests)
    monkeypatch.setattr(runner_module, "log_in", lambda *_args: None)

    outcomes = {
        1: RegistrationStatus.REGISTERED,
        2: RegistrationStatus.FAILED,
    }
    monkeypatch.setattr(
        runner_module,
        "register_for_contest",
        lambda _page, contest, _config: RegistrationResult(
            contest, outcomes[contest.contest_id]
        ),
    )

    document = runner_module.run(config, browser_factory=FakePageFactory, now_epoch_seconds=NOW)

    assert document["run"]["status"] == "PARTIAL"
    assert document["run"]["message"] == "1 of 2 eligible contest(s) registered."
    assert {r["contestId"] for r in document["registrations"]} == {"1", "2"}


def test_a_browser_crash_is_reported_rather_than_propagated(config, monkeypatch):
    monkeypatch.setattr(runner_module, "fetch_contests", lambda: [make_contest()])

    class ExplodingFactory(FakePageFactory):
        def __enter__(self):
            raise RuntimeError("chromium failed to launch")

    document = runner_module.run(config, browser_factory=ExplodingFactory, now_epoch_seconds=NOW)

    assert document["run"]["status"] == "FAILED"
    assert "chromium failed to launch" in document["run"]["message"]
