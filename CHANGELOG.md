# Changelog

Notable changes to Interval, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The version is `versionName` in `androidApp/module.yaml`; the iOS marketing version must match, and
`tools/check_versions.py` fails CI when the two drift apart.

## [0.5.0] — Unreleased

### Added
- Today, a new first tab and where the app opens: this week against the goal, what is up next (today's plan, or
  the next day of the program followed) under one Start, and "Or pick by time", which offers the presets that fit
  5, 10, 20 or 30 minutes, each with a Start of its own. With nothing planned it leads to the plan and to Lift's
  programs. The Timer, Lift and History stay tabs beside it, and Back from any of them returns to Today.
- About how long a program's day takes: "about 20 min" on Lift's program card, on each day of a program's sheet,
  on every program in the list ("about 12–15 min" where its days differ) and on Today's Up next. It is
  estimated from the day's sets and reps, each lift's pace, the minutes of anything on the minute and the rest
  between sets set on Lift, so a longer rest makes a longer day; a day that is all on the minute says its
  minutes exactly, any other is rounded to five.
- Sequences: a workout of blocks run one after another, each block a shape of its own (work, rest, rounds, sets
  and ladder) with a rest between blocks. The workout says "Block 2 of 3 · Round 1 of 8", "Rest between blocks"
  and "Next: block 2 of 3 · 0:40", the voice "Rest before block 2" and "Block 2. Work", and the notification the
  block too; a preset of blocks reads "20s on · 10s off · ×8, then 1m on · 30s off · ×5 · 2m between blocks".
  Exports are schema 5, which a 0.4 build refuses with "update the app"; older files read as one block.
- Building a sequence: "Add a block" on the Timer's Sets sheet, or in the preset editor, adds a block like the last;
  each block is a card of its own rows that moves up or down or goes, and "Between blocks" sets the rest between
  them. A sequence on the Timer shows its blocks on one card, which opens the editor ("Edit blocks"); Done puts it
  back on the Timer without saving a preset, and "Save as preset" names it. The Timeline draws every block.

## [0.4.0] — 2026-11-05

### Added
- Settings from every tab: History and Lift have the gear the Timer has. Opened from them it is a page of its own;
  on a wide window it still sits beside the Timer when opened from there.
- Settings shows the plan's weekly goal and reminder as they stand, under "Plan"; either row opens Plan, where they
  are changed.
- Loaded carries and kettlebell sport lifts in Lift: Farmer's walk (always two bells), Suitcase carry, Rack carry and
  Bottoms-up carry, timed as holds with their bell ("24 kg · 0:30 · Left", "2 × 24 kg · 0:45") and setting heaviest
  and longest records ("Heaviest 24 kg · 0:30 · longest 1:00 at 16 kg"); Half snatch and kettlebell Push press (with
  the barbell's now named "Barbell push press"); and the 18, 22, 26 and 30 kg competition bells in the bell rack.
- Lift: "One-stop shop" under Dan John, pairing 125 two-hand swings with press, row, deadlift, goblet squat and
  suitcase carry three days a week for six weeks.
- "15:15 MVO2" among the Timer's presets: 15s on and 15s off for 40 rounds as a Kettlebell workout, after Kenneth Jay's
  snatch protocol, and read by Describe ("15:15 mvo2").
- Sets of rounds, with a longer rest between them: a "Sets" chip under the Timer's rows, whose sheet takes the rest
  between them once there is more than one (the chip then reads "4 · 1m"), and rows for both in the editor
  ("20s on · 10s off · ×8 · 4 sets · 1m between"). The workout says
  "Set 2 of 4 · Round 3 of 8" on screen and in the notification, shows the rest between sets in a colour of its own
  and says "Rest between sets" and "Set 2. Work"; Health Connect counts it as rest. Describe reads
  "4 x (8 x 20/10), 1:00 between" and "4 sets of 8 rounds 20/10" (one count alone, "8 sets of 20/10", is still
  rounds). Exports are schema 4, which a 0.3 build refuses with "update the app"; older files read as one set.
- Ladders, whose work climbs or falls a step each round, starting over with each set: set on the Timer in Work's
  sheet, under its quick picks, where a bar and "Rounds work 0:20 · 0:30 · … · 1:00" show what it makes of the
  rounds, and said on the Work row ("Work · +10s a round"); a "Ladder" row in the editor
  ("20s on, +10s a round · 10s off · ×5", named "20→60/10 ×5"). The workout's clock is
  each round's own length, and "Next: round 2 of 5 · 0:30" says how long the coming one is. A ladder that would fall
  below five seconds falls less steeply instead. Describe reads "ladder 20 to 60 by 10", "20/10 climbing 10 a round"
  and "60 down to 20 by 10, 10 off".
- The Home Screen's quick actions on iOS, as Android's launcher shortcuts: a long-press on the icon offers "Start a
  workout" and "Start a Lift session", from a cold start or with the app running, and a workout already running is
  kept.
- The home-screen widget's preview in Android's widget picker, drawn over a sample week (Android 15 and up).

### Changed
- Settings is regrouped: the health store has a "Health" group of its own, after the calories its weight feeds,
  instead of a row under "Your data"; Effort and Theme sit in cards under their names, which also name their
  options for a screen reader; the body weight shows in a pill as Get ready does; and About is a row at the foot.
- Lift's Dan John programs and kettlebell cues: Armor Building Formula alternates the 20-minute ABC EMOM and five
  double-bell press ladders of 2-3-5-10 across eight weeks; Easy Strength finishes with a suitcase carry; 10,000 Swings
  rotates press, goblet squat, chin-up and bench dip across four days; and the swing, clean, press, snatch, get-up and
  windmill cues tighten the hinge, J-hook, crush grip and vertical forearm.
- The Timer's chips are one quiet row under Work, Rest and Rounds: the sets, the warm-up and the cool-down, each a
  small name over its value, with no wash, border or stripe, so the three rows keep the colour and the height.

### Fixed
- The home-screen widget in the Play build stayed on its loading spinner and never drew the week: the release's minifier stripped a part WorkManager makes for it by name. A rule keeps it.
- A Lift session that Android brings back after the app was closed on its summary returns to the Lift tab,
  rather than opening a new, empty session.
- A program's block planned with a band you added shows that band's name on the Programs list and the Lift tab,
  not "Band".
- The Timer's Work, Rest and Rounds values were cut off on a phone once the chips under them took three rows. The
  rows now share the height only where each has room for its value, and scroll where they don't.

## [0.3.0] — 2026-11-04

### Added
- A home-screen widget on Android: this week's workouts, lifts and intervals alike, as the ring against the
  weekly goal set on Plan (or the count alone with no goal), the streak under it, and Workout and Lift buttons
  that start one as the launcher's shortcuts do, keeping a workout already running. It follows History as a
  workout or session is finished or the goal moves, and turns with the week on its own.

### Changed
- Lift's house programs build reps before weight: their bell and band lifts (Press ladders, Clean and press EMOM,
  Strength for the mat, Round conditioning, Grip and pull, Shoulder armor, Kettlebell foundations, Full body for
  beginners, Bells and bodyweight, Bands and bells) each climb a few reps from where they start, then take the
  next bell or band back at the bottom, rather than a bell a size up after one day done in full. An EMOM among
  them keeps its minutes. Their days ask for what they always did; the programs after a book are as written.
