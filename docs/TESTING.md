# Testing KismetMobile

## Local checks

Use JDK 17 or 21, Android SDK platform 36 and Build Tools 36.0.0.

```powershell
.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testDemoDebugUnitTest :app:lintStandardDebug :app:lintDemoDebug
```

On macOS/Linux, use `./gradlew` with the same tasks. `testDebugUnitTest` and `lintDebug` are ambiguous because the project has standard and demo flavors.

JUnit XML reports are under `app/build/test-results/`; lint XML/HTML reports are under `app/build/reports/`. A successful lint task can still contain warnings; inspect their counts and categories.

## Offline demo

Build `:app:assembleDemoDebug` and install `app/build/outputs/apk/demo/debug/app-demo-debug.apk` on an emulator or device. The demo starts with synthetic devices, sources, and campaigns and does not need a Kismet server. It uses the separate `com.kismet.mobile.demo` application ID.

## Release builds

Release variants are shrunk by R8 and are intentionally unsigned; `RELEASING.md` describes how they become signed releases. To check them locally:

```powershell
.\gradlew.bat :app:assembleStandardRelease :app:assembleDemoRelease
python tools/release/kismet_release.py stage --out dist/unsigned
python -m unittest discover -s tools/release -p "test_*.py" -v
```

`stage` fails unless both APKs are really unsigned, zip-aligned, not debuggable, not `testOnly`, and have the expected package (`com.kismet.mobile`, `com.kismet.mobile.demo`), `versionName`, `versionCode`, `minSdk` and `targetSdk`. The release-tool tests need the Android SDK build-tools; the tests that exercise real signing also need the unsigned release APKs, a debug APK and the SDK's debug keystore, and are skipped when those are missing. They sign with the SDK debug key through a test-only switch, so they prove the mechanics and the fail-closed rules, not any production key. The command-line tool itself always rejects a debug certificate.

JVM tests and lint run on debug builds. Whether the shrunk release build behaves (start-up, saved profiles and campaigns, exports, update over a previous signed release) needs an Android device or emulator.
