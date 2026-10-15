# interval — Claude Code Guide

Compose Multiplatform app for **Android, iOS and desktop (JVM)**, built with the **Kotlin Toolchain**
(`./kotlin`, Amper-based, version pinned in the `kotlin` wrapper). Docs: https://kotlin-toolchain.org/0.13/

> **NO GRADLE.** There is no `gradlew`, `build.gradle.kts` or `settings.gradle.kts`. Never run or suggest
> Gradle commands. Build configuration lives in `project.yaml` and each module's `module.yaml`.

## Commands

```sh
./kotlin build                      # Everything. The only build that links iOS. Run before calling work done
./kotlin build -p jvm               # Fast compile loop (common + JVM)
./kotlin test -p jvm                # Fast test loop
./kotlin check lint                 # ktlint over every module
./kotlin do format                  # ktlint auto-format
./kotlin check                      # All checks (tests + lint)
./kotlin run -m desktopApp          # Desktop window (with Compose Hot Reload)
./kotlin package -m androidApp -f aab   # Release bundle (R8 on; rules in androidApp/proguard-rules.pro).
                                        # Unsigned until a keystore is set up (`./kotlin tool generate-keystore`)
./kotlin show modules | settings | dependencies | tasks
```

`-m` takes the **leaf module name** (`shared`, `androidApp`, `iosApp`, `desktopApp`, `lint-plugin`).

### Android (android CLI)
```sh
./kotlin build -m androidApp
android emulator start amper-37
android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
android layout -p                   # UI tree as JSON, faster than a screenshot
android screen capture -o shot.png
android docs search "<topic>"       # Official Android docs
```

### iOS
```sh
./kotlin build -m iosApp -p iosSimulatorArm64
xcrun simctl install booted build/tasks/_iosApp_buildIosAppIosSimulatorArm64Debug/bin/Debug-iphonesimulator/interval.app
xcrun simctl launch booted dev.justinsmith.interval
```

## Layout

| Module        | Product          | Role                                                    |
|---------------|------------------|---------------------------------------------------------|
| `shared`      | `kmp/lib`        | All UI and logic: Compose, Nav3, ViewModels, Koin       |
| `androidApp`  | `android/app`    | `MainActivity` (splash + edge-to-edge) hosting `App()`  |
| `iosApp`      | `ios/app`        | SwiftUI shell + `ViewController()` hosting `App()`      |
| `desktopApp`  | `jvm/app`        | `main.kt` window hosting `App()`                        |
| `lint-plugin` | `jvm/amper-plugin` | ktlint `lint` check and `format` command              |

Amper source layout, not Gradle's:
- `<module>/src/` common code; `src@android/`, `src@ios/` (both iOS targets), `src@jvm/` platform code
- `<module>/test/` common tests; `test@jvm/` etc. for platform tests
- `androidApp/res/` Android resources; `shared/composeResources/` multiplatform resources (`Res.*`)

**Never create `src/commonMain/kotlin/`.** Amper ignores it silently.

Versions live in `libs.versions.toml` (`$libs.a.b.c` = alias `a-b-c`). Compose versions come from the
toolchain (`$compose.*`).

## Conventions

- **Navigation 3**, one back stack in `shared/src/App.kt`. Adding a screen takes three edits:
  1. `@Serializable data object/class X : NavKey` in `shared/src/nav/Routes.kt`
  2. `subclass(X::class)` in `navConfiguration` (App.kt). Without it saving the back stack fails on iOS/desktop.
  3. `entry<X> { XRoute() }` in the `entryProvider` (App.kt)
- **Screens** are split into `XRoute()` (resolves the ViewModel via `currentKoinScope()` + `viewModel { }`,
  collects state) and a stateless `XScreen(state, callbacks)` that previews and tests can drive directly.
- **ViewModels** expose `StateFlow<UiState>` from a private `MutableStateFlow`. They are entry-scoped by
  `rememberViewModelStoreNavEntryDecorator`.
- **DI is Koin DSL** (no annotations/KSP) in an isolated container: `shared/src/di/AppKoin.kt`. Add a module
  per domain to `appModules()`. Constructor-inject everything; keep platform calls (`getPlatform()`) behind
  injected interfaces so tests can fake them.
- **Theme**: `IntervalTheme` in `shared/src/ui/Theme.kt`.
- **Logging**: Kermit (`co.touchlab.kermit.Logger`).
- **Platform code**: `expect`/`actual` in `src/` + `src@<platform>/`. Prefer an interface injected via Koin
  over `expect class`.

## Gotchas

- **`allWarningsAsErrors` is on everywhere.** Opt in per file (`@file:OptIn(...)`), **never** with module-wide
  `optIns`. Since toolchain 0.13 a module's flags reach the Kotlin/Native cache build of every dependency, and
  an unresolved marker there fails the iOS link. Only a full `./kotlin build` catches it; `-p jvm` passes.
- **Desktop needs `kotlinx-coroutines-swing`** (already in `desktopApp`). Without it `Dispatchers.Main` is
  missing and `viewModelScope` crashes at runtime.
- **The lint plugin needs `slf4j-nop`**: ktlint's rules bind kotlin-logging to slf4j at class-init time.
- iOS `ViewController()` is PascalCase on purpose (Swift calls it by that name) and is suppressed for ktlint.

## Version control: jj, colocated with git

`.jj/` sits beside `.git/`. Use `jj` (`jj st`, `jj describe`, `jj new`, `jj git push`). A detached git HEAD
is normal; don't "fix" it or create git branches.
