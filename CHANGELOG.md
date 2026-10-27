# Changelog

Notable changes to Interval, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The version is `versionName` in `androidApp/module.yaml`; the iOS marketing version must match, and
`tools/check_versions.py` fails CI when the two drift apart.

## [0.2.0] — Unreleased

### Added
- A signed release bundle: `./kotlin package -m androidApp` signs with `androidApp/keystore.properties`,
  and pushing a `v*` tag builds and attaches the AAB in CI.
- This changelog, and a CI check that Android and iOS carry the same version.
- Backup rules: Android's backup and device-to-device transfer carry all of the app's data.

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
