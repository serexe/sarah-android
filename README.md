# S.A.R.A.H Android — current build21 source snapshot

Kotlin/Compose client for a separately operated HTTPS voice bridge. Current source declares `com.serexe.sarah`, `versionCode 21`, `versionName 0.1.20-owner-identity-review`. Josh reported the build21 review APK **works on his phone**. This is a source publication of that approved development snapshot, not a new build, retest, APK release, production deployment, or guarantee that every voice path works on every device. The published source is not a reproducible signed-APK attestation.

## What this source does

- Foreground tap-to-talk, encrypted local pairing state, authenticated HTTPS bridge health/upload and reply playback; unknown upload admission is not blindly retried.
- Opt-in **Hey Sarah** offline keyword listener with Android foreground-service notification and explicit Stop; listener, capture, and playback ownership gates guard handoffs. Manual tap remains fallback. Assistant-role gesture opens the app; it does not itself grant recording permission.
- Code21 corrects an offscreen cleanup owner-reference error (`this@OffscreenCommand`) after native ACK. The development report records focused unit tests, full unit suite/lint, a debug build, and one scoped exact-APK synthetic emulator service/Stop proof before phone handoff. Phone approval is user-reported; this publication did not independently repeat physical acoustic wake, STT/TTS, audible output, black-screen or sustained battery/reliability trials.

Microphone requires opt-in/permission. Do not put provider or home secrets on the device; use a revocable bridge token. Local reset does not erase server history, and OS cache may retain temporary reply audio after process death. No live bridge endpoint, token, private CA, signer or personal transcript is published.

## Build source and limits

JDK 17, Android SDK 35 and Gradle 8.11.1 wrapper. The **compiled upstream sherpa-onnx AAR is intentionally not in Git**. Retrieve the official v2.0.1 artifact from the URL in [KWS-DEPENDENCIES.md](KWS-DEPENDENCIES.md) as `app/libs/sherpa-onnx-2.0.1.aar` and verify SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` before building; Gradle's `verifySherpaAar` task also rejects a mismatch. Model inference assets and required notice texts are included under `app/src/main/assets/`. SDKs, downloaded dependencies and caches are not included.

`app/build.gradle.kts` retains its local review signing configuration, which reads `.local-review/review.jks` and `.local-review/signing-pass`. Those files are private and absent. A separately approved local signer is required for the configured signed debug build; do not regenerate or publish the phone-approved signer. Do not treat a locally rebuilt APK as identical to the phone-approved APK. Android instrumentation/device-fixture tests are omitted from this public current snapshot because their audio fixtures and test-server PKCS#12 private key are excluded. Historical `ScreenshotPolicyTest.kt` remains from code3; the published unit tests under `app/src/test/` are source only and were not rerun for publication. Build and test histories refer to the separate local development workspace, not this checkout.

## Repository history and licensing

This commit updates the tracked application source from historical code3 to build21 while preserving prior commits, screenshots, `CODE3-*` records, existing `historical-code8` release/tag/assets, and the historical code8 [COVERED-SOURCE.md](COVERED-SOURCE.md), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), and `notices/` tree. **Those documents and the code8 APK refer to separate historical artifacts, not a build produced from current source.** See [CODE21-PUBLICATION.md](CODE21-PUBLICATION.md) for exact current snapshot scope and retained native notices. The older [docs/screenshots](docs/screenshots/) images show code3 only.

Owner-controlled source/artwork: [MIT LICENSE](LICENSE); third-party components and model keep separate terms. [KWS-DEPENDENCIES.md](KWS-DEPENDENCIES.md) records sherpa/model provenance; [DEPENDENCIES.md](DEPENDENCIES.md) records declared dependencies; `app/src/main/assets/licenses/` includes sherpa Apache-2.0, ONNX Runtime MIT and its ThirdPartyNotices. No APK or compiled native AAR is committed by this snapshot. Nothing here changes or promotes a release.
