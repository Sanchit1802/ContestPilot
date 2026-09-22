# ContestPilot automation

Scheduled Codeforces auto-registration, run by GitHub Actions. Nothing here runs on your
PC or inside the Android app.

See the repository `README.md` for setup, required secrets and cost notes.

## Layout

```
automation/
  contestpilot/
    config.py      # environment-driven configuration (no secrets in source)
    models.py      # contest / result value types
    contests.py    # Codeforces contest.list client + eligibility filtering
    auth.py        # Playwright sign-in, with CAPTCHA / 2FA detection
    register.py    # the registration flow for one contest
    status.py      # the machine-readable report the Android app reads
    runner.py      # orchestration
  main.py          # entry point
  tests/           # offline pytest suite
```

## Running locally

```bash
python -m pip install -r automation/requirements-dev.txt
python -m playwright install --with-deps chromium
python -m pytest automation/tests            # offline, no credentials needed

# A dry run never signs in and never registers:
python automation/main.py --dry-run --output automation-status.json
```
