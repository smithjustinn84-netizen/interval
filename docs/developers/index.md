# Developers

Interval is a Compose Multiplatform app for Android, iOS and desktop (JVM), built with the
[Kotlin Toolchain](https://kotlin-toolchain.org/0.14/) (Amper). **There is no Gradle**: build configuration lives
in `project.yaml` and each module's `module.yaml`.

## Prerequisites

The `./kotlin` wrapper (`kotlin.bat` on Windows) provisions the pinned toolchain, a Java runtime and the Android
SDK components itself.

- **iOS** needs macOS with Xcode.
- **Python 3** runs the checks in `tools/`.

## Build, test, check

```sh
./kotlin build                        # everything, including the iOS link
./kotlin build -p jvm                 # the fast compile loop
./kotlin test -m lift-domain -p jvm   # one module's tests
./kotlin test -p jvm                  # every test on the JVM
./kotlin check lint detekt            # ktlint and detekt
./kotlin do format                    # ktlint auto-format
python3 tools/check_module_rules.py   # the module layering
```

Avoid a bare `./kotlin check` locally: it also links every module's iOS tests in Release.

## Run

=== "Desktop"

    ```sh
    ./kotlin run -m desktopApp --compose-hot-reload
    ```

    Each save swaps the code in place: the quickest way to work on the UI.

=== "Android"

    ```sh
    ./kotlin run -m androidApp
    ```

=== "iOS"

    ```sh
    ./kotlin run -m iosApp
    ```

## The code

35 modules in five folders: `core/` (what both sides build on), `interval/` (conditioning, mobility, Today and
the Log), `lift/` (Lift, the strength side), `apps/` (the app and its three hosts) and `tooling/`. Dependencies
point toward `core/`, and `tools/check_module_rules.py` keeps them that way in CI.

<div class="grid cards" markdown>

-   **[Architecture](../architecture.md)**

    The layers, the modules, Navigation 3, Koin, storage, and the workout off screen.

-   **[ADR-0001: KMP app choices](../adr/0001-kmp-app-choices.md)**

    Why one Compose Multiplatform codebase, Navigation 3, Room KMP and the Kotlin Toolchain.

-   **[Testing](../testing.md)**

    What runs where: logic, Compose UI on the JVM, acceptance specs, on-device journeys.

-   **[Kettlebell coaching](../kettlebell-coaching.md)**

    How Lift weighs the grip and readiness, eases a taxed day, and works out density and pace.

-   **[Releasing](../release.md)**

    Versions, R8, the keystore, Play Console and CI.

</div>

The conventions in full are in
[CLAUDE.md](https://github.com/smithjustinn84-netizen/interval/blob/main/CLAUDE.md).

## Icons & storefront renders

The 3D kettlebell app icon and framed storefront mockups are rendered headlessly in Blender:

```sh
uv run --with pillow tools/render_icon_3d.py     # iOS app icon & Android adaptive foreground
/Applications/Blender.app/Contents/MacOS/Blender --background \
  --python tools/render_store_frames.py        # transparent portrait & landscape device renders
```

## Version control

The repository uses [Jujutsu](https://github.com/jj-vcs/jj) colocated with Git: `jj st`, `jj describe`,
`jj new`, `jj git push`. A detached Git `HEAD` is normal.

## This site

MkDocs Material, from `mkdocs.yml` and `docs/`, published by `.github/workflows/pages.yml` on every push to
`main` that touches it. Preview it locally:

```sh
uv run --with-requirements tools/docs-requirements.txt mkdocs serve
```
