from __future__ import annotations

import pytest

from contestpilot.config import (
    DEFAULT_DIVISIONS,
    ConfigError,
    load_config,
    parse_divisions,
    parse_window_days,
    redact,
)


def test_missing_secrets_are_reported_with_where_to_put_them():
    with pytest.raises(ConfigError) as excinfo:
        load_config(env={})

    message = str(excinfo.value)
    assert "CODEFORCES_HANDLE" in message
    assert "CODEFORCES_PASSWORD" in message
    assert "repository secrets" in message


def test_credentials_are_optional_for_a_dry_run():
    config = load_config(env={}, dry_run=True)

    assert config.dry_run is True
    assert config.handle == ""


def test_credentials_are_optional_for_a_check_only_run():
    config = load_config(env={}, check_only=True)

    assert config.check_only is True


def test_configuration_is_read_from_the_environment():
    config = load_config(
        env={
            "CODEFORCES_HANDLE": "tourist",
            "CODEFORCES_PASSWORD": "secret",
            "AUTO_REGISTER_DIVISIONS": "DIV_2, DIV_3",
            "CONTEST_WINDOW_DAYS": "3",
            "AUTO_REGISTRATION_ENABLED": "false",
            "WORKFLOW_RUN_URL": "https://github.com/me/repo/actions/runs/1",
        }
    )

    assert config.handle == "tourist"
    assert config.divisions == ("DIV_2", "DIV_3")
    assert config.window_days == 3
    assert config.auto_register_enabled is False
    assert config.workflow_run_url == "https://github.com/me/repo/actions/runs/1"


def test_the_password_never_appears_in_a_repr_or_str():
    config = load_config(
        env={"CODEFORCES_HANDLE": "tourist", "CODEFORCES_PASSWORD": "hunter2-and-more"}
    )

    assert "hunter2-and-more" not in repr(config)
    assert "hunter2-and-more" not in str(config)


def test_division_parsing_accepts_common_spellings():
    assert parse_divisions("div_2,div.3") == ("DIV_2", "DIV_3")
    assert parse_divisions("DIV_1_2") == ("DIV_1_2",)
    assert parse_divisions(None) == DEFAULT_DIVISIONS
    assert parse_divisions("   ") == DEFAULT_DIVISIONS


def test_an_unknown_division_is_an_error_rather_than_a_silent_skip():
    with pytest.raises(ConfigError) as excinfo:
        parse_divisions("DIV_2,DIV_9")

    assert "DIV_9" in str(excinfo.value)


@pytest.mark.parametrize("raw", ["0", "61", "seven"])
def test_an_unusable_window_is_rejected(raw):
    with pytest.raises(ConfigError):
        parse_window_days(raw)


def test_window_parsing_defaults_to_seven_days():
    assert parse_window_days(None) == 7
    assert parse_window_days("14") == 14


def test_redaction_removes_the_password_from_any_text():
    env = {"CODEFORCES_PASSWORD": "super-secret-value"}

    redacted = redact("login failed for super-secret-value", env=env)

    assert "super-secret-value" not in redacted
    assert "***" in redacted


def test_redaction_leaves_text_alone_when_no_secret_is_set():
    assert redact("nothing to hide", env={}) == "nothing to hide"


def test_a_very_short_password_is_not_used_for_blanket_redaction():
    # Redacting a two-character value would mangle unrelated text without helping.
    env = {"CODEFORCES_PASSWORD": "ab"}

    assert redact("about abstract absolutes", env=env) == "about abstract absolutes"
