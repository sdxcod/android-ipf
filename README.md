# IpCheck
Simple Android app that fetches your public IP details and lets you copy or send them via SMS.

## What it does
- Calls `ipwho.is` to get IP, geo, ISP, timezone, and coordinates.
- Falls back to `api.ipify.org` if the primary service fails (IP only, no geo/ISP).
- Displays a map for the coordinates inside an embedded WebView.
- Lets you copy the IP, store a phone number, pick a contact, and prefill an SMS with the IP.

## Requirements
- Android Studio (Giraffe/Koala+ recommended) with Android SDK 34 installed.
- Android Gradle Plugin 8.4.2, Kotlin 1.9.24 (configured in `build.gradle`).
- Java 17 toolchain.
- Min SDK 24, target SDK 34.
- Working internet connection for API calls; emulator/device with Play Services not required.

## Setup
1) Clone the repo and open it in Android Studio.  
2) Ensure the local Android SDK path is set in `local.properties` (`sdk.dir=...`).  
3) Let Gradle sync; it will generate `BuildConfig` with:
   - `IPWHO_URL = "https://ipwho.is"`
   - `IPIFY_URL = "https://api.ipify.org?format=json"`
4) Connect a device or start an emulator (Android 10+/API 29+ recommended).

## Build & Run
- CLI: `./gradlew :app:assembleDebug` (builds APK) or `./gradlew :app:installDebug` (builds+installs to a connected device/emulator).
- Android Studio: Use Run ▶ on the `app` module after selecting a device/AVD.

## USB device testing
- On the phone: enable Developer Options, turn on USB debugging, and connect via USB (select File Transfer/MTP if prompted).
- Trust the computer on the device prompt; verify connection with `adb devices` (should show `device` status).
- Install and run: `./gradlew :app:installDebug` then `adb shell am start -n ir.seefa.ipcheck/.MainActivity` or launch from the app drawer.
- For a fresh run, you can uninstall first: `adb uninstall ir.seefa.ipcheck` (ignored if not present).

## Permissions
- `INTERNET` for the API requests.
- `READ_CONTACTS` to pick a contact for the SMS target (requested at runtime).

## Notes & Limitations
- If `ipwho.is` is down/unreachable, fallback only returns the IP (no location/ISP/timezone).
- Map is WebView-based and requires latitude/longitude; when unavailable, the map is blank.
- SMS sending uses an implicit `smsto:` intent; requires an installed SMS app and does not auto-send.
- Emulator startup issues: if AVD hangs on boot, try “Cold Boot Now” or “Wipe Data” in AVD Manager, or start with `emulator -avd <name> -no-snapshot-load`.
