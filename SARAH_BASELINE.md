# S.A.R.A.H — first golden baseline (build29)

Immutable source ref: annotated [`sarah-golden-v29`](https://github.com/serexe/sarah-android/tree/sarah-golden-v29), on cumulative `main` after code26 → goals1+3 build28 → health-cause diagnostic build29. Exact commit: `git rev-list -n1 sarah-golden-v29` (the tag and release target this commit). This is a selected production/build/unit-test **source snapshot** from the separate non-Git development tree, not proof of bit-reproducible APK output. No private instrumentation fixtures, keys or local config are in the snapshot.

Exact user-approved APK: [`sarah-v29-health-cause-diagnostic-arm64-v8a-x86_64-signed-local-review-debug.apk`](https://github.com/serexe/sarah-android/releases/download/sarah-golden-v29/sarah-v29-health-cause-diagnostic-arm64-v8a-x86_64-signed-local-review-debug.apk) — **82,973,185 bytes**, SHA-256 `e7da9f16b6a6e0a3e72dc586bb374b00c9520c39dca9a497bfdf7c1a9bafb2b9`. Package `com.serexe.sarah`; versionCode `29`, versionName `0.1.28-health-cause-diagnostic-review`; minSdk 26 / targetSdk 35; ABIs `arm64-v8a`, `x86_64` (five native libraries per ABI); APK signing v2 local-review/debug certificate SHA-256 `79c9b26baac4b0b60563430ebdf0023808cdf4e04b4d1d719fa950a7f3d5fa7d`. **Not production signed**; do not re-sign/rebuild and call different bytes this golden.

Source association: `app/build.gradle.kts` SHA-256 `ff490948a19f5642fe542ec28fc21343e870bb46ef4f983963894e5920acc684`; `app/src/main/java/com/serexe/sarah/SarahViewModel.kt` `4eab56177b826db48e88b0d12d686af16687ab08d149c40132556c189b4e9833`; `app/src/test/java/com/serexe/sarah/SarahViewModelTest.kt` `fda24c600f87b6258ca5ad049b2e0746d2a29f76ae08ac05660909d0e3d7450e`. Build28→29 changed these three files only; native libraries, model and bundled license/notice assets are byte-identical to retained build28 APK. Historical code8 Eigen covered-source archives and notices remain linked in [COVERED-SOURCE.md](COVERED-SOURCE.md) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md); those historical hashes are **not** build29 identity claims.

Proof limit: build29 focused diagnostic JUnit 1 test / 0 failures and one isolated VM exact-APK upgrade/About → diagnostics → Back, no app crash in scoped logs. Its fixed health-failure categories are `dns`, `connect`, `noRoute`, `timeout`, `eof`, `protocol`, `tls`, existing HTTP/auth categories and fallback `network`; `noRoute` was not in the seven injected test classes. This is **diagnostic, not an APK connectivity repair**. Separately restoring availability of a stopped paired bridge cannot establish the physical phone's cause or recovery. Goals1+3 inherited build28 source/focused-test and qualified synthetic runtime evidence, **not full goals1+3 physical/offscreen/acoustic acceptance**. User approved these exact build29 bytes as first golden; physical-phone behavior is not independently asserted here. Bridge origin, token, private CA and signing key are not bundled; configure your own paired HTTPS bridge.

Future baseline check (from a clean clone):

```sh
git fetch origin main --tags
git rev-list -n1 sarah-golden-v29   # inspect immutable baseline commit
git merge-base --is-ancestor sarah-golden-v29 origin/main
sha256sum app/build.gradle.kts app/src/main/java/com/serexe/sarah/SarahViewModel.kt app/src/test/java/com/serexe/sarah/SarahViewModelTest.kt  # at golden tag, compare above
sha256sum sarah-v29-health-cause-diagnostic-arm64-v8a-x86_64-signed-local-review-debug.apk # downloaded release asset; compare above
```

Build future changes from the golden/cumulative line, but never infer a new artifact's signer, version, ABI, proof or approval from this tag.
