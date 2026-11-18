# Momentum Build, Versioning & Release Scripts

This directory contains the shell automation for versioning, building, packaging, and verifying Android APK/AAB artifacts staged in [`../bundled-apk/`](../bundled-apk/).

## Directory Layout

```text
scripts/
├── common.sh           # Shared logging, SDK discovery, versioning, signing & staging helpers
├── bump-version.sh     # Inspect or bump versionName and versionCode
├── build-debug.sh      # Build a debug APK and stage it in ./bundled-apk/
├── build-release.sh    # Build, align & sign a release APK (and optional AAB)
├── package-release.sh  # Create reproducible release bundles + SHA256SUMS + manifest
├── verify.sh           # Verify scripts, Android SDK tools, APKs, signatures & checksums
└── README.md           # This documentation

bundled-apk/
├── README.md
├── <app>-debug-<version>.apk
├── <app>-release-<version>.apk
├── <app>-latest.apk
└── releases/
    └── v<version>/
        ├── <app>-v<version>-vc<code>.apk
        ├── release-manifest.json
        └── SHA256SUMS.txt
```

## Quick Start

```bash
# 1. Inspect current version
./scripts/bump-version.sh show

# 2. Bump patch version (updates version.properties / Gradle / chrome/VERSION)
./scripts/bump-version.sh patch

# 3. Build debug APK into ./bundled-apk/
./scripts/build-debug.sh

# 4. Build signed release APK into ./bundled-apk/
RELEASE_KEYSTORE=/path/to/release.jks \
RELEASE_KEY_ALIAS=momentum \
RELEASE_STORE_PASSWORD=secret \
./scripts/build-release.sh

# 5. Package release bundle, manifest, checksums, and archive
./scripts/package-release.sh

# 6. Verify all scripts, APKs, signatures, alignment, and checksums
./scripts/verify.sh --require-apk
```

---

## Script Reference

### `scripts/common.sh`

Shared library sourced by every script in `scripts/`. Provides:

- **Strict execution**: `set -euo pipefail` and multiple-source include guard.
- **Path resolution**: `ROOT_DIR`, `SCRIPTS_DIR`, `BUNDLED_APK_DIR`, `RELEASES_DIR`, `BUILD_DIR_DEBUG`, `BUILD_DIR_RELEASE`.
- **Android SDK discovery**: Automatically locates `ANDROID_SDK_ROOT` / `ANDROID_HOME` (falling back to `/opt/android/sdk` and standard SDK paths) and resolves the latest `build-tools` binaries (`apksigner`, `zipalign`, `aapt2`).
- **Build system detection**: Supports both **Gradle** (`gradlew` / `gradle`) and **Chromium GN/Ninja** (`gn` + `autoninja` / `ninja`).
- **Signing & staging**: `align_and_sign_apk`, `is_apk_signed`, `stage_apk_to_bundled`, and `write_sha256_file`.

### `scripts/bump-version.sh`

Inspects or increments `versionName` and `versionCode` across any detected version source (`version.properties`, `chrome/VERSION`, `app/build.gradle.kts`, `app/build.gradle`, or `VERSION`).

```bash
./scripts/bump-version.sh [show|major|minor|patch|build|set <ver>] [options]
```

Options:
- `--code <int>`: Explicitly set `versionCode` (default increments by `1`).
- `--no-code`: Leave `versionCode` unchanged.
- `--file <path>`: Update only a specific version file.
- `--init`: Create `version.properties` if no version file exists.
- `--commit` / `--tag`: Commit the change and/or create an annotated git tag `v<version>`.
- `--dry-run`: Preview changes without writing files.

### `scripts/build-debug.sh`

Builds a debug APK, signs it with `debug.keystore` if unsigned, and stages `<app>-debug-<version>.apk`, `<app>-debug.apk`, and `.sha256` files into `./bundled-apk/`.

Options:
- `--target <name>`: Gradle task (default `:app:assembleDebug`) or GN target (default `chrome_public_apk`).
- `--out-dir <dir>`: GN output directory (default `out/Debug`).
- `--arch <arm64|arm|x64|x86>`: Target CPU for GN `args.gn` generation (default `arm64`).
- `--output-name <name>`: Custom filename inside `./bundled-apk/`.
- `--clean`: Clean before building.
- `--no-bundle`: Skip copying output into `./bundled-apk/`.

### `scripts/build-release.sh`

Builds a release APK (and optionally `.aab` with `--aab`), aligns with `zipalign`, signs with your release keystore, and stages artifacts into `./bundled-apk/`.

Environment variables (or matching CLI flags `--keystore`, `--alias`, `--store-pass`, `--key-pass`):
- `RELEASE_KEYSTORE`: Path to `.jks` / `.keystore` file.
- `RELEASE_KEY_ALIAS`: Key alias inside the keystore.
- `RELEASE_STORE_PASSWORD`: Keystore password.
- `RELEASE_KEY_PASSWORD`: Key password (defaults to `RELEASE_STORE_PASSWORD`).

For local smoke tests only, pass `--allow-debug-sign` to sign a release build with `debug.keystore`.

### `scripts/package-release.sh`

Collects the built release APK (plus optional `.aab` and `mapping.txt`), verifies its signature with `apksigner`, copies normalized artifacts into `./bundled-apk/releases/v<version>/`, updates `./bundled-apk/<app>-latest.apk`, writes `release-manifest.json` and `SHA256SUMS.txt`, and builds a `.zip` or `.tar.gz` bundle archive.

Options:
- `--apk <path>`, `--aab <path>`, `--mapping <path>`: Override artifact discovery.
- `--build-if-missing`: Automatically invoke `build-release.sh` if no release APK exists.
- `--archive <auto|zip|tar.gz|none>`: Choose archive format (default `auto`).
- `--clean`: Recreate `releases/v<version>/` cleanly.

### `scripts/verify.sh`

Runs validation checks on:
1. Script presence, `bash -n` syntax, executable permissions, and `shellcheck` (if installed).
2. `./bundled-apk/` directory, Android SDK tools (`apksigner`, `zipalign`, `aapt2`), and `debug.keystore`.
3. Every `.apk` in `./bundled-apk/` (or `--apk <path>`): non-empty check, `zipalign -c -v 4`, `apksigner verify --print-certs`, `aapt2 dump badging`, and `.sha256` / `SHA256SUMS.txt` verification.
4. Project unit tests when `--run-tests` is passed.

Options:
- `--scripts-only`: Validate scripts and `./bundled-apk/` only.
- `--require-apk`: Fail if no APK files are present in `./bundled-apk/`.
- `--apk <path>`: Verify a single APK path.
- `--run-tests`: Run Gradle unit tests if present.