- Four house programs gain the work a week of them left out: push-ups in Full body for beginners and Kettlebell
  foundations, band chest presses in Bands and bells, and ten minutes of inverted rows on the minute in Bodyweight
  EMOM's second day. Following one, its days done count against the program as it now is.
- A save that fails (a full disk, say) now says so, "Couldn't save. Try again.", rather than passing unnoticed.
- History and Plan open at once and fill in as they are worked out, rather than holding the tap until then, and
  a change on the Timer no longer works either out again.
- Opening the app again after backing out of it (Android) no longer reopens its databases and settings, and a
  change made just before leaving is no longer at risk: what the app keeps now lives as long as its process.

### Added
- Lift: "15-minute kettlebell plan" under Classics, alternating a three-set strength day of double front squats,
  clean and presses and rows with a 15-minute EMOM day of two-hand swings and double thrusters, three days a week
  for six weeks, along with Clean and press and Thruster in the exercise library.
- Apple Health (iOS): Settings connects it as Health Connect is on Android. Each workout and Lift session you
  finish is saved there as a workout, with its intervals or sets, pauses, calorie estimate and how hard it was;
  the weight sheet offers your latest weight from it, and a summary shows a watch's heart rate. Turned off in
  Settings, nothing more is written or read.
- How hard a program day felt reaches the program: said to be Very hard or All out on its summary, the day's
  steps up for next time wait, switched off, until you turn one on; the program's page shows each day done with
  how it felt; and Health Connect names a program's session by the program and day ("Simple & Sinister · day
  12"), the session keeping its own title here.
- Make it yours: a built-in program's sheet opens a copy of it in the program editor, "(yours)", to change any of
  it (ranges, holds, bands, two bells, its days and weeks). Saved while you follow the built-in, your copy carries
  on from the day you were at.
- A program's sheet shows what a week of it works: the Lift tab's balance of sets per muscle group, counted from
  the days it plans, with the groups it leaves out named under the bars.
- Live Updates (Android 16 and later): a running workout and a rest between sets show as a chip with their
  clock in the status bar, and as a bar on the lock screen and in the shade. A workout of up to ten intervals
  shows each in its phase's colour with a mark where each round begins; a longer one, one bar in the phase's
  colour. Pause, Resume and End still work from the notification.
- App shortcuts (Android): a long-press on the icon offers "Start a workout", the Timer's workout, and "Start a
  Lift session", which starts or resumes one. Each opens over its tab, so Back lands there, and a workout
  already running is kept: either shortcut returns to it.
