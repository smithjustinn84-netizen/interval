# Backlog: what no sprint holds yet

Work that is out of every sprint for now, each with why and what would bring it in. A sprint that takes an item
moves it into its own tickets and deletes it here; a sprint's "Explicitly not this sprint" section links here
rather than copying the list forward. What is in a sprint is in that sprint's file (none is open; the last
was [sprint-0.6.md](archive/sprint-0.6.md)); finished sprints and reviews are in [archive/](archive/).

## Waiting on something

- **AppFunctions** ("start a Tabata", "log a set"). `androidx.appfunctions` was still alpha (1.0.0-alpha10) on
  Jetpack's last listing. *Brings it in:* a stable release. It would sit over the start routes the widgets and
  the launcher shortcuts already use.
- **iOS signing and an Apple team.** iOS builds and tests in CI, but nothing is signed or shipped to the App
  Store. *Brings it in:* an Apple Developer account, and a decision to ship on iOS.
- **A Live Activity that follows each phase on iOS.** With the app suspended its Live Activity can't learn of the
  next interval, so past the current one's end it shows only the whole workout's clock. *Brings it in:* the Apple
  team above, and a push service to update it at each change.
- **The Live Activity's stale view, checked on an iPhone.** Once the interval it counts ends with the app
  suspended, the activity should drop the phase and its clock for "back in the app" over the whole workout's
  (`staleDate` is the interval's end, `IntervalActivity.swift`). On the simulator (2026-11-17, the app stopped with
  SIGSTOP) it never did: the Lock Screen sat in always-on display with the backlight off, which only steps the
  timers, and kept "Work 0:00" until the app ran again and caught it up. *Brings it in:* a phone with the app on it
  (a personal team in Xcode is enough to install). Start a workout, lock, wait past the interval with the screen
  on, and look; if it still says "Work 0:00", the stale view needs another way in.
- **Screenshot tests and coverage.** The Kotlin Toolchain (0.13) supports neither ([testing.md](testing.md)).
  *Brings it in:* the toolchain supporting them.

## Planned, not yet scheduled

After the class recorded on a heart-rate sensor (2026-11-18: record, sync, rounds, on Android), what it left out:

- **The sensor on a real mat.** `PolarClassSensor` paired, recorded and synced a few minutes on a Verity Sense
  (2026-11-18, a Pixel 10 Pro: the heart rate a reading a second, the accelerometer recording, and a recording's
  start coming back in UTC, which that run found and fixed). Not yet a whole class: an hour and a half on the
  sensor's memory, the sync's length at that size, and the sensor out of reach of the phone all the while. Then
  tune `detectRounds`
  (`interval-domain`, `timer/Rounds.kt`) on what real classes look like, a recording or two kept as a fixture
  for `RoundsTest`. *Brings it in:* the sensor in hand.
- **A class on the H10.** `PolarClassSensor` takes an H10 too (2026-11-19), and on 2026-11-22 the R8 release
  build paired, streamed, reconnected, recorded and synced on a real H10 and a real Verity Sense. Not yet: the
  recording surviving the phone out of reach for a whole class, and rounds found by the heart rate alone on one.
  *Brings it in:* an H10 in hand.
- **Pairing and the pulse, on the real sensors** (2026-11-20: tried, both worked). Pairing took an H10 left at
  home in place of the Verity Sense on the arm, since Polar's search opens by naming every sensor the phone knows;
  it now takes only one heard advertising in the scan, and asks "Which sensor?" with two in reach. The paired
  sensor's heart rate beside the gear on every tab came with it. Both were run on the real sensors that day. Not
  said to be tried: the "Which sensor?" sheet with an armband and a strap both awake, the pulse's sheet, a low
  battery's dot, and "Rec" through a class.
- **Live heart rate over Android's own Bluetooth** (F1, 2026-11-22). Every sensor streams over GATT now
  (`AndroidHeartRateLink`), Polar's too. `journeys/live-heart-rate-strap.xml` was run by hand with real straps the
  same day, and iOS's (`live-heart-rate-strap-ios.xml`) too. Not said to be tried: Android 12's callbacks beside 13
  and up's, and a Verity paired by an older build found again by its name. *Brings it in:* an Android 12 phone.
