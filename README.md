# ScreenLate

[Русская версия](README.ru.md)

ScreenLate is an Android app that translates English text on the screen of any app into Russian and draws the translation on top of the original. It works fully offline: translation runs on the phone with the Opus-MT en→ru model, and screen text never leaves the device.

## Status

**Stage 4: OCR.** The app finds English text in any app and draws plaques with the Russian translation over it. Translation runs on the phone. For apps that do not expose their text (games, images), a one-shot screenshot mode recognises English text with ML Kit and translates it the same way. The full specification is in [docs/SPEC.md](docs/SPEC.md) (in Russian).

- An accessibility service reads on-screen text. Passwords, input fields, the keyboard and the system UI are skipped.
- Translation uses Opus-MT en→ru, exported to ONNX and quantized to int8 (about 97 MB, downloaded once on first run, then fully offline), on ONNX Runtime with a native SentencePiece tokenizer.
- Translations are cached in memory and on disk, so text seen before shows up at once.
- A single touch-transparent overlay draws the plaques and hides them while the screen scrolls.
- "Translate a picture" in the floating button's menu takes one screenshot (MediaProjection, asked every time), waits until the screen stops changing, recognises English lines with ML Kit (bundled, offline) and shows the same plaques. Protected screens (FLAG_SECURE) get a clear message.
- A Quick Settings tile switches translation on and off.
- The main screen has a setup checklist and a "test translation" field.
- A hidden diagnostics screen (long press on the version number) shows service events and timings and runs a model self-check. It never shows screen text.

Target device: Realme 10 (4 GB RAM), Android 13/14. Minimum Android version: 8.0.

## Install

1. On the phone, open the [latest release](https://github.com/makltd123/screenlate/releases/latest).
2. Under **Assets**, tap `ScreenLate-<version>.apk` and open the downloaded file.
3. Allow installing apps from this source if Android asks, then tap **Install**.

On Realme phones, install through [Obtainium](https://github.com/ImranR98/Obtainium) instead: Android 13+ blocks sideloaded apps from enabling accessibility services, and Realme hides the usual override. See [README.ru.md](README.ru.md) for the steps.

New versions install over the old one and keep its data, as long as they are signed with the same key.

## Build

The APK is built only in GitHub Actions ([`build.yml`](.github/workflows/build.yml)): unit tests, then `assembleRelease`, then the APK is uploaded as a workflow artifact. Pushing a `v*` tag also creates a GitHub Release with the APK.

| | |
| --- | --- |
| Language | Kotlin, Jetpack Compose (Material 3) |
| Build | Gradle 9.8.2, Android Gradle Plugin 9.4.1, Kotlin 2.4.21 |
| SDK | minSdk 26, targetSdk 34, compileSdk 37 |
| ABI | arm64-v8a |

### Translation model

The model is not in the APK. [`convert-model.yml`](.github/workflows/convert-model.yml) runs [`scripts/convert_model.py`](scripts/convert_model.py): it exports [Helsinki-NLP/opus-mt-en-ru](https://huggingface.co/Helsinki-NLP/opus-mt-en-ru) to ONNX (encoder plus a merged decoder with KV cache), quantizes it to int8, checks 20 reference phrases against the original PyTorch model and publishes the zip to the `model-v1` release. The app downloads that exact file and checks its SHA-256 (see `gradle.properties`).

Opus-MT models are by the Language Technology Research Group at the University of Helsinki and are released under the Apache License 2.0.

### OCR

Stage 4 adds screenshot translation. `com.google.mlkit:text-recognition` 16.0.1 (the bundled Latin variant) recognises the text fully offline; the Opus-MT model does the translating. The capture is one shot per tap of the floating button: a foreground service of type `mediaProjection` holds the projection only while the run lasts.

### Signing key

The release APK is signed with a key stored in the repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the workflow signs with a throwaway debug key, and such an APK cannot be installed over a previous version. Step-by-step instructions for creating the key are in [README.ru.md](README.ru.md#ключ-подписи).

---

Author: [Fedor Makovskiy](https://fedor.alqira.ru), AI developer. Services and projects with quality metrics: [fedor.alqira.ru](https://fedor.alqira.ru).
