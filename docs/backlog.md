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
- **Screenshot tests and coverage.** The Kotlin Toolchain (0.13) supports neither ([testing.md](testing.md)).
  *Brings it in:* the toolchain supporting them.

## Planned, not yet scheduled


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