- **The heart rate read, on a real strap** (2026-11-19). The trace under the ring, Lift's heart card and its
  rest's "ready", recovery and heart load on the summary and in the Log, the zones by heart-rate reserve, the
  sensor's battery, and heart load in readiness are all built against fakes. None has met a sensor: the battery
  (`FEATURE_BATTERY_INFO`, `batteryLevelReceived`, whether an H10 and a Verity Sense both say it on connecting),
  the trace over a real workout, and whether "ready" at zone 2 comes at a sensible moment between heavy sets.
  The constants are first readings to tune on real weeks: `heavyHeartDay`'s half again a usual day and its floor
  of 60, `Recovery`'s fading at three quarters, `USUAL_MAT_SECONDS`. *Brings it in:* a sensor in hand, then a few
  weeks of its days.
- **Live heart rate, still left.** A workout recorded before 2026-11-19 keeps samples that start at the sensor's first
  reading rather than its own start, so its recovery in the Log may be read a few seconds off. Recovery is read
  for Lift nowhere but live in the rest. Lift's zone change has no haptic, as the workout's has under Vibrate.
  No acceptance spec drives a workout or a session with a sensor: the fakes can (`FakeLiveHeartRate`).
- **Round tags and trends.** A partner's level, how the round went and the position it stuck in, a tap or two a
  round on the class's page, then the rounds over weeks by tag. Needs the rounds stored once tagged (today they
  are found afresh from the samples at each read, so a retuned detector would move the tags' rounds).
- **Composure: heart rate against movement.** Each sample keeps how much the arm moved, so a round can be read as
  a scramble, a grind or easy work without a second sync. Wants real classes first, to see what the armband's
  accelerometer tells apart.
- **A resting heart rate taken by the strap.** Settings takes the number typed; an H10 worn for a minute on
  waking could read it, and offer it as the weight sheet offers Health Connect's weight.
- **A morning reading into readiness.** Two minutes seated against a week's baseline, as a fourth reason beside
  `ReadinessReason`'s three (`readiness(...)` takes its inputs as parameters; `CoachLine` words each reason). An
  optical armband's beat-to-beat intervals are only good at rest and still; a chest strap's are the ones to trust.
- **A class's grip load by its rounds.** `GRAPPLING_GRIP_LOAD` weighs every BJJ day the same; a recorded class
  knows how long it was rolled for.
- **The class in Health Connect.** Written as a martial-arts session with its heart rate; `HealthWorkoutKind` has
  no such kind yet, and the write would add a heart-rate permission to Play's declaration.
- **A class recorded on iOS.** Polar's iOS SDK is Swift alone: a `swiftPackage` dependency of `iosApp` and a bridge
  handed into Kotlin as `startWidgetFeed`'s closure is, recording and syncing beside `StreamingSensor`'s live
  stream. Until then iOS streams any sensor and records no class on it.

### From the 2026-11-22 product review ([product-review.md](product-review.md))

Full findings, cleanup items, and the 3-phase `0.6.0 → 1.0.0` roadmap live in [product-review.md](product-review.md). Unscheduled items awaiting a sprint:

- **Clean up & dead weight (C1–C6).** Batch `ExportBundle.SCHEMA` bumps once per release (the rule is in CLAUDE.md's Storage and release.md's pre-flight from 2026-11-22; Room stays one bump a change); prune the `Presets` route (and its `subclass` in `navConfiguration`, its line in `NavKeyRoundTripTest`) and `timerUnderTrain`/`rootAtToday` only after a release past `v0.4.0`, the last build with the Timer as a tab, is tagged and installed (no tag holds them yet; `Timer`'s `@SerialName` stays, it is the name every build saves under); curate the built-in program catalogue.
- **Next-level `1.0` features (F4–F7; F1, F2 and F3 are done).** Unified Progress / Trends view in Log; superset / paired blocks in Lift; CSV export & gentle offline backup nudges; audio ducking & OEM battery-optimization check.

## Left out on purpose

- **Import atomicity across both databases (E5** in the [2026-10-31 review](archive/architecture-review.md)).
  The interval side's and Lift's Room databases each import in one transaction, but two databases can't share
  one; importing the same file again completes an interrupted second half without duplicating the first.
  *Brings it in:* a report of an import left half done that a second import didn't put right.
- **Localisation.** The built-in presets' and the shipped flows' names are stored and spoken data by design,
  and both text parsers work on English keywords. A second locale is a sprint,
  not a ticket. *Brings it in:* a locale worth the sprint.
- **Wear OS.** A platform project of its own. `HealthStore` (`core-platform`) already writes workouts and reads
  body weight and a connected watch's heart rate through Health Connect and Apple Health.
