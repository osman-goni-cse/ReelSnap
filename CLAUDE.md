# ReelSnap (Android)

Personal, sideloaded Android app. While Instagram, Instagram Lite, Facebook or Facebook Lite is in the foreground,
it repeats: **wait → screenshot × N (with a gap) → swipe up**. Offline: the app has no INTERNET permission.
Companion browser extension: https://github.com/osman-goni-cse/ReelSnap-Web

## Stack and build
- Kotlin, Jetpack Compose (Material 3), single `app` module, package `com.reelsnap`, minSdk 30 (Android 11+).
- Gradle wrapper 9.1, AGP 8.13, Kotlin 2.2.20, compileSdk/targetSdk 34 (the SDK installed on this Mac).
  Library versions are pinned to work with SDK 34; don't bump them unless the SDK is upgraded too.
- The system JDK is 25. `local.properties` (gitignored) holds `sdk.dir=~/Library/Android/sdk`.
- `adb` isn't on PATH: use `~/Library/Android/sdk/platform-tools/adb`.

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed, see below
./gradlew lintDebug
```

## Release signing
- Keystore: `~/.android/reelsnap-release.jks` (outside the repo). Its path and password are in the gitignored
  `keystore.properties`. Never commit either, and never print the password.
- Every release must be signed with this key, or it won't install over the previous version.
- v1.0 was debug-signed; v1.0.1 was the first release-signed build (cert SHA-256 `bc6f3690…cda9d60`).
- Releases: bump `versionCode`/`versionName` in `app/build.gradle.kts`, then
  `gh release create vX.Y.Z <apk> -R osman-goni-cse/ReelSnap`.

## How it works
- `service/ReelSnapService.kt`: the AccessibilityService. It tracks the active app window (system UI such as the
  notification shade counts as "left") and starts or cancels the loop with a 1 s debounce.
- `service/CaptureLoop.kt`: the loop. ±20% jitter. Screenshots at least 1.1 s apart, because Android allows about
  one accessibility screenshot per second.
- `service/Screenshotter.kt` uses `takeScreenshot`; `service/Swiper.kt` uses `dispatchGesture`.
- `storage/ImageSaver.kt` saves through MediaStore to `Pictures/ReelSnap/<session>/`.
- Settings live in DataStore (`data/Settings.kt`). The UI is one screen (`ui/SettingsScreen.kt`).
- Android 13+: sideloaded apps need App info → ⋮ → *Allow restricted settings* before the accessibility service
  can be enabled.

## Tracking
- Issues use JIRA-style labels (`type: epic|story|feature|task|bug`) and milestones (v1.0, v1.1).
  Epic: #12. Board: https://github.com/users/osman-goni-cse/projects/1 (shared with ReelSnap-Web).
- Close tickets from commit messages (`Closes #n`) and tick them off in the epic's checklist.
- Open: #10, testing on a real Android 11+ device. It has never run on a device: only the build and lint are verified.

## Conventions
- Commit and push only when asked. Commit messages end with the Co-Authored-By line.
