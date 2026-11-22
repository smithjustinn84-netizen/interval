# ADR-0001: One Compose Multiplatform codebase, UI included, for Android, iOS and desktop

**Status:** Accepted (written down after the fact, 2026-11-19)
**Date:** 2026-11-19
**Deciders:** Justin Smith

This record writes down choices the code already embodies. Where the repository records why a choice was
made (a sprint plan, the [architecture review](../archive/architecture-review.md),
a backlog entry), that reason is quoted. Where it records none, the reason given is **inferred** from the code
and marked so. The alternatives are the ones a reader would weigh today. Nothing says they were costed when the
choice was made.

## Context

Interval is an offline training companion with no backend and no account. It has two sides: an interval timer
and Lift, the kettlebell side. On top of those sit a weekly plan, a log, mobility flows and heart-rate sensors.
It has to:

- run a timed workout to the second with the screen off, on Android and iOS: cues, notifications, a Live
  Activity, a foreground service;
- keep everything on the device, exportable as versioned JSON;
- reach the platform's own surfaces: Health Connect and HealthKit, home-screen widgets, shortcuts and quick
  actions, Bluetooth sensors;
- be built and maintained by one developer working with an agent. A feature should be written once. Tests
  should run on the JVM in seconds.

The constraints are one person's time, a growing feature set (two sides, 35 modules) and a toolchain that is
still young.

**Where the code stands** (lines of Kotlin, excluding tests, 2026-11-19):

| Source set | Lines | Share |
|---|---:|---:|
| `src/` (common) | 72,865 | 93% |
| `src@android/` | 2,449 | 3% |
| `src@ios/` | 1,523 | 2% |
| `src@jvm/` (desktop) | 594 | 1% |
| Swift (`apps/iosApp`) | 852 | 1% |

Only 16 `expect` declarations exist, in 5 files. Every other platform difference is an interface bound by Koin.

## Decision

Write the whole app in Kotlin Multiplatform, **screens included**, with Compose Multiplatform. The three hosts
(`androidApp`, `iosApp`, `desktopApp`) stay thin around one `app` module. Platform code sits behind injected
ports (`*-platform-api`), implemented in `src@<platform>/`. Swift is kept to what only Swift can do: WidgetKit,
the Live Activity and its App Intents.

Below that, the stack is:

| Concern | Choice | Instead of | Why |
|---|---|---|---|
| Build | Kotlin Toolchain 0.14 (Amper), `module.yaml` + templates | Gradle with KMP plugins | Short declarative modules. Each module is its own unit of compile, KSP, lint and test, rerun only when it changes. *Inferred.* |
| UI | Compose Multiplatform 1.13 (rc), Material3 under the app's own tokens; headless `composeunstyled` for dialogs, sheets, text fields and switches | SwiftUI on iOS; stock Material | One UI to write and preview. The headless parts let sheets and dialogs look the same on every platform, drawn from `Tokens.kt`. *Inferred.* |
| Navigation | Navigation 3, one back stack of `@Serializable` keys | Decompose, Voyager, Navigation 2 | The back stack is plain state the app owns (`BackStack.kt`), saved and repaired across builds. Jetpack-backed, with a multiplatform UI from JetBrains. *Inferred.* |
| DI | Koin DSL, no KSP; **one container per process** | Hilt (Android-only), kotlin-inject or Metro (codegen) | Runs on every target with no code generation. The per-process lifetime is recorded: the [architecture sprint](../archive/sprint-architecture.md) moved it off the Activity (root cause A), so services and receivers can reach it. `VerifyAppModulesTest` gives back the graph check that codegen would give. |
| Relational storage | Room Multiplatform + bundled SQLite, one database per side | SQLDelight | Room's migrations and exported `schemas/` are already the Android norm, and the bundled driver gives the same SQLite everywhere. *Inferred.* |
| Settings | DataStore Preferences, held in memory and written through | `multiplatform-settings`, `NSUserDefaults` | Same reasoning as Room: a Jetpack store that is multiplatform. *Inferred.* |
| Platform seams | Interfaces in `*-platform-api`, bound per platform in Koin; `expect`/`actual` only for composable hooks and constants | `expect class` per service | Each port gets a recording fake, so a ViewModel test needs no platform. CLAUDE.md states this as the rule. |
| Tests | `kotlin.test` + hand-written fakes in `*-testing` modules; Compose UI tests in `test@jvm/`; acceptance specs | Mockk, Robolectric, `androidTest` | Common tests run on JVM, Android and iOS. Mockk is JVM-only. Robolectric and `androidTest` are not supported by the toolchain ([testing.md](../testing.md)). |
| iOS-only surfaces | Swift for the widget, Live Activity and App Intents, fed JSON by `WidgetFeed.kt` | Kotlin-only iOS | WidgetKit and ActivityKit have no Kotlin route. The toolchain allows one Kotlin target in the Xcode project, so the widget target is Swift-only. |

