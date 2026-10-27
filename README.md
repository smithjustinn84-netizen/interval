# Interval

This project is targeting Android, iOS, Desktop (JVM), built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/iosApp](./iosApp) contains the iOS application.
- [/lint-plugin](./lint-plugin) is a Kotlin Toolchain plugin that runs ktlint (`./kotlin check lint`, `./kotlin do format`).
- [/detekt-plugin](./detekt-plugin) is a Kotlin Toolchain plugin that runs detekt (`./kotlin check detekt`).
- [/shared](./shared) is the app the three applications host: its navigation and container over the library
  modules beside it. [/core](./core) is the theme and components; `interval-*` the workout side (domain, data,
  platform, the shared UI, and a module per feature); `bells-*` the kettlebell side behind the Lift tab, with
  [/bells](./bells) its entry point; `*-testing` the fakes their tests share. In every module `src` is common
  code and the sibling `src@<platform>` folders (for example `src@android`) hold code compiled only for the
  platform named in the folder. [CLAUDE.md](./CLAUDE.md) maps the modules.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required.
Build the whole project with `./kotlin build`.

### Running

Run each module from the command line:

- Android app: `./kotlin run -m androidApp`, or with the [android CLI](https://developer.android.com/tools/agents/android-cli):
  `android emulator start amper-37` then `android run --apks=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`

### Testing

Run the project's tests with `./kotlin test`, or `./kotlin test -p jvm` for the fast loop (logic and Compose UI tests). `./kotlin check` runs tests, ktlint and detekt. See [docs/testing.md](./docs/testing.md) for the testing strategy and the on-device journeys.

See [CLAUDE.md](./CLAUDE.md) for architecture and conventions, and [CHANGELOG.md](./CHANGELOG.md) for what each version changed.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/latest/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/).
