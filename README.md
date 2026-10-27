# SARAH Android — historical code8 binary archive

Historical **binary-only debug/review build**, not a production-ready release. Public owner: **serexe**. Intended repository: `serexe/sarah-android`; proposed tag: `historical-code8` (prerelease, not latest).

## Artifact

- `sarah-code8-playback-repaired-arm64-x86_64-review.apk`
- Android package `com.serexe.sarah`; version `0.1.7-handsfree-review`, versionCode **8**.
- Android API 26 minimum, target API 35; ARM64 (`arm64-v8a`) and `x86_64` native runtimes.
- 83,050,988 bytes; SHA-256 `4cb4efa8e6ba13f9eee7154830b84237cecfe8f94a7b9102a278df8e842f93f4`.
- APK v2 signature verified. Local-review certificate SHA-256 `79c9b26baac4b0b60563430ebdf0023808cdf4e04b4d1d719fa950a7f3d5fa7d`; identity `CN=SARAH Local Review, O=Local Review`. Certificate does not establish independent identity/security certification.

## Missing source / provenance

The complete corresponding original code8 application source snapshot has **not been recovered**. This archive contains documentation, third-party notices, third-party covered-source supplements and unchanged historical APK bytes. The source supplements contain Eigen only, not SARAH application source. It does not contain current Android source, a matching source tag, reproducible-build instructions, or a claim that newer source produced this APK. GitHub-generated “Source code” archives would contain this documentation repository, not corresponding application source.

## Behavior and limits

SARAH is an experimental Android voice/assistant client for a separately paired authenticated bridge. Endpoint/device-token configuration belongs to the operator; no operational token is supplied. Local wake detection is opt-in and bounded to a two-minute window. The historical UI describes foreground/unlocked automatic command capture and reply, with unlock/open/tap fallback for locked/offscreen hits. It does not establish reliable always-on or locked-screen operation.

Prior acceptance was a **phone-review candidate awaiting physical check**. Exact-byte emulator tests used injected capture/speech flags, loopback HTTPS and synthetic tone playback: five cases inside one instrumentation test, three command POSTs and three audio GETs. Native completion/Stop/background cleanup and rearm policy were checked in those cases. This is not acoustic wake, actual spoken command recognition, real TTS generation/audibility, real bridge/smart-home action, physical-phone, Doze/OEM, or complete interruption/security-matrix proof. A prior trigger test ran on an earlier APK hash and is not exact-code8-repaired-hash trigger proof. No new runtime testing was performed for this archive.

Debuggable build: local debugging increases exposure of app-private data. Historical reply audio used a plaintext temporary MP3 in app-private cache; cleanup is best-effort and process death can leave residual data. Stop audio does not roll back server work. Historical timer/replay/cancellation behavior is not a comprehensive security guarantee. See [release notes](RELEASE-NOTES.md).

This old versionCode is not a normal upgrade from newer builds. Preserve existing pairing, requests and conversations; do not bypass version/signature conflicts. No downgrade or destructive reset procedure is provided.

## License and redistribution status

[MIT LICENSE](LICENSE) covers **serexe-owned material only**, including owner-confirmed original/derived launcher artwork and original application portions to the extent owned. It does **not** relicense any third-party code, native library, model, tokenizer, data or notice embedded in the APK. Corresponding original application source remains unavailable despite the owner's MIT grant.

[THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md) records model-specific licensing and verified native provenance. Redistribution review cleared for this unchanged historical APK **with the accompanying notices and Eigen covered-source supplements**. [COVERED-SOURCE.md](COVERED-SOURCE.md) identifies the source archives, applicable MPL-2.0 terms and upstream modifications. These are third-party sources, not the missing application snapshot. No complete SBOM, bit-reproducible build, undocumented-upstream-change attestation or blanket compliance guarantee is claimed.