## Options considered

### Option A: Compose Multiplatform for everything (chosen)

| Dimension | Assessment |
|---|---|
| Complexity | Medium. One codebase, but with a young toolchain and Kotlin/Native's build quirks |
| Cost | Lowest per feature: a screen is written once and previewed on the JVM |
| Scalability | Good. 35 modules, with layering enforced in CI by `check_module_rules.py` |
| Team familiarity | High for an Android developer. Swift only at the edges |

**Pros**
- About 93% of the code is shared, tests included.
- Most of the loop is the JVM: hot reload on desktop, `@Preview`s rendered to PNG, and acceptance specs.
- The two sides share one design system.

**Cons**
- The UI on iOS is not native. iOS keeps its own page slide (`platformPageTransitions`), but controls,
  scrolling feel and accessibility are Compose's.
- iOS links take minutes.
- Some failures appear only on Native, such as opt-in leaks and a stale incremental cache.

### Option B: KMP for logic, native UI (Compose on Android, SwiftUI on iOS)

| Dimension | Assessment |
|---|---|
| Complexity | High. Two UIs, plus a Swift-facing API for every ViewModel's state (SKIE or hand-written wrappers) |
| Cost | Roughly doubles the screen work. About three quarters of `src/` (76%) is in the modules that draw: features, `*-ui`, `core`, `app` |
| Scalability | Fine technically. Limited by one person's time |
| Team familiarity | Lower: SwiftUI, and Swift interop with coroutines and Flow |

**Pros**
- The iOS UI is fully native.
- No Compose-on-iOS rendering risk.

**Cons**
- Each feature is built twice.
- Previews and UI tests split by platform.
- The acceptance specs could cover only one platform's UI.

### Option C: Android-only Compose, a separate iOS app (or none)

| Dimension | Assessment |
|---|---|
| Complexity | Low on Android |
| Cost | Lowest if iOS never ships. Highest if it does |
| Scalability | Poor across platforms |
| Team familiarity | Highest |

**Pros**
- The full Android toolchain: AGP, Robolectric, screenshot tests, coverage.
- No Native builds.

**Cons**
- iOS would be a second product or nothing.
- Desktop, which the hot-reload loop relies on, goes away.

### Option D: A non-Kotlin cross-platform framework (Flutter, React Native)

| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Cost | A rewrite, plus a bridge layer for every platform service |
| Scalability | Good |
| Team familiarity | Low |

**Pros**
- Mature tooling for both phones.

**Cons**
- Health Connect, the foreground service, Glance, Polar's SDK and HealthKit would each need plugins or a
  bridge.
- The pure Kotlin domains (`interval-domain`, `lift-domain`) could not be kept.

## Trade-off analysis

The deciding force is **one developer and two sides**. Only Options A and C keep a feature to one
implementation, and only A keeps iOS and desktop. Option A gives up a native iOS UI and the mature Android
toolchain in exchange.

