# Bundled APKs

This directory holds compiled, signed, and packaged APK/AAB artifacts produced by the build scripts in `../scripts/`.

## Layout

- `<app>-debug-<version>.apk` / `<app>-debug.apk` — Debug APK builds produced by `scripts/build-debug.sh`
- `<app>-release-<version>.apk` / `<app>-release.apk` — Release APK builds produced by `scripts/build-release.sh`
- `*.sha256` — SHA-256 checksums for individual artifacts
- `releases/v<version>/` — Reproducible release bundles, `SHA256SUMS.txt`, `release-manifest.json`, and archives created by `scripts/package-release.sh`

Run `../scripts/verify.sh` from the repository root (`./scripts/verify.sh`) to verify APKs and checksums in this directory.
