"""Entry point for the ContestPilot cloud automation.

Run by GitHub Actions on a schedule. Writes a status JSON document and exits non-zero
only when the run itself could not be carried out, so a workflow failure means something
a human should look at.
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from contestpilot.config import ConfigError, load_config  # noqa: E402
from contestpilot.models import RunStatus  # noqa: E402
from contestpilot.runner import run  # noqa: E402
from contestpilot.status import write_status_document  # noqa: E402


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="contestpilot-automation",
        description=(
            "Register a Codeforces account for upcoming contests and publish a status "
            "report the ContestPilot Android app can read."
        ),
    )
    parser.add_argument(
        "--output",
        default="status/automation-status.json",
        help="Where to write the status document (default: %(default)s).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help=(
            "Read contests and reach the registration form without signing in for real "
            "or submitting anything. Credentials are optional in this mode."
        ),
    )
    parser.add_argument(
        "--check-only",
        action="store_true",
        help=(
            "Only list which contests would be registered for. No browser is started "
            "and no credential is read."
        ),
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Log at DEBUG level. Secrets are redacted regardless of level.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
    )
    logger = logging.getLogger("contestpilot")

    try:
        config = load_config(dry_run=args.dry_run, check_only=args.check_only)
    except ConfigError as exc:
        logger.error("%s", exc)
        return 2

    document = run(config)
    path = write_status_document(document, args.output)
    logger.info("Status written to %s", path)

    status = document["run"]["status"]
    message = document["run"]["message"]
    logger.info("Run status: %s - %s", status, message)

    if status == RunStatus.FAILED.value:
        return 1
    if status == RunStatus.BLOCKED.value:
        # Blocked means Codeforces wants a human. That is an expected outcome, not a
        # broken workflow, so the run succeeds and the app shows the reason.
        logger.warning(
            "Codeforces requires manual verification. Register manually for now."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
