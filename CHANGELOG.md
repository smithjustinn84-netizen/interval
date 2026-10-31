# Changelog

Notable changes to Interval, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The version is `versionName` in `androidApp/module.yaml`; the iOS marketing version must match, and
`tools/check_versions.py` fails CI when the two drift apart.

## [0.3.0] — Unreleased

### Added
- Live Updates (Android 16 and later): a running workout and a rest between sets show as a chip with their
  clock in the status bar, and as a bar on the lock screen and in the shade. A workout of up to ten intervals
  shows each in its phase's colour with a mark where each round begins; a longer one, one bar in the phase's
  colour. Pause, Resume and End still work from the notification.
- App shortcuts (Android): a long-press on the icon offers "Start a workout", the Timer's workout, and "Start a
  Lift session", which starts or resumes one. Each opens over its tab, so Back lands there, and a workout
  already running is kept: either shortcut returns to it.

## [0.2.0] — Unreleased

### Added
- A signed release bundle: `./kotlin package -m androidApp` signs with `androidApp/keystore.properties`,
  and pushing a `v*` tag builds and attaches the AAB in CI.
- This changelog, and a CI check that Android and iOS carry the same version.
- Backup rules: Android's backup and device-to-device transfer carry all of the app's data.
- Export: Settings > Your data > Export offers one JSON file of your workouts, presets, plan, settings and
  Lift's sessions, programs, exercises and gear (schema 2) to share or save.
- Share: a finished workout (its summary and its page in History) and a finished Lift session (its summary
  and its page) can be sent in words through the system share sheet.
- Accessibility: section titles, dialog titles, sheet titles and the summaries' headings are headings for
  screen readers, so TalkBack and VoiceOver can jump between sections.
- Lift: a rest between sets is heard with the screen off. Android keeps it counting in a notification and
  beeps as it ends; iOS shows a notification when the rest is over (once notifications are allowed).
- Lift: an exercise's page charts your best each session, the weight, reps or hold as the exercise counts,
  once it has been done twice.
- Health Connect (Android): connect it under Settings > Your data, and each workout and Lift session you
  finish is written there as an exercise session, with its intervals, rests, pauses or sets and its calorie
  estimate. The app writes only, and reads nothing. The privacy policy is under About, and is what Health
  Connect opens when asked.
- Plan: a day can hold a Lift session, the next day of the program followed or any session, beside interval
  workouts. A session finished on Lift that day does it, so it counts in "Planned done", in reminders and in
  Up next, which starts the program's day it comes to. The Plan page shows the program, how many of its days
  a week the routine covers, and each lift day's program day; the add sheet offers a one-tap spread of the
  program's days. History's week splits each day's bar by kind, and "kettlebells mon wed fri" describes a
  routine of lifts.

## [0.1.0] — 2026-10-26

The first build: Android, iOS and desktop from one Compose Multiplatform codebase.

### Timer
- Work, rest and rounds with steppers and a value sheet, the total, a calorie estimate and an activity picker.
- Describe a workout in words ("40/20 x8 on the rower", "tabata").
- Presets: four built-ins (Tabata, HIIT 30/30, Hangboard, Long efforts) and your own, drawn to scale,
  with edit, copy, and delete with undo.
- The workout: tap anywhere to pause; Back, Skip, +10s and End; sound, voice, halfway and vibration cues.
  It keeps counting while the app is off screen: a foreground service with Pause and End in its
  notification on Android, a notification per phase change on iOS.
- A summary with time, rounds, calories, the weekly goal and the streak; Repeat and Save as preset.

### History and Plan
- Week, month and all-time summaries: a goal ring, streaks, a 12-week chart, and a log by day that
  counts Lift sessions too.
- Plan: a weekly goal, a routine by weekday, one-off workouts, routines in words ("tabata mon wed fri"),
  and a daily reminder that survives reboots.

### Lift
- The Lift tab: the week, the followed program's next day, the last session, and your equipment
  (bells, bar, bands, records).
- About 100 built-in exercises with cues and muscles, your own exercises, a library with search and
  filters, and each exercise's history and best.
- Sessions: sets, EMOMs and holds, a rest timer, edits with undo, today's plan with skips, and a
  summary with new records and load suggestions.
- 29 built-in programs in six families, a program editor (sets, EMOM and ladder blocks, rep ranges),
  and program progress.

### Settings
- Cues, keep the screen on, get-ready seconds, System/Light/Dark, and body weight and effort for calories.
