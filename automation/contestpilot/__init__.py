"""Cloud automation for ContestPilot.

Runs in GitHub Actions. Reads upcoming Codeforces contests, decides which ones the user
asked to be registered for, drives a real browser session to register, and publishes a
machine-readable report the Android app reads.
"""

__all__ = ["config", "models", "contests", "auth", "register", "status", "runner"]
