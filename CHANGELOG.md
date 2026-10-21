# CloudIDE 3.3

- Saving build results no longer depends on an APK. A successful build that makes only files (C, Rust, Go,
  Python... builds) now offers "Save to folder" (one file, or all files as one .zip) or "Files...".
- Three-dot menu: new "APK and build files" and "Settings" entries (this project's GitHub and build settings,
  or the app's theme, editor and accounts).
- The build panel button is shown after any successful build: "Get APK" or "Build files".
- "Install last APK" with no APK now says what the build did make and opens those files.
- Keyboard is dismissed when you open the Problems or Build log tabs, so they are not squeezed.

# CloudIDE 3.2

- Terminal tab in the bottom panel (also Project > Terminal). Runs the Android shell in your project folder and
  remembers the folder between commands. Built-ins: `build [task]`, `push`, `apk`, `outputs`, `history`,
  `problems`, `langs`, `open <file>`, `clear`, `help`. Stop button ends a running command. Tap the prompt for history.
- Error bar under the editor: shows the compiler message for the line you are on, with Prev / Next to jump between
  the problems in the file. Tap the message to copy it.
- After a build, the APK downloaded from GitHub is offered as Install / Save to folder / Later. "Get APK" in the
  build panel and the Build outputs list also offer Save to a folder (any folder you pick), Share and Inspect.
- Fix: project detection used `java.nio.file`, which crashes on Android 7. Now uses APIs available on API 24.

# CloudIDE 3.1

## Multi-language builds ("auto" build type, now the default for imported projects)
The workflow looks at the repo when it runs and builds everything it finds, up to 3 folders deep:

| Found | Built with |
|---|---|
| `build.sh` in the root | only that script (your own build always wins) |
| `settings.gradle(.kts)` | Gradle: Android, Kotlin, Java, Compose, NDK. Plain JVM projects use `assemble` |
| `pubspec.yaml` with `flutter:` | `flutter build apk --debug` (creates `android/` if missing) |
| `package.json` | npm / yarn / pnpm install, then `npm run build` |
| `Cargo.toml` | `cargo build --release` |
| `pom.xml` | Maven `package` |
| `go.mod` | `go build ./...` |
| `pyproject.toml` / `setup.py` | `python -m build` |
| `*.sln` / `*.csproj` | `dotnet publish` |
| `CMakeLists.txt` / `Makefile` | cmake / make |

- Nothing is built twice: Flutter's `android/`, C++ that Gradle compiles, Rust that Gradle drives, workspace
  members and `node_modules` are skipped.
- One failing language does not stop the others. The build ends with a PASS/FAIL table, shown in the app.
- Outputs land in `cloudide-out/<language>/`, APKs and AABs are picked up from Gradle's folders.
- Existing projects keep their saved build type. Choose `auto` in the project's build settings to switch.
- A project that already has its own `build.yml` is never overwritten: CloudIDE writes `cloudide.yml` next to it.
- Project > "Regenerate build workflow" rewrites the workflow file on demand.

# CloudIDE 3.0

## Build system
- Android Gradle Plugin 8.5.2 -> 8.13.0, Gradle 8.9 -> 8.13 (workflow default and new-project default)
- compileSdk 34 -> 36, targetSdk 34 -> 35
- AppCompat 1.7.1, Material 1.13.0, RecyclerView 1.4.0, CoordinatorLayout 1.3.0, Activity 1.10.1
- Gradle parallel + build cache enabled
- New-project templates upgraded to the same toolchain. Kotlin 1.9.24 -> 2.2.20 (Compose now uses the
  Compose compiler plugin instead of `composeOptions`), Compose BOM 2025.06.01, `kotlinOptions` -> `compilerOptions`
- Generated apps respect system bars (targetSdk 35 forces edge-to-edge)

## Android 15/16 support
- Edge-to-edge with proper insets on every screen, including keyboard (IME) handling in the editor
- Predictive back (OnBackPressedDispatcher) replaces the deprecated `onBackPressed`
- Adaptive + themed (monochrome) launcher icon

## New features
- Quick open (Ctrl+P, or Project > Quick open file): fuzzy file finder, open files ranked first
- Build task picker: debug/release APK, release AAB, lint, unit tests, clean build, or a custom task.
  Outputs are collected automatically for each task (AAB, lint reports, test results)
- Build history per project (result, duration, run number, link to the GitHub run)
- Build log actions: copy, share, open run on GitHub, history
- APK inspector (Build outputs > Inspect): size breakdown, DEX count, ABIs, signature schemes
  (v1 and v2/v3 detected), largest files, text preview, extract and share
- Home screen: search, recent-first sorting, project type, last-build status with time
- Settings: build downloads storage usage with one-tap cleanup, live version number

## Look
- New palette (graphite + lagoon teal, amber reserved for build actions), WCAG AA contrast in light and dark
- New shape scale, monospace wordmark, color-coded project tiles, actionable empty states
- "Lagoon Dark" / "Lagoon Light" editor themes, used by Auto

## Fixes
- Build fix: `colorPrimary` now read from `androidx.appcompat.R` (Material 1.13 no longer exposes it)
- setup-java action bumped to v5

## Not changed on purpose
- Other GitHub Actions stay on `@v4` (checkout, setup-gradle, cache, upload-artifact)
- targetSdk stays at 35. Move to 36 after testing on an Android 16 device.
