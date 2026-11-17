# Backlog: what no sprint holds yet

Work that is out of every sprint for now, each with why and what would bring it in. A sprint that takes an item
moves it into its own tickets and deletes it here; a sprint's "Explicitly not this sprint" section links here
rather than copying the list forward. What is in a sprint is in that sprint's file (the current one is
[sprint-0.6.md](sprint-0.6.md)); finished sprints and reviews are in [archive/](archive/).

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

Nothing at present: the last item here, Pause and End on iOS's Live Activity, landed on 2026-11-16.

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
