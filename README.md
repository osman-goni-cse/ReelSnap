# ReelSnap

While you watch Reels in **Instagram, Instagram Lite, Facebook or Facebook Lite**, ReelSnap repeats:

> wait → screenshot × N (with a gap) → swipe up to the next reel

It is free and fully offline: the app has **no INTERNET permission**. Screenshots go to `Pictures/ReelSnap/<session>/` in your Gallery.

Requires **Android 11+**. Kotlin, Jetpack Compose, Material 3.

## How it works
One `AccessibilityService` (`service/ReelSnapService.kt`):
- tracks the foreground app. The loop auto-starts when an enabled target app is active and pauses about 1 s after you leave (home, recents, notification shade).
- takes screenshots with `AccessibilityService.takeScreenshot` (no screen-recording prompt).
- swipes with `dispatchGesture`.

The loop itself is in `service/CaptureLoop.kt`. Android allows about 1 accessibility screenshot per second, so shots are spaced at least 1.1 s apart.

Settings (all editable in the app):

| Setting | Default |
|---|---|
| Wait before screenshots | 2 s |
| Screenshots per reel | 1 |
| Gap between screenshots | 2 s |
| Random jitter (±20% timing, varied swipe path) | on |
| Stop after N reels / M minutes | 0 = never |
| Format | JPEG (q92) / PNG |

## Build & install
```sh
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
### Signed release build
Release APKs are signed with a key kept **outside the repo**. `keystore.properties` (gitignored) points at it:
```
storeFile=/Users/<you>/.android/reelsnap-release.jks
storePassword=…
keyAlias=reelsnap
keyPassword=…
```
Then `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. Back up the keystore and its
password: updates only install over a previous version when they are signed with the same key. Without
`keystore.properties`, the release build is left unsigned.

Then open ReelSnap → **Turn on** → enable "ReelSnap auto capture".

**Android 13+ "Restricted setting":** sideloaded apps can't enable accessibility at first. Go to Settings → Apps → ReelSnap → ⋮ (top right) → **Allow restricted settings**, then enable the service again.

Logs: `adb logcat -s ReelSnap`

## Notes
- The loop runs whenever a target app is in the foreground, not only on the Reels tab. Toggle it off in ReelSnap (or disable the service) when you just want to browse.
- Some OEM skins (MIUI, ColorOS, …) kill accessibility services in the background. Exclude ReelSnap from battery optimization if the loop stops.
- This is for personal use. Respect the content creators and the apps' terms.
