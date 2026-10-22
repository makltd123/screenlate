# ScreenLate

[Русская версия](README.ru.md)

ScreenLate is an Android app that translates English text on the screen of any app into Russian and draws the translation on top of the original. It works fully offline: translation runs on the phone with the Opus-MT en→ru model, and screen text never leaves the device.

## Status

**Stage 1: service and overlay with a placeholder.** The app finds English text in any app and draws plaques over it, but instead of a translation each plaque shows a placeholder `RU: [original text]`. This stage checks that plaques line up with the text; real translation arrives in stage 2. The full specification is in [docs/SPEC.md](docs/SPEC.md) (in Russian).

- An accessibility service reads on-screen text. Passwords, input fields, the keyboard and the system UI are skipped.
- A single touch-transparent overlay draws the plaques and hides them while the screen scrolls.
- A Quick Settings tile switches translation on and off.
- A setup checklist on the main screen walks through the required permissions.
- A hidden diagnostics screen (long press on the version number) shows service events and timings, never screen text.

Target device: Realme 10 (4 GB RAM), Android 13/14. Minimum Android version: 8.0.

## Install

1. On the phone, open the [latest release](https://github.com/makltd123/screenlate/releases/latest).
2. Under **Assets**, tap `ScreenLate-<version>.apk` and open the downloaded file.
3. Allow installing apps from this source if Android asks, then tap **Install**.

New versions install over the old one and keep its data, as long as they are signed with the same key.

## Build

The APK is built only in GitHub Actions ([`build.yml`](.github/workflows/build.yml)): unit tests, then `assembleRelease`, then the APK is uploaded as a workflow artifact. Pushing a `v*` tag also creates a GitHub Release with the APK.

| | |
| --- | --- |
| Language | Kotlin, Jetpack Compose (Material 3) |
| Build | Gradle 9.8.2, Android Gradle Plugin 9.4.1, Kotlin 2.4.21 |
| SDK | minSdk 26, targetSdk 34, compileSdk 37 |
| ABI | arm64-v8a |

### Signing key

The release APK is signed with a key stored in the repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the workflow signs with a throwaway debug key, and such an APK cannot be installed over a previous version. Step-by-step instructions for creating the key are in [README.ru.md](README.ru.md#ключ-подписи).
