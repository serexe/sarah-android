# v31 REVIEW screenshot provenance

Captured 2026-10-30 from the real installed `com.serexe.sarah` APK, versionCode **31**, versionName **0.1.31-commandcenter-review**. Review branch: `ui/command-center-review`; APK source commit: `dcb78f7c588f1e5588bc90f5050d50c1a4989bd3`.

## Exact artifact

- Existing dual-ABI APK: `sarah-v31-command-center-arm64-x86_64-review.apk` (arm64-v8a + x86_64).
- APK SHA-256: `1e857480bde80bb2f02dc673701465a83cfd97695860e8bfbbc0165000ad3245`.
- Installed base APK bytes read back over ADB and SHA-256 matched exactly.
- No rebuild, re-sign, source change or golden promotion. Golden30 remains stable.

## Capture

Existing disposable local AVD `bloombreak_proof_20260710`, Android 15 / API 35, `emulator-5580`, 1080 × 2400 portrait. Sarah was not installed before this run. Installed the exact existing v31 APK, launched `com.serexe.sarah/.MainActivity`, then opened and closed Settings through its visible controls. Captured directly with `adb -s emulator-5580 exec-out screencap -p`; UIAutomator XML collected separately. Both PNGs were visually inspected before publication.

| File | Actual state | PNG SHA-256 |
| --- | --- | --- |
| [v31-setup.png](v31-setup.png) | Unpaired setup; empty bridge/token fields; no imported CA | `85bd6c88ef366a32debdbad0669c42819c4be8a0deeeef8aec21dc41038d8e66` |
| [v31-settings.png](v31-settings.png) | Settings sheet; no local pairing; not default assistant | `9b3a109ae0282e811cddaf730ab40bd71fc0eecc62cac7c2a7d73e082153f9a2` |

Natural, unedited screenshots: no HTML mockup, synthetic conversation, seeded server state, crop, blur or redaction. No bridge address, token, private CA, account or personal transcript entered or shown. Existing real TVs and phones were not touched. Emulator was initially stopped and shut down after capture; no AVD data wipe, app reset or uninstall.

## Limits

The paired Command Center session/timeline is gated behind saved bridge configuration. It was **not captured**, rather than creating credentials, inventing replies or presenting a mockup as installed UI. Screenshots establish only the visible unpaired setup and Settings surfaces. No bridge transaction, microphone request, wake-listener behavior, physical-phone behavior or comprehensive runtime acceptance is claimed. Historical `code3-*` images and their provenance remain unchanged.
