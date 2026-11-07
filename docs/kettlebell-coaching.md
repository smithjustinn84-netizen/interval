# Kettlebell coaching: what Lift does for the lifter, and what it will

How the app coaches a kettlebell lifter: it picks the day's load and volume, guides the set, watches the grip,
progresses past fixed bell sizes, and gives benchmarks to chase. The analysis follows a coach and
product-manager brief written on 2026-11-07. Each section says what is built, what is planned and where it
lives.

The principles are the brief's:
- Hinge and squat are different movements.
- The grip needs its float.
- 15:15 and the Armor Building Complex (ABC) are the conditioning protocols.
- The trunk is trained by resisting movement, not by flexing.

## 1. The day's workout, adjusted to the lifter

**The movement of a lift.** Every built-in lift has a `Movement` (`lift-domain`, `Movement.kt`), with four parts:
- **pattern**: Hinge, Squat, Lunge, Press, Pull, Carry, GetUp, Core or Other. A swing is a hinge and a goblet squat is a squat; the app never treats one as the other.
- **tempo**: Ballistic (swings, cleans, snatches, high pulls, jerks), Grind (presses, squats, rows, get-ups) or Hold.
- **grip**: 0 to 3.
- **perRep**: how many lifts one logged rep of a complex holds. The ABC is 2 cleans, a press and 3 squats, so 6.

A custom lift's movement is worked out from its equipment and how it is measured.

**Readiness** (`Readiness.kt`): Fresh, Normal or Taxed. It is worked out from what is logged:
- the last session's effort;
- BJJ logged today or yesterday on top of a grip above the weeks before; the days come through `IntervalActivity` with their sport;
- the grip load.

A class that is planned but not yet logged doesn't count.

**On a Taxed day** the app offers an easier day (`Autoregulate.kt`). It is offered, never imposed:
- grinds go a bell down, or a pair down when two bells are used;
- an EMOM of ballistics loses about a quarter of its minutes (20 becomes 15, Dan John's "leave something in the tank");
- sets of ballistics lose a round;
- rest follows the tempo: grinds rest longer, ballistics and holds shorter;
- where a program has a day much lighter on the grip, it offers that day instead.

**Equipment.**
- A lift done with two bells steps up only to a weight the lifter owns a pair of (`GearStore.pairs`).
- Bells come in fixed sizes. Where the next one is a big jump, progression goes through density first (section 4).

## 2. Guidance and safety during the set

Lift's timers speak, never mid-rep:
- **Speech for Lift.** The speech engine is `Speaker` in `core-platform` (Android's text-to-speech, iOS's synthesizer, none on desktop), one per process. The timers hand typed lines (`Coaching`, through `LiftVoice`) to `SpokenCoaching`, which words them. Settings' Voice switch governs Lift as it does the interval side (`IntervalActivity.voice`).
- **Set-up cues.** At an EMOM minute's count-in, nine seconds before it (`EmomClock.countInsBetween`), Lift says one of the lift's three cues (`cues.xml`), the minute's own so they rotate. From the second minute on, it speaks only once the minute before has its set done, since until then the lifter may still be lifting. In the last ten seconds of rest (`RestClock.setUpBetween`) it says the next lift's set-up cue: the plan's next, or the Next set card's.
- **Fault cues.** A loaded kettlebell hinge has faults (`Fault` and `Exercise.faults`, from its `Movement`); a thrown one has more, and the snatch has one of its own. One is said as the rest after a set starts, or as an EMOM minute's set is done: at most once a set, a different one each set. The worst is shown under the set's figure ("Watch for · …"):
  - **Squatting the swing**: "Hips back, shins stay vertical."
  - **Death grip in the float**: "Loosen at the top, grip on the way down." Squeezing through the float wears the forearm flexors and tears calluses.
  - **Wrist flexion in the hinge**: "Knuckles to the floor, wrist straight." A straight line from elbow to handle spares the wrist extensors.
  - **Late hand insertion on the snatch**: "Punch through before it lands."
- **Safety lines (planned).** Before the first ballistic set at a new bell: "Groove five light first." On a Taxed day: "Stop a rep early."

## 3. Grip and fatigue

**Grip load** (`Grip.kt`): reps × the lifts in a rep × the lift's grip.
- A ballistic set weighs half again, for the catch and the float.
- A set with two bells weighs double.
- A hold counts its seconds.
- A BJJ day adds a fixed amount, about a hundred two-hand swings, for the gripping in the gi.

**Fatigue** compares this week's load against the four weeks' weekly average. Above 1.3 the grip is overreached. A first week never counts, since it has no average to compare against yet.

**Where it shows:**
- **Lift's Lately group**: the week's grip load, how much of it was ballistic, and the overreached warning.
- **The session summary**:
  - "Heavy grip day" when the session tops the month's daily average by half;
  - after a hundred snatches or three hundred one-hand swings, a line on hand care: file the calluses, chalk, relax at the float.

**When the grip is taxed**, the easier day above swaps grip-heavy ballistics for leg-dominant and low-grip work: goblet squats, get-ups, carries done short.

## 4. Progression by density, not only by weight

**Density** (`Density.kt`) is reps per minute across a lift's sets in a session. Each set's start is inferred from its time logged and the lift's pace.
- Sets logged in a batch afterwards give no density, since their inferred times overlap. So a pace record or a benchmark is always one the lifter actually did.

**The ladder past fixed bell sizes**, in this order:
1. reps up the range;
2. where the next bell is more than 20% heavier, rest shortens 15 s a time, down to 30 s for ballistics and 60 s for grinds; an EMOM adds reps a minute first;
3. more minutes or sets;
4. only then the next bell, with the reps and the rest back to where they start.

A block keeps its own rest (`Block.rest`) once it has stepped.

**Pace records** sit beside the heaviest, most reps and longest hold: "New best pace: 9.4 swings a minute at 24 kg."

**Planned:**
- A time-capped block (`BlockKind.Density`): as many good rounds as you can in 15–20 minutes, the ABC's second option.
- Counting snatches in 15:15. At the end of a 15:15 workout, the lifter gives about how many a set (7–9 is the target), so the interval side's 15:15 counts toward snatch totals and density.

## 5. Benchmarks and engagement

All of these are worked out from the logged sets, with nothing new stored (`Benchmarks.kt`).

**Challenges:**
- **10,000 Swings**: one- and two-hand swings counted over 28 days, or over the run while the program is followed.
- **Snatch test**: the most snatches in any five minutes of a session, against 100.
- **Armor Building Complex**: the most complexes in any twenty minutes, a personal best.
- **Bodyweight get-up**: the heaviest get-up as a share of the body weight set in Settings.

**Lifetime totals** for each lift appear on the lift's page, with milestones at 1,000, 5,000, 10,000 and 25,000 named on the summary.

**Where it shows:**
- A Challenges card under the program on Train's Strength pillar (`ChallengesCard`, `lift-today`), a bar for each challenge under way.
- The summary names a new snatch test or ABC best.

**The weekly streak** stays by the week, so a rest day never breaks it.
