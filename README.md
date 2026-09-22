# ContestPilot

A personal competitive-programming contest manager: an Android app that shows upcoming
Codeforces and CodeChef contests, reminds you before they start, and reports what a cloud
automation has registered you for.

Your PC never has to be on. Registration runs in GitHub Actions; reminders are exact
alarms on your phone.

---

## Contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Android app](#android-app)
- [Building the app](#building-the-app)
- [Data sources](#data-sources)
- [Cloud auto-registration](#cloud-auto-registration)
- [Setting up GitHub Actions](#setting-up-github-actions)
- [Connecting the app to the status feed](#connecting-the-app-to-the-status-feed)
- [Notifications](#notifications)
- [Database schema](#database-schema)
- [Security model](#security-model)
- [Testing](#testing)
- [Cost](#cost)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)

---

## What it does

- Lists upcoming **Codeforces** and **CodeChef** contests inside a configurable window
  (7 days by default).
- Classifies Codeforces rounds by division — Div. 1, Div. 2, Div. 3, Div. 4 and the
  combined Div. 1 + Div. 2.
- Shows dates and times in **IST (Asia/Kolkata)** with a live countdown.
- Fires a local notification a configurable number of minutes before the start
  (10 by default), whether or not the app is open.
- Registers your Codeforces account for qualifying contests **from the cloud**, and shows
  you the result.
- Keeps working offline against cached data, and says plainly when a source is stale.

---

## Architecture

```
                   ANDROID APP (Kotlin, Jetpack Compose)
                                  |
     +----------------------------+----------------------------+
     |                            |                            |
Contest data                 User settings              Reminders/alarms
(Room cache)                 (DataStore)                (AlarmManager)
     |                            |                            |
     +-------------+--------------+----------------------------+
                   |
        reads (HTTPS, unauthenticated)
                   |
     +-------------+--------------+------------------------------+
     |             |                                             |
Codeforces     CodeChef                          Status JSON on the `status` branch
contest.list   contest list                                      ^
                                                                 | publishes
                                                          GITHUB ACTIONS (cron)
                                                                 |
                                                          Python + Playwright
                                                                 |
                                                             Codeforces
```

Responsibilities are split deliberately:

| Layer | Owns |
|---|---|
| **Android** | UI, settings, local cache, notifications, exact alarms, status display |
| **GitHub Actions** | Scheduled execution — no always-on server, no PC |
| **Python + Playwright** | The browser session that performs registration |
| **`status` branch** | The only "backend": one public JSON file, no credentials |

The app never automates a browser and never holds a Codeforces credential. That is not an
oversight — it is the point of the split.

---

## Android app

- Kotlin, Jetpack Compose, Material 3, MVVM with `StateFlow`
- Room for the local cache, DataStore for settings
- Retrofit + Gson over a single shared OkHttp client
- Navigation Compose for the two screens (contests, settings)
- `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`
- Package `com.sanchit.contestpilot`

### Screens

**Contests** — a synchronisation panel (per-platform freshness, last automation run) above
a list of contest cards. Each card shows the platform, division, registration status,
name, countdown, IST date and time, length, and any message the automation left.

**Settings** — platforms, contest window, notifications and lead time, auto-registration
and its divisions, and the automation status feed URL. There is no password field, by
design.

### Source layout

```
app/src/main/java/com/sanchit/contestpilot/
  domain/model/     Contest, Platform, ContestDivision, ContestPhase,
                    RegistrationState, AutomationStatus, SyncState, AppSettings
  domain/logic/     ContestClassifier, ContestFilter, CountdownFormatter, DateTimeUtils,
                    RegistrationEligibility, NotificationScheduleCalculator, TimeProvider
  data/remote/      Codeforces, CodeChef and status-feed APIs + DTOs, NetworkModule
  data/provider/    ContestProvider and its two implementations
  data/local/       Room database, entities, DAOs
  data/settings/    DataStore-backed SettingsRepository
  data/repository/  ContestRepository, AutomationStatusRepository
  notification/     ContestAlarmScheduler, ContestAlarmReceiver, SystemEventReceiver,
                    ReminderMaintenanceWorker, ContestSyncWorker, NotificationChannels
  ui/               Compose screens, view models, theme
  di/               ServiceLocator
```

---

## Building the app

Requirements: JDK 21, Android SDK platform 36.1, and the Android SDK command-line tools.
Gradle 9.3.1 comes from the wrapper.

```bash
./gradlew assembleDebug            # debug APK
./gradlew testDebugUnitTest        # unit tests (no device needed)
./gradlew assembleDebugAndroidTest # compile instrumentation tests
./gradlew connectedDebugAndroidTest# run them on a device or emulator
./gradlew assembleRelease          # minified release APK
```

The release build is minified and resource-shrunk; the R8 rules in
`app/proguard-rules.pro` keep the Gson DTOs, Retrofit interfaces, Room database and the
manifest-declared receivers.

`gradle.properties` sets `android.disallowKotlinSourceSets=false`. AGP 9's built-in Kotlin
support otherwise rejects the source directory KSP registers for Room's generated code.

---

## Data sources

### Codeforces

`https://codeforces.com/api/contest.list?gym=false` — the documented public method
([API reference](https://codeforces.com/apiHelp/methods)). No authentication, no key.

Two things the API genuinely does **not** provide, which shaped the design:

- **There is no registration method.** The full method list is `blogEntry.*`, `contest.*`
  (`hacks`, `list`, `ratingChanges`, `standings`, `status`), `group.isManager`,
  `problemset.*`, `recentActions`, `system.status` and `user.*`. None of them register
  for a contest or report whether you are registered. `contest.standings` for a regular
  contest is anonymous-only with exactly one `contestId` parameter and returns official
  standings, not a registrant list. This is why registration is done by browser
  automation, and why registration status can only come from the automation's own report.
- **There is no `rated` field.** The documented `Contest` object has no such property, so
  ContestPilot classifies rounds by division only and never claims to know whether a
  round is rated. Selecting "Div. 2" selects contests whose name says Div. 2 — nothing
  more is implied.

### CodeChef

`https://www.codechef.com/api/list/contests/all` — the JSON endpoint that powers
codechef.com/contests. CodeChef retired its documented developer API and has published no
replacement, so there is no official alternative. This endpoint is **unofficial and may
change without notice**, so `CodeChefContestProvider` treats every failure as recoverable:
the app keeps showing its cache and says the source is stale.

CodeChef contests carry no division; they are classified `OTHER` rather than being given
a Codeforces-style division inferred from the name.

Auto-registration is **not** implemented for CodeChef. Its contests show
"Not automated" rather than a status ContestPilot cannot actually determine.

---

## Cloud auto-registration

`automation/` is a standalone Python package. It runs only in GitHub Actions.

```
automation/
  main.py                  entry point (--dry-run, --check-only)
  requirements.txt         playwright, requests (pinned)
  requirements-dev.txt     + pytest
  contestpilot/
    config.py              environment-driven config and secret redaction
    models.py              Contest, RegistrationResult, status enums
    contests.py            contest.list client + eligibility rules
    auth.py                Playwright sign-in, CAPTCHA/2FA detection
    register.py            the registration flow for one contest
    status.py              the JSON document the app reads
    runner.py              orchestration
  tests/                   offline pytest suite
```

### What a run does

1. Fetch `contest.list`.
2. Select contests that are upcoming, start inside the window (the exact boundary counts
   as inside), and whose division you enabled.
3. If nothing qualifies, **no browser is started** and the run ends.
4. Otherwise sign in to Codeforces with Playwright.
5. For each contest, open `https://codeforces.com/contestRegistration/<id>`:
   - already registered → recorded as `ALREADY_REGISTERED`, nothing submitted;
   - registration not open → recorded as `PENDING`, retried next run;
   - otherwise the form is submitted, and the page is **re-read to confirm** before
     anything is reported as registered.
6. Write `automation-status.json` and publish it to the `status` branch.

Duplicate registration is prevented at two points: the automation never submits when the
page already says you are registered, and the app treats `REGISTERED` /
`ALREADY_REGISTERED` as ineligible for a further attempt.

### Anti-automation protections

If Codeforces presents a CAPTCHA, a Turnstile widget, or asks for a second factor, the
run **stops and reports it**. No attempt is made to solve, bypass or work around any such
check. The status becomes `BLOCKED`, the app explains that you need to register manually,
and the workflow still exits successfully — a human being asked to act is an expected
outcome, not a broken pipeline.

### Running it locally

```bash
python -m pip install -r automation/requirements-dev.txt
python -m playwright install --with-deps chromium

cd automation
python -m pytest tests -q                         # offline, no credentials

python main.py --check-only --output /tmp/check.json   # what would be registered
python main.py --dry-run   --output /tmp/dry.json      # reach the form, submit nothing
```

`--check-only` and `--dry-run` never require credentials.

---

## Setting up GitHub Actions

### 1. Push this repository to GitHub

A **public** repository is recommended: standard GitHub-hosted runners are free for public
repositories, and the app can then read the status file anonymously. Nothing published to
the `status` branch is sensitive.

If you make it private, the app cannot read the status feed without a token — and
shipping a token in an APK is exactly what this design avoids. Use a public repository, or
accept that the app will show contests and reminders but not registration status.

### 2. Add the secrets

**Settings → Secrets and variables → Actions → Secrets → New repository secret**

| Secret | Value |
|---|---|
| `CODEFORCES_HANDLE` | Your Codeforces handle |
| `CODEFORCES_PASSWORD` | Your Codeforces password |

Do not put either of these in a file, in the app, or in this README. GitHub encrypts
repository secrets and masks them in logs; the automation additionally redacts them from
every message it writes.

> Consider whether you are comfortable storing your Codeforces password as a repository
> secret at all. If not, leave `AUTO_REGISTRATION_ENABLED` set to `false` and use
> ContestPilot for contests and reminders only — everything else works without it.

### 3. Add the variables (optional)

**Settings → Secrets and variables → Actions → Variables**

| Variable | Default | Meaning |
|---|---|---|
| `AUTO_REGISTER_DIVISIONS` | `DIV_2,DIV_3,DIV_4,DIV_1_2` | Divisions to register for |
| `CONTEST_WINDOW_DAYS` | `7` | How far ahead to look (1–60) |
| `AUTO_REGISTRATION_ENABLED` | `true` | Master switch for the cloud side |

These mirror the app's settings. The app's own toggles control what *it* displays and
schedules; these control what the *cloud* does. Keep them in step.

### 4. Enable workflows

**Actions → I understand my workflows, go ahead and enable them.**

| Workflow | Schedule | Does |
|---|---|---|
| `contest-check.yml` | daily, 01:35 UTC | Lists qualifying contests. No sign-in, no browser, no credential. Also keeps the repository active. |
| `auto-register.yml` | every 3 hours | Signs in and registers. Publishes the status document. |
| `ci.yml` | on push / PR | Python tests, Android unit tests, APK build |

Run `auto-register` once manually (**Actions → Auto-register → Run workflow**) to create
the `status` branch. Tick *dry run* the first time if you want to verify sign-in without
submitting anything.

---

## Connecting the app to the status feed

After the first successful run, the status document lives at:

```
https://raw.githubusercontent.com/<your-user>/<your-repo>/status/automation-status.json
```

Open **Settings → Cloud status feed** in the app and paste that URL. Only `https://` is
accepted. Optionally enter your Codeforces handle — it is public information, stored as
plain text, and used for display only.

The app then shows the last run's outcome, its timestamp, and a per-contest registration
status on each card.

---

## Notifications

A reminder is delivered by an **exact alarm** (`AlarmManager.setExactAndAllowWhileIdle`),
not a periodic background job, because only an exact alarm can be relied on to fire at
"start minus ten minutes".

- Text: `Codeforces Round 999 starts in 10 minutes.`
- Channel: *Contest reminders*, high importance.
- Works with the app closed.
- Every scheduled alarm is mirrored into Room. After a reboot, an app update, a clock
  change or a time-zone change, `SystemEventReceiver` enqueues `ReminderMaintenanceWorker`
  to re-arm everything still pending.
- A reminder is marked delivered when it fires, so a later reschedule or a reboot replay
  cannot post it twice.
- When contest data changes, alarms are diffed: new ones are scheduled, changed ones
  replaced, obsolete ones cancelled.
- Alarms are stored as absolute epoch milliseconds, so a time-zone change never shifts
  one. IST is applied only when rendering text.

### Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Android 13+ runtime permission; requested on first launch |
| `USE_EXACT_ALARM` | Android 13+; contest reminders are user-set, time-critical alarms |
| `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | The Android 12 equivalent |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms the system drops on reboot |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetching contests and the status feed |

If the OS refuses exact alarms, the app falls back to `setAndAllowWhileIdle` and shows a
banner explaining that reminders may arrive a few minutes late and how to grant the
permission. If notification permission is denied, a banner says so rather than failing
silently.

`ContestSyncWorker` is a *periodic* WorkManager job (every 6 hours) that refreshes contest
data and re-syncs alarms. It never delivers the reminder itself.

---

## Database schema

Room, version 1, database `contestpilot.db`. Schemas are exported to `app/schemas/`.

**`contests`** — the cached list. Primary key `id` (`"CODEFORCES:1234"`), indexed on
`startTimeSeconds` and `platform`. Columns: `platform`, `platformContestId`, `name`,
`phase`, `startTimeSeconds` (nullable), `durationSeconds`, `division`, `url`,
`fetched_at_epoch_seconds`. Raw API payloads are never stored.

**`registration_states`** — primary key `contestId`, plus `status`, `message`,
`updatedAtEpochSeconds`. Kept separate from `contests` so refreshing the contest list
never discards what the automation reported. Orphans are pruned after each refresh.

**`notification_schedules`** — primary key `contestId`, plus `contestName`,
`platformName`, `requestCode`, `triggerAtEpochMillis`, `contestStartEpochMillis`,
`leadMinutes`, `delivered`, `exact`, `scheduledAtEpochMillis`. This is what makes
reminders survive a reboot.

**`sync_states`** — primary key `source` (`CODEFORCES`, `CODECHEF`,
`AUTOMATION_STATUS`), plus `outcome`, `lastSuccessEpochSeconds`,
`lastAttemptEpochSeconds`, `message`. Last success is tracked separately from last
attempt, so a failure can say "showing data from 09:00" instead of just "failed".

**`automation_status`** — a single row (`id = 0`) with the last run's status, message,
timestamp, workflow URL, handle and registration counts.

Settings live in DataStore (`contestpilot_settings`) rather than Room: they are a small,
flat key-value set, and DataStore gives transactional writes and a `Flow` without a table.

---

## Security model

**The Android app cannot access any cloud credential.** It has no field for a Codeforces
password, no code path that reads one, and no token of any kind. The APK contains nothing
that would let an attacker who extracted it act as you.

| Secret | Where it lives | Who can read it |
|---|---|---|
| Codeforces password | GitHub Actions repository secret | The workflow at run time |
| Codeforces handle | Repository secret + optionally the app (public info) | — |
| Session cookie | In-memory Playwright context, destroyed at run end | Nobody |
| Playwright storage state | **Never saved** | — |

Specific measures:

- `automation/contestpilot/config.py` reads credentials from the environment only. The
  `Config` dataclass marks `password` as `repr=False`, and `redact()` scrubs any secret
  value out of every message before it reaches a log or the status file. Both behaviours
  are covered by tests.
- The password is typed into the form by Playwright; it never appears on a command line.
- The published status document contains only a public handle, contest ids and names,
  statuses and messages.
- HTTP logging is `BASIC` and debug-only — never headers or bodies.
- The status feed URL must be `https://`; cleartext traffic is blocked by the platform
  default at `targetSdk 36`.
- `.gitignore` excludes `.env*`, keystores, `storage_state.json`, `cookies.*`, `auth.json`
  and similar, as a safety net behind the fact that no code reads a credential from a file.

**Rotate your Codeforces password if you ever suspect the repository secret leaked**, and
remember that anyone with write access to the repository can read secrets indirectly by
adding a workflow.

---

## Testing

| Suite | Count | How to run |
|---|---|---|
| Android unit tests | 92 | `./gradlew testDebugUnitTest` |
| Android instrumentation tests | 24 | `./gradlew connectedDebugAndroidTest` |
| Python automation tests | 78 | `cd automation && python -m pytest tests -q` |

No test reads the system clock. Every time-dependent test pins a fixed `Instant` (Kotlin)
or epoch second (Python), including the seven-day boundary, the null-start-time cases, the
countdown thresholds and the reminder arithmetic.

Coverage includes: contest parsing and classification, upcoming-window filtering and its
exact boundary, phase filtering, sorting, countdown formatting and ticker cadence,
registration eligibility, duplicate-registration prevention, notification scheduling
arithmetic, the Room DAOs, settings persistence and clamping, provider error
classification, repository partial-failure behaviour, status-document parsing and schema
versioning, CAPTCHA/2FA detection, secret redaction, and Compose rendering and
accessibility of the contest card.

The Python suite touches no network, starts no browser, and needs no credentials.

---

## Cost

The intent is zero recurring cost for personal use, but no service can be promised to stay
free.

**GitHub Actions**, as documented at the time of writing:

- Standard GitHub-hosted runners are **free for public repositories**.
- The **GitHub Free plan** includes **2,000 minutes/month** on standard runners for private
  repositories, plus 500 MB artifact storage.

Estimated usage with the default schedule:

| Workflow | Runs/month | Approx. minutes/run | Approx. total |
|---|---|---|---|
| `auto-register` | ~240 (every 3 h) | 2–4 (most end before launching a browser) | ~480–960 |
| `contest-check` | ~30 (daily) | ~1 | ~30 |

So roughly **500–1,000 minutes/month** on a private repository — inside the 2,000 free
minutes, but not comfortably. On a public repository the standard-runner usage is free.
If you want more headroom, widen the `auto-register` cron to every 6 hours.

Everything else is free: the Codeforces and CodeChef endpoints are public and
unauthenticated, and the app stores everything else on the device.

**Check the current limits yourself before relying on this** — GitHub has changed Actions
pricing before and may again.

### External accounts required

| Account | Needed for | Optional? |
|---|---|---|
| GitHub | Hosting the repository and running the automation | Required for auto-registration only |
| Codeforces | The account being registered | Required for auto-registration only |
| CodeChef | — | Not needed; contest data is read anonymously |

Contests, the UI, and notifications work with **no account at all**.

---

## Limitations

These are real constraints, documented rather than worked around:

1. **Codeforces has no registration API.** Registration is browser automation, which is
   inherently more fragile than an API and can break if Codeforces changes its markup.
2. **Registration status cannot be read from the official API.** It comes only from the
   automation's own report, so a contest ContestPilot has never processed shows
   "Unknown" — not "not registered".
3. **No rated flag exists.** Division rules are name-based. ContestPilot never claims a
   round is rated.
4. **CAPTCHA and 2FA stop the automation.** Deliberately. If Codeforces enables either for
   your account, auto-registration cannot proceed and you register manually.
5. **CodeChef's contest endpoint is unofficial** and may change or disappear.
6. **CodeChef auto-registration is not implemented.**
7. **Scheduled workflows are not punctual.** GitHub delays them under load and may drop
   queued runs. Registration windows are hours wide, so this is tolerable; the phone
   reminder does not depend on it.
8. **A public repository is effectively required** for the app to read the status feed
   without a token.
9. **Scheduled workflows in a public repository are disabled after 60 days without
   repository activity.** The daily `contest-check` commit is what keeps them alive.
10. **A private repository consumes free Actions minutes**, which may run out.
11. **Exact alarms can be denied** by the OS or restricted by aggressive battery
    management on some devices; the app falls back to inexact alarms and says so.
12. **`compileSdk` is 36.1**, so library versions are pinned to ones compatible with it
    (Compose BOM 2026.02.00, Navigation 2.9.8, Lifecycle 2.10.0). Moving to API 37 would
    allow newer versions.

---

## Troubleshooting

**No contests are shown.** Check that at least one platform is enabled in Settings, then
refresh. The panel at the top of the list shows each source's freshness and the reason for
any failure.

**The list shows "Showing cached data".** A source failed; the message names which one.
Cached contests keep working. Refresh when your connection returns.

**No reminder arrived.** In order: is *Contest reminders* on in Settings? Did you allow
notifications? Is *Alarms & reminders* allowed for ContestPilot in Android settings? Is
the contest more than the lead time away (a contest starting in 5 minutes gets no
10-minute reminder)? Is battery optimisation restricting the app?

**Registration status says "Unknown".** Either the status feed URL is not configured, or
no automation run has covered that contest yet.

**Registration status says "Blocked by Codeforces".** Codeforces asked for a CAPTCHA or a
second factor. Register manually; ContestPilot will not bypass it.

**The workflow fails with "Missing required secret(s)".** `CODEFORCES_HANDLE` or
`CODEFORCES_PASSWORD` is not set. Add them under Settings → Secrets and variables →
Actions.

**The workflow fails at sign-in with `INVALID_CREDENTIALS`.** The handle or password
secret is wrong. Re-enter both; a trailing space in the secret value is a common cause.

**The status branch does not exist.** Run `auto-register` manually once. The branch is
created on its first successful publish.

**Scheduled runs stopped.** If the repository has been inactive for 60 days, GitHub
disabled them. Re-enable under Actions.

**Gradle fails with "Using kotlin.sourceSets DSL is not allowed with built-in Kotlin".**
`android.disallowKotlinSourceSets=false` is missing from `gradle.properties`.

**Gradle fails with "requires ... compileSdk of at least 37".** A dependency was upgraded
past what `compileSdk 36.1` supports. Either install API 37 and raise `compileSdk`, or pin
the dependency back.
