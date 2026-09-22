from __future__ import annotations

import pytest
import requests

from contestpilot.contests import (
    ContestFetchError,
    classify_division,
    fetch_contests,
    select_eligible,
)
from contestpilot.models import SkipReason

from .conftest import DAY, NOW, make_contest


class FakeResponse:
    def __init__(self, payload=None, status_code=200, raise_json=False):
        self._payload = payload
        self.status_code = status_code
        self._raise_json = raise_json

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"{self.status_code}")

    def json(self):
        if self._raise_json:
            raise ValueError("not json")
        return self._payload


class FakeSession:
    def __init__(self, response=None, error=None):
        self._response = response
        self._error = error
        self.last_params = None

    def get(self, url, params=None, timeout=None, headers=None):
        self.last_params = params
        if self._error is not None:
            raise self._error
        return self._response


@pytest.mark.parametrize(
    ("name", "expected"),
    [
        ("Codeforces Round 999 (Div. 1 + Div. 2)", "DIV_1_2"),
        ("Codeforces Round 999 (Div 1 and Div 2)", "DIV_1_2"),
        ("Codeforces Round 999 (Div. 1)", "DIV_1"),
        ("Codeforces Round 999 (Div. 2)", "DIV_2"),
        ("Codeforces Round 999 (div 3)", "DIV_3"),
        ("Codeforces Round 999 (DIV.4)", "DIV_4"),
        ("Educational Codeforces Round 999", "OTHER"),
        ("Codeforces Round 1234", "OTHER"),
    ],
)
def test_division_classification_matches_the_android_rules(name, expected):
    assert classify_division(name) == expected


def test_gym_contests_are_excluded_from_the_request():
    session = FakeSession(FakeResponse({"status": "OK", "result": []}))

    fetch_contests(session)

    assert session.last_params == {"gym": "false"}


def test_contests_are_parsed_and_classified():
    session = FakeSession(
        FakeResponse(
            {
                "status": "OK",
                "result": [
                    {
                        "id": 1234,
                        "name": "Codeforces Round 999 (Div. 2)",
                        "phase": "BEFORE",
                        "durationSeconds": 7200,
                        "startTimeSeconds": NOW + DAY,
                    }
                ],
            }
        )
    )

    contest = fetch_contests(session)[0]

    assert contest.contest_id == 1234
    assert contest.division == "DIV_2"
    assert contest.url == "https://codeforces.com/contest/1234"
    assert contest.registration_url == "https://codeforces.com/contestRegistration/1234"


def test_rows_missing_an_id_or_name_are_dropped():
    session = FakeSession(
        FakeResponse(
            {
                "status": "OK",
                "result": [
                    {"name": "No id", "phase": "BEFORE"},
                    {"id": 2, "phase": "BEFORE"},
                    {"id": 3, "name": "   ", "phase": "BEFORE"},
                    {"id": 4, "name": "Good", "phase": "BEFORE", "durationSeconds": 1},
                ],
            }
        )
    )

    assert [c.contest_id for c in fetch_contests(session)] == [4]


def test_a_missing_start_time_is_kept_as_none():
    session = FakeSession(
        FakeResponse(
            {
                "status": "OK",
                "result": [{"id": 1, "name": "Unscheduled", "phase": "BEFORE"}],
            }
        )
    )

    assert fetch_contests(session)[0].start_time_seconds is None


def test_a_failed_envelope_is_reported_with_its_comment():
    session = FakeSession(FakeResponse({"status": "FAILED", "comment": "rate limited"}))

    with pytest.raises(ContestFetchError) as excinfo:
        fetch_contests(session)

    assert "rate limited" in str(excinfo.value)


def test_a_non_json_response_is_reported_clearly():
    session = FakeSession(FakeResponse(raise_json=True))

    with pytest.raises(ContestFetchError) as excinfo:
        fetch_contests(session)

    assert "not JSON" in str(excinfo.value)


def test_a_network_error_is_wrapped():
    session = FakeSession(error=requests.ConnectionError("offline"))

    with pytest.raises(ContestFetchError):
        fetch_contests(session)


def test_an_eligible_contest_is_selected():
    eligible, _ = select_eligible(
        [make_contest()], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert [c.contest_id for c in eligible] == [1]


def test_the_exact_window_boundary_is_inside_the_window():
    inside = make_contest(contest_id=1, start_offset=7 * DAY)
    outside = make_contest(contest_id=2, start_offset=7 * DAY + 1)

    eligible, skipped = select_eligible(
        [inside, outside], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert [c.contest_id for c in eligible] == [1]
    assert skipped == [(outside, SkipReason.OUTSIDE_WINDOW)]


def test_a_contest_starting_exactly_now_is_not_upcoming():
    contest = make_contest(start_offset=0)

    eligible, skipped = select_eligible(
        [contest], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert eligible == []
    assert skipped == [(contest, SkipReason.NOT_UPCOMING)]


def test_a_running_contest_is_not_registered_for():
    contest = make_contest(phase="CODING")

    eligible, skipped = select_eligible(
        [contest], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert eligible == []
    assert skipped == [(contest, SkipReason.NOT_UPCOMING)]


def test_a_contest_with_no_start_time_is_skipped_with_a_specific_reason():
    contest = make_contest(start_offset=None)

    _, skipped = select_eligible(
        [contest], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert skipped == [(contest, SkipReason.NO_START_TIME)]


def test_an_unselected_division_is_skipped():
    contest = make_contest(division="DIV_1")

    eligible, skipped = select_eligible(
        [contest], divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert eligible == []
    assert skipped == [(contest, SkipReason.DIVISION_NOT_SELECTED)]


def test_nothing_is_selected_when_auto_registration_is_off():
    contest = make_contest()

    eligible, skipped = select_eligible(
        [contest],
        divisions=("DIV_2",),
        window_days=7,
        auto_register_enabled=False,
        now_epoch_seconds=NOW,
    )

    assert eligible == []
    assert skipped == [(contest, SkipReason.AUTO_REGISTRATION_DISABLED)]


def test_selection_is_ordered_by_start_time():
    contests = [
        make_contest(contest_id=3, start_offset=3 * DAY),
        make_contest(contest_id=1, start_offset=1 * DAY),
        make_contest(contest_id=2, start_offset=2 * DAY),
    ]

    eligible, _ = select_eligible(
        contests, divisions=("DIV_2",), window_days=7, now_epoch_seconds=NOW
    )

    assert [c.contest_id for c in eligible] == [1, 2, 3]
