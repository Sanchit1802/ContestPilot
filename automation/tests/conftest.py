"""Shared fixtures. Nothing here touches the network or starts a browser."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from contestpilot.config import Config  # noqa: E402
from contestpilot.models import Contest  # noqa: E402

#: Fixed reference instant, so no test depends on the real clock.
NOW = 1_800_000_000

DAY = 24 * 60 * 60


@pytest.fixture
def config() -> Config:
    return Config(
        handle="tester",
        password="not-a-real-password",
        divisions=("DIV_2", "DIV_3"),
        window_days=7,
        timeout_ms=1_000,
    )


def make_contest(
    contest_id: int = 1,
    name: str = "Codeforces Round 1 (Div. 2)",
    phase: str = "BEFORE",
    start_offset: int | None = DAY,
    division: str = "DIV_2",
) -> Contest:
    return Contest(
        contest_id=contest_id,
        name=name,
        phase=phase,
        start_time_seconds=None if start_offset is None else NOW + start_offset,
        duration_seconds=7_200,
        division=division,
    )
