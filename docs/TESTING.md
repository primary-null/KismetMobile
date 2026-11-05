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