- Import: Settings > Your data > Import reads back a file Export wrote, on this phone or another, from 0.2 on. A
  sheet says when it was exported and what in it is new, and imports only that: nothing here is changed or
  removed, and importing the same file again adds nothing. Settings come too only with "Also settings" on. A
  damaged file, another app's, or one from a newer Interval is refused and changes nothing.
- How was it: a finished workout's and a Lift session's summary ask how hard it felt (Easy, Steady, Hard, Very
  hard, All out) and take a one-line note, kept as you choose and type. History's log and a workout's page,
  and a session's page on Lift, show them; sharing adds the note; the export carries both (schema 3); and
  Health Connect gets them as the session's notes when the summary closes.
- Warm-up and cool-down: an easier stretch before the first round and after the last, set under the Timer's
  rows or in the preset editor (off by default), or said in words ("5 min warm-up, 40/20 x8, 3 min cool
  down"). They are part of a preset's shape, so presets, History, Plan and the export keep them, and they count
  in the total, the timeline, the calories (as recovery) and Health Connect. The workout says each, cues and
  notifies it, and ending in the cool-down keeps the workout as finished.
- Your weight from Health Connect (Android): with Health Connect connected, Settings' weight sheet offers the
  latest weight a scale or another app recorded there in the last 30 days ("Use Health Connect's: 82 kg"), in
  your unit. It is taken only when you tap it, never on its own, and the sheet then says where it came from.
  Health Connect's sheet now also asks to read your weight; if you connected before this version, turn Health
  Connect off and on in Settings to be asked.
- Your heart rate from a watch (Android): with Health Connect connected, a finished workout's or Lift session's
  summary shows its average and peak heart rate when a watch or another app recorded it there, and so do its pages
  in History and on Lift. A watch that syncs later shows on the page the next time it opens; the heart rate is
  then kept with the workout and in the export. Health Connect's sheet now also asks to read your heart rate; if
  you connected before this version, turn Health Connect off and on in Settings to be asked.
- A Lift session asks to show notifications as its first rest starts, so a rest's end reaches you with the
  screen off even if you have never started a timed workout or a reminder. On iOS a plan reminder due while
  the app is open now shows as a banner; the workout's and the rest's stay silent there, as the app beeps.
- Balance on the Lift tab: once two sessions are in the last four weeks, a card counts their sets per muscle
  group (chest, shoulders, back, arms, core, glutes and hamstrings, quads and calves), what worked each most and,
  lighter, what only assisted it, and names any group nothing trained.

### Fixed
- Turning Health Connect off in Settings takes effect at once: Android gives the permissions back only as the
  app's process ends, and until then the switch stayed on and finished workouts were still written.
- Lift: what a session's summary says of it (how hard it felt, a note) reaches Health Connect a second after it is
  said, without waiting for the summary to close.
- Lift: deleting a past session, or importing, while a session is open now shows the open session's records,
  suggested loads and "last time" as they stand, rather than as they were when it opened.
- Lift: the session's screen stays smooth as a long history grows: a tap on the Log sheet no longer works out every
  record and recent lift again.
- Lift: a rest, an EMOM or a hold comes back where the clock says it is when the phone closes the app in the
  background to free memory, rather than being lost, as the session is opened again; an EMOM keeps its minutes
  done. One that ended meanwhile is over, with nothing logged or beeped for it.
- Lift (Android): an EMOM or a timed hold keeps counting and beeping with the screen off, as a rest does, and shows
  in the status bar and on the lock screen ("EMOM · minute 2 of 10"); starting one over a rest no longer lets the
  phone sleep. On iOS a notification says when it is done. Lift's timers no longer bend when the clock changes,
  and leaving a session ends its rest.
- Lift: a change to today's plan made straight after logging a set, or a held + on the next set's load, is no
  longer sometimes lost; each change builds on the last.
- Lift: finishing or discarding a session, deleting a program and removing a band each happen whole or not at
  all, so a crash part way leaves nothing half done.
- Lift (Android): rests one after another keep the phone awake to the end of each, however long the session; the
  beeps no longer stop with the screen off fifteen minutes in.
- Import: a file that can't be read now says so, rather than leaving Import stuck on "Reading" until the app is
  restarted.
- A workout's setting or a saved preset, workout or plan damaged on disk now reads as the nearest valid value,
  rather than showing a negative length or leaving the rest of its list empty.
- Plan reminders (Android) keep coming without the app being opened: each one shown sets the next two weeks', where
  before they stopped two weeks after the app was last used. A reminder now says what the plan holds as it shows,
  in the app's current language, and none comes for a day whose workout was already done.
- A workout (Android) keeps running when the system closes the app's screen in the background, and is there to come
  back to from its notification; before, it ended with the screen.

## [0.2.0] — 2026-11-04

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
