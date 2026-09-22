"""Value types shared by the automation modules.

The status names here are the same strings the Android app parses, so the two sides stay
in step without a shared schema file.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class RegistrationStatus(str, Enum):
    """Mirrors ``RegistrationStatus`` in the Android app."""

    PENDING = "PENDING"
    REGISTERED = "REGISTERED"
    ALREADY_REGISTERED = "ALREADY_REGISTERED"
    SKIPPED = "SKIPPED"
    FAILED = "FAILED"


class LoginStatus(str, Enum):
    OK = "OK"
    INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    #: Codeforces presented a CAPTCHA. The automation stops; it does not try to solve it.
    CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED"
    #: A second factor was requested. Again, the automation stops.
    TWO_FACTOR_REQUIRED = "TWO_FACTOR_REQUIRED"
    NOT_ATTEMPTED = "NOT_ATTEMPTED"
    ERROR = "ERROR"


class RunStatus(str, Enum):
    """Mirrors ``AutomationRunStatus`` in the Android app."""

    SUCCESS = "SUCCESS"
    PARTIAL = "PARTIAL"
    FAILED = "FAILED"
    BLOCKED = "BLOCKED"


class SkipReason(str, Enum):
    AUTO_REGISTRATION_DISABLED = "auto-registration is disabled"
    OUTSIDE_WINDOW = "starts outside the configured window"
    DIVISION_NOT_SELECTED = "division is not selected for auto-registration"
    NOT_UPCOMING = "contest is not upcoming"
    NO_START_TIME = "contest has no announced start time"


@dataclass(frozen=True)
class Contest:
    """A Codeforces contest, as ``contest.list`` describes it.

    Only documented fields are modelled. There is deliberately no ``rated`` attribute:
    the Codeforces API does not expose one.
    """

    contest_id: int
    name: str
    phase: str
    start_time_seconds: int | None
    duration_seconds: int
    division: str

    @property
    def url(self) -> str:
        return f"https://codeforces.com/contest/{self.contest_id}"

    @property
    def registration_url(self) -> str:
        return f"https://codeforces.com/contestRegistration/{self.contest_id}"


@dataclass(frozen=True)
class RegistrationResult:
    """What happened to one contest during this run."""

    contest: Contest
    status: RegistrationStatus
    message: str | None = None

    @property
    def succeeded(self) -> bool:
        return self.status in (
            RegistrationStatus.REGISTERED,
            RegistrationStatus.ALREADY_REGISTERED,
        )
