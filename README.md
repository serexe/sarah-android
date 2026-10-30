# S.A.R.A.H Android — active development

**Stable source and golden:** `sarah-golden-v30` on `main`, package `com.serexe.sarah`, versionCode 30 / versionName `0.1.29-persistent-wake-review`. [Download the latest stable golden APK](https://github.com/serexe/sarah-android/releases/latest) and see [SARAH_BASELINE.md](SARAH_BASELINE.md) for exact approved bytes, source linkage, signer and proof limits. The Kotlin/Compose client connects to a separately operated HTTPS voice bridge; users must configure their own paired bridge and credentials. The golden is an approved **local-review/debug-signed persistent-wake** build, not a production-signed APK or proof of physical-phone connection repair. Josh confirms app close and notification dismissal work; screen-off successive wakes and explicit Stop remain unconfirmed. Golden29 and older review checkpoints remain historical, including [CODE26-CHECKPOINT.md](CODE26-CHECKPOINT.md), [CODE22-REVIEW.md](CODE22-REVIEW.md), and the code8 assets.

## v31 Command Center review — actual APK screenshots

This branch contains **v31 REVIEW**, `0.1.31-commandcenter-review` (versionCode 31), not a new stable golden. **Golden30 remains the stable baseline.** These unedited screenshots come from the installed Android APK on an existing disposable Android 15 / API 35 x86_64 emulator, not HTML mockups. The device has no Sarah pairing; no credentials, private CA, account or conversation was entered.

| Unpaired setup | Settings |
| --- | --- |
| ![v31 review APK: unpaired HTTPS bridge setup](docs/screenshots/v31-setup.png) | ![v31 review APK: Settings with no local pairing](docs/screenshots/v31-settings.png) |

The paired Command Center session/timeline requires saved bridge configuration and **was not captured**. These images show only the actual unpaired setup and Settings surfaces; no dialogue or connected state was fabricated. See [capture provenance and limits](docs/screenshots/V31-CAPTURE.md). Voice requests, wake listening and physical-phone behavior were not tested by this screenshot run.

## What this source does

- Foreground tap-to-talk, encrypted local pairing state, authenticated HTTPS bridge health/upload and reply playback; unknown upload admission is not blindly retried.
- Opt-in **Hey Sarah** offline keyword listener with Android foreground-service notification and explicit Stop; listener, capture, and playback ownership gates guard handoffs. Manual tap remains fallback. Assistant-role gesture opens the app; it does not itself grant recording permission.
- Code21 corrected an offscreen cleanup owner-reference error (`this@OffscreenCommand`) after native ACK. Later source adds adaptive endpointing and wake-stage handling; no-speech or 30-second cap discards. The code26 softer cue starts after native recording reports ready and drains its acoustic tail before retaining command PCM. Phone feedback is subjective; code26 automated runtime/endpoint proof was not run.

Microphone requires opt-in/permission. Do not put provider or home secrets on the device; use a revocable bridge token. Local reset does not erase server history, and OS cache may retain temporary reply audio after process death. No live bridge endpoint, token, private CA, signer or personal transcript is published.

## Build source and limits

JDK 17, Android SDK 35 and Gradle 8.11.1 wrapper. The **compiled upstream sherpa-onnx AAR is intentionally not in Git**. Retrieve the official v2.0.1 artifact from the URL in [KWS-DEPENDENCIES.md](KWS-DEPENDENCIES.md) as `app/libs/sherpa-onnx-2.0.1.aar` and verify SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` before building; Gradle's `verifySherpaAar` task also rejects a mismatch. Model inference assets and required notice texts are included under `app/src/main/assets/`. SDKs, downloaded dependencies and caches are not included.

`app/build.gradle.kts` retains its local review signing configuration, which reads `.local-review/review.jks` and `.local-review/signing-pass`. Those files are private and absent. A separately approved local signer is required for the configured signed debug build; neither key nor credentials are bundled. A rebuilt APK is **not** identical to the phone-approved golden unless its SHA-256 matches. Private Android instrumentation/device-fixture tests, audio fixtures and test-server PKCS#12 key are excluded; historical `ScreenshotPolicyTest.kt` remains from code3. Public unit tests under `app/src/test/` are source only. Build30 physical feedback and runtime proof limits, plus historical build29 evidence, are documented in [SARAH_BASELINE.md](SARAH_BASELINE.md); no build or test was rerun for this publication.

## Repository history and licensing

The build29 snapshot preserves prior commits, screenshots, code26 and older publication records, existing historical releases/assets, historical code8 [COVERED-SOURCE.md](COVERED-SOURCE.md), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), and `notices/` tree. **Historical identity/checksum assertions in those documents refer to code8, not build29.** Existing third-party notices and Eigen covered-source downloads remain linked without claiming an exhaustive SBOM or legal certification. The older `code3-*` images under [docs/screenshots](docs/screenshots/) are historical code3 captures, not the v31 review UI shown above.

Owner-controlled source/artwork: [MIT LICENSE](LICENSE); third-party components and model keep separate terms. [KWS-DEPENDENCIES.md](KWS-DEPENDENCIES.md) records sherpa/model provenance; [DEPENDENCIES.md](DEPENDENCIES.md) records declared dependencies; `app/src/main/assets/licenses/` includes sherpa Apache-2.0, ONNX Runtime MIT and its ThirdPartyNotices. No APK or compiled native AAR is committed to Git; the exact approved build30 APK is a release asset, not claimed bit-reproducible from this source snapshot.
