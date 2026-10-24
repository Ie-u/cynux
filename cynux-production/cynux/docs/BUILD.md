# Building the APK

## Verified build

The launcher was compiled successfully by GitHub Actions on 2026-10-24. The APK from that build is available in this checkout at `dist/CynuxProductionLauncher.apk` (396,609 bytes). The workflow artifact is named `cynux-apk`.

The project has **no library dependencies**: only the Android SDK platform 34 and Android Gradle Plugin 8.5.2 (downloaded by Gradle).

## Option A – Android Studio (simplest)
1. Install Android Studio; it fetches the SDK and JDK.
2. Open the `cynux` folder, let Gradle sync.
3. Build > Build APK(s) – or run `gradlew assembleRelease`.
4. Output: `app\build\outputs\apk\release\app-release.apk` (debug-signed, fine for sideloading).

## Option B – Command line (your PC has 8 GB RAM; Studio is heavy)
1. JDK 17, Android command-line tools; `sdkmanager "platforms;android-34" "build-tools;34.0.0"`.
2. Set `ANDROID_HOME`, run `gradle wrapper` once, then `gradlew assembleRelease`.
3. Expected download: roughly 1 GB total. Consider whether it's worth it vs. option C.

## Option C – No local toolchain
Push the folder to a GitHub repo and build with a GitHub Actions workflow (`setup-java` 17 + `android-actions/setup-android`, then `./gradlew assembleRelease`). I can write that workflow if you want it.

First compile may reveal small errors (I could not run the compiler). Send me the Gradle error text and I'll fix it.

## Remote keys
D-pad moves, OK opens, **hold OK** toggles favourite, **MENU** (or INFO / yellow) cycles three themes (Forest, Onyx, Emerald). BACK is ignored by design.