- **Native feel vs one UI.** Accepted for now. iOS is not shipped (no Apple team, [backlog](../backlog.md)),
  so no App Store user has judged it yet. The surfaces where iOS users expect native behaviour (the widget,
  the Live Activity, quick actions, HealthKit's sheet) already are native.
- **The Kotlin Toolchain vs Gradle.** The toolchain gives short builds per module and readable configuration.
  It costs Robolectric, screenshot tests, coverage and instrumented tests, which `testing.md` records as
  missing under 0.13. The wrapper is now on 0.14, so that list should be checked again. The project makes up
  for the gaps with JVM Compose UI tests, acceptance specs, `PreviewRenderTest` and on-device `journeys/`.
  Coverage has no replacement.
- **Koin vs compile-time DI.** Koin needs no KSP on five targets, but a missing binding fails at runtime.
  `VerifyAppModulesTest` turns that back into a test failure. The trade holds as long as that test is kept
  honest about route parameters (`injectedParameters`).
- **Room vs SQLDelight.** Room brings KSP, and with it `-Xexpect-actual-classes` and a pinned
  `sqlite-bundled`. That cost is contained by putting each database in its own data module, so a UI edit never
  reruns it. Two databases cannot share a transaction, which costs import atomicity (E5, left out on purpose
  in the backlog).

## Consequences

**Easier**
- A feature is one ViewModel, one stateless screen and one test suite, and runs on three platforms.
- The design system, the motion tokens and the coaching domain are shared by both sides and all three
  platforms.
- Iterating happens on the JVM: hot reload, preview PNGs, Compose UI tests, and ~12 s for one class's tests.
- Platform services are swappable. Every port has a recording fake, so no test loads a real platform module.

**Harder**
- **iOS build cost.** Linking takes minutes, so `./kotlin build` runs once per piece of work, and CI tiers
  the iOS test shards (`tools/ios-test-shards.txt`). A native-only failure in a feature's common tests can
  surface a night late.
- **Native-only traps** that `-p jvm` won't catch:
  - module-wide `optIns` leak into dependencies' Native cache and fail the link;
  - moving files can leave the incremental cache stale;
  - `compose-stability.conf` is not applied on Native, so iOS compares the domain types by instance (hence
    `keptFrom`).
- **Version friction.** Compose runs ahead of the toolchain's 1.12.1, so Material3 and the nav suite are
  catalog entries bumped by hand with `compose.version`. Navigation 3's runtime (Google) and UI (JetBrains)
  ship on different cadences. detekt 2.0 is still alpha and pinned to compose-rules.
- **Swift-only SDKs.** Polar's iOS SDK is Swift-only, so iOS has no class sensor (`NoClassSensor`) until a
  Swift bridge is handed into Kotlin.
- **No coverage, no screenshot goldens.** Both wait on the toolchain.

**To revisit**
- When iOS is signed and shipped: whether Compose's iOS feel holds up with real users, screen by screen.
  The fallback is SwiftUI for a screen or two behind the same ViewModel, not a rewrite.
- With each toolchain release: whether coverage, screenshot tests or Robolectric have become possible, and
  update `testing.md`'s table, which still says 0.13.
- If Koin's runtime graph ever lets a bug past `VerifyAppModulesTest`: Metro or kotlin-inject.

## Action items

1. [ ] Recheck `testing.md`'s "Not available under the Kotlin Toolchain" table against 0.14, and correct its
   version.
2. [ ] The iOS class sensor: the Swift bridge over Polar's SDK described in the backlog ("The sensor on iOS").
3. [ ] iOS signing and an Apple team, which come before any judgment of Compose on iOS (backlog: "iOS signing
   and an Apple team").
4. [ ] Screenshot tests and coverage, once the toolchain supports them (backlog).
5. [ ] Bump `navigation3-ui` to match the runtime once JetBrains ships 1.2.1 (`libs.versions.toml`).

## Update, 2026-11-22

The desktop stays a target, for the JVM loop this decision leans on (hot reload, previews, the acceptance specs),
but no longer a product: the 2026-11-22 product review's C4 took its Option A, so it is the developer harness, not
offered to users, and a platform feature needs no desktop actual beyond a no-op.
