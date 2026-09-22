"""Configuration, read entirely from the environment.

No credential is ever written in source, committed, or echoed back. In GitHub Actions the
values come from repository secrets; locally they come from your shell.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

# Divisions the user may enable. Mirrors ContestDivision in the Android app.
VALID_DIVISIONS = ("DIV_1", "DIV_2", "DIV_3", "DIV_4", "DIV_1_2")

DEFAULT_DIVISIONS = ("DIV_2", "DIV_3", "DIV_4", "DIV_1_2")

DEFAULT_WINDOW_DAYS = 7

#: Codeforces can be slow to fully render its login/registration pages, and a
#: datacenter CI IP may see extra latency before the real form appears. 45s gives
#: real slowness room without letting a truly stuck page hang the job.
DEFAULT_TIMEOUT_MS = 45_000

#: Names of the environment variables that hold secrets. Used by the redaction helper so
#: a value can never reach the log, whatever path it took to get there.
SECRET_ENV_VARS = ("CODEFORCES_PASSWORD",)


class ConfigError(RuntimeError):
    """Raised when required configuration is absent or unusable."""


@dataclass(frozen=True)
class Config:
    """Everything one automation run needs."""

    handle: str
    password: str = field(repr=False)
    divisions: tuple[str, ...] = DEFAULT_DIVISIONS
    window_days: int = DEFAULT_WINDOW_DAYS
    auto_register_enabled: bool = True
    dry_run: bool = False
    #: Report what would happen without opening a browser or reading the password.
    check_only: bool = False
    headless: bool = True
    timeout_ms: int = DEFAULT_TIMEOUT_MS
    workflow_run_url: str | None = None
    #: Local directory to save a screenshot + HTML dump to on a browser failure.
    #: `None` disables capture entirely (the default for anything that builds a
    #: Config directly, e.g. tests); the CLI enables it by default.
    debug_dir: str | None = None

    def __str__(self) -> str:  # pragma: no cover - trivial
        return (
            f"Config(handle={self.handle!r}, divisions={self.divisions}, "
            f"window_days={self.window_days}, dry_run={self.dry_run})"
        )


def _parse_bool(value: str | None, default: bool) -> bool:
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def parse_divisions(raw: str | None) -> tuple[str, ...]:
    """Parse ``DIV_2,DIV_3`` into a validated tuple.

    An unrecognised name is an error rather than a silent skip: quietly ignoring it would
    mean registering for a different set of contests than the user asked for.
    """
    if raw is None or raw.strip() == "":
        return DEFAULT_DIVISIONS

    divisions = tuple(
        part.strip().upper().replace(".", "_").replace(" ", "")
        for part in raw.split(",")
        if part.strip()
    )
    unknown = [d for d in divisions if d not in VALID_DIVISIONS]
    if unknown:
        raise ConfigError(
            f"Unknown division(s): {', '.join(unknown)}. "
            f"Valid values are {', '.join(VALID_DIVISIONS)}."
        )
    return divisions or DEFAULT_DIVISIONS


def parse_window_days(raw: str | None) -> int:
    if raw is None or raw.strip() == "":
        return DEFAULT_WINDOW_DAYS
    try:
        days = int(raw.strip())
    except ValueError as exc:
        raise ConfigError(f"CONTEST_WINDOW_DAYS must be a whole number, got {raw!r}") from exc
    if not 1 <= days <= 60:
        raise ConfigError(f"CONTEST_WINDOW_DAYS must be between 1 and 60, got {days}")
    return days


def load_config(
    env: dict[str, str] | None = None,
    *,
    dry_run: bool = False,
    check_only: bool = False,
) -> Config:
    """Build a :class:`Config` from environment variables.

    In ``dry_run`` and ``check_only`` mode credentials are optional, so the workflow can
    be validated end to end before any secret exists.
    """
    source = os.environ if env is None else env

    handle = (source.get("CODEFORCES_HANDLE") or "").strip()
    password = source.get("CODEFORCES_PASSWORD") or ""

    if not dry_run and not check_only:
        missing = [
            name
            for name, value in (
                ("CODEFORCES_HANDLE", handle),
                ("CODEFORCES_PASSWORD", password),
            )
            if not value
        ]
        if missing:
            raise ConfigError(
                "Missing required secret(s): "
                + ", ".join(missing)
                + ". Add them as GitHub Actions repository secrets "
                "(Settings > Secrets and variables > Actions)."
            )

    return Config(
        handle=handle,
        password=password,
        divisions=parse_divisions(source.get("AUTO_REGISTER_DIVISIONS")),
        window_days=parse_window_days(source.get("CONTEST_WINDOW_DAYS")),
        auto_register_enabled=_parse_bool(source.get("AUTO_REGISTRATION_ENABLED"), True),
        dry_run=dry_run,
        check_only=check_only,
        headless=_parse_bool(source.get("PLAYWRIGHT_HEADLESS"), True),
        timeout_ms=int(source.get("PLAYWRIGHT_TIMEOUT_MS") or DEFAULT_TIMEOUT_MS),
        workflow_run_url=source.get("WORKFLOW_RUN_URL") or None,
        debug_dir=source.get("PLAYWRIGHT_DEBUG_DIR") or None,
    )


def redact(text: str, env: dict[str, str] | None = None) -> str:
    """Replace any secret value found in ``text`` with ``***``.

    Playwright and requests both like to quote the input that caused a failure, so every
    message that reaches a log or the published status file passes through here first.
    """
    source = os.environ if env is None else env
    redacted = text
    for name in SECRET_ENV_VARS:
        value = source.get(name)
        if value and len(value) >= 3:
            redacted = redacted.replace(value, "***")
    return redacted
