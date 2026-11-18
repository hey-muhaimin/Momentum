#!/usr/bin/env bash
# Build, align, and sign the Momentum release APK (and optional AAB) into ./bundled-apk/.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Build a release APK (and optional Android App Bundle), sign/align it, and stage it
into ./bundled-apk/.

Options:
  --target <name>         Gradle task (default: :app:assembleRelease) or GN target
                          (default: chrome_public_apk)
  --aab                   Also build an Android App Bundle (.aab) in Gradle mode
  --out-dir <dir>         Chromium GN output directory (default: out/Release)
  --arch <cpu>            Target CPU architecture for GN builds: arm64, arm, x64, x86
                          (default: arm64)
  --keystore <path>       Release keystore path (or env RELEASE_KEYSTORE)
  --alias <name>          Release key alias (or env RELEASE_KEY_ALIAS)
  --store-pass <pass>     Keystore password (or env RELEASE_STORE_PASSWORD)
  --key-pass <pass>       Key password (or env RELEASE_KEY_PASSWORD; defaults to store-pass)
  --allow-debug-sign      Allow signing release APK with debug.keystore (local testing only)
  --output-name <name>    Override output APK filename inside ./bundled-apk/
  --clean                 Clean build outputs before building
  -h, --help              Show this help message
EOF
}

TARGET=""
BUILD_AAB=0
OUT_DIR="${BUILD_DIR_RELEASE}"
TARGET_ARCH="arm64"
KEYSTORE_PATH="${RELEASE_KEYSTORE:-${KEYSTORE_PATH:-}}"
KEY_ALIAS="${RELEASE_KEY_ALIAS:-${KEY_ALIAS:-}}"
STORE_PASS="${RELEASE_STORE_PASSWORD:-${KEYSTORE_PASSWORD:-}}"
KEY_PASS="${RELEASE_KEY_PASSWORD:-${KEY_PASSWORD:-}}"
ALLOW_DEBUG_SIGN=0
OUTPUT_NAME=""
CLEAN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || die "Missing value for --target"
      TARGET="$2"
      shift 2
      ;;
    --aab|--bundle)
      BUILD_AAB=1
      shift
      ;;
    --out-dir)
      [[ $# -ge 2 ]] || die "Missing value for --out-dir"
      OUT_DIR="$2"
      shift 2
      ;;
    --arch)
      [[ $# -ge 2 ]] || die "Missing value for --arch"
      TARGET_ARCH="$2"
      shift 2
      ;;
    --keystore)
      [[ $# -ge 2 ]] || die "Missing value for --keystore"
      KEYSTORE_PATH="$2"
      shift 2
      ;;
    --alias)
      [[ $# -ge 2 ]] || die "Missing value for --alias"
      KEY_ALIAS="$2"
      shift 2
      ;;
    --store-pass)
      [[ $# -ge 2 ]] || die "Missing value for --store-pass"
      STORE_PASS="$2"
      shift 2
      ;;
    --key-pass)
      [[ $# -ge 2 ]] || die "Missing value for --key-pass"
      KEY_PASS="$2"
      shift 2
      ;;
    --allow-debug-sign)
      ALLOW_DEBUG_SIGN=1
      shift
      ;;
    --output-name)
      [[ $# -ge 2 ]] || die "Missing value for --output-name"
      OUTPUT_NAME="$2"
      shift 2
      ;;
    --clean)
      CLEAN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1 (run with --help for usage)"
      ;;
  esac
done

ensure_bundled_apk_dir
VERSION_NAME="$(get_version_name)"
BUILD_SYSTEM="$(detect_build_system)"

log_step "Building Release APK (build_system=${BUILD_SYSTEM}, version=${VERSION_NAME})"

BUILT_APK=""
BUILT_AAB=""

case "${BUILD_SYSTEM}" in
  gradle)
    GRADLE_CMD="$(resolve_gradle_cmd)" || die "Gradle wrapper or 'gradle' binary not found."
    GRADLE_TASK="${TARGET:-:app:assembleRelease}"

    if [[ "${CLEAN}" -eq 1 ]]; then
      log_info "Cleaning Gradle build outputs..."
      (cd "${ROOT_DIR}" && "${GRADLE_CMD}" clean)
    fi

    log_info "Running ${GRADLE_CMD} ${GRADLE_TASK}..."
    (cd "${ROOT_DIR}" && "${GRADLE_CMD}" "${GRADLE_TASK}")

    BUILT_APK="$(find "${ROOT_DIR}" -path "*/build/outputs/apk/release/*.apk" -type f 2>/dev/null | sort | head -n 1 || true)"
    [[ -n "${BUILT_APK}" && -f "${BUILT_APK}" ]] || die "Release build completed, but no release APK was found under */build/outputs/apk/release/."

    if [[ "${BUILD_AAB}" -eq 1 ]]; then
      log_info "Running ${GRADLE_CMD} :app:bundleRelease..."
      (cd "${ROOT_DIR}" && "${GRADLE_CMD}" :app:bundleRelease)
      BUILT_AAB="$(find "${ROOT_DIR}" -path "*/build/outputs/bundle/release/*.aab" -type f 2>/dev/null | sort | head -n 1 || true)"
    fi
    ;;

  chromium)
    require_cmd gn "Install Chromium depot_tools so 'gn' is on PATH."
    NINJA_CMD="$(resolve_ninja_cmd)" || die "Neither 'autoninja' nor 'ninja' found on PATH."
    GN_TARGET="${TARGET:-chrome_public_apk}"

    if [[ "${CLEAN}" -eq 1 && -d "${OUT_DIR}" ]]; then
      log_info "Cleaning ${OUT_DIR}..."
      rm -rf "${OUT_DIR}"
    fi

    mkdir -p "${OUT_DIR}"
    if [[ ! -f "${OUT_DIR}/args.gn" ]]; then
      log_info "Generating release args.gn in ${OUT_DIR} (arch=${TARGET_ARCH})..."
      cat > "${OUT_DIR}/args.gn" <<EOF
target_os = "android"
target_cpu = "${TARGET_ARCH}"
is_debug = false
is_component_build = false
symbol_level = 0
blink_symbol_level = 0
v8_symbol_level = 0
ffmpeg_branding = "Chrome"
proprietary_codecs = true
enable_desktop_android_extensions = true
enable_guest_view = true
enable_platform_apps = false
EOF
    fi

    log_info "Running gn gen ${OUT_DIR}..."
    (cd "${ROOT_DIR}" && gn gen "${OUT_DIR}")

    log_info "Building release target ${GN_TARGET} with $(basename "${NINJA_CMD}")..."
    (cd "${ROOT_DIR}" && "${NINJA_CMD}" -C "${OUT_DIR}" "${GN_TARGET}")

    BUILT_APK="$(find "${OUT_DIR}/apks" -maxdepth 2 -name "*.apk" -type f 2>/dev/null | sort | head -n 1 || true)"
    [[ -n "${BUILT_APK}" && -f "${BUILT_APK}" ]] || die "GN release build finished, but no APK was found in ${OUT_DIR}/apks."
    ;;

  none)
    die "No active Android build configuration detected in ${ROOT_DIR}.
Expected either:
  - A Gradle project (settings.gradle.kts / build.gradle.kts / app/), or
  - A full Momentum (Chromium) checkout with build/ and depot_tools (gn + autoninja/ninja) on PATH."
    ;;
esac

SIGNED_TMP="$(mktemp "${TMPDIR:-/tmp}/${APP_NAME}-release-XXXXXX.apk")"
trap 'rm -f "${SIGNED_TMP:-}"' EXIT

if [[ -n "${KEYSTORE_PATH}" ]]; then
  [[ -f "${KEYSTORE_PATH}" ]] || die "Release keystore does not exist: ${KEYSTORE_PATH}"
  [[ -n "${KEY_ALIAS}" ]] || die "Release key alias is required (--alias or RELEASE_KEY_ALIAS)."
  [[ -n "${STORE_PASS}" ]] || die "Release keystore password is required (--store-pass or RELEASE_STORE_PASSWORD)."
  KEY_PASS="${KEY_PASS:-${STORE_PASS}}"
  align_and_sign_apk "${BUILT_APK}" "${SIGNED_TMP}" "${KEYSTORE_PATH}" "${KEY_ALIAS}" "${STORE_PASS}" "${KEY_PASS}"
  BUILT_APK="${SIGNED_TMP}"
elif is_apk_signed "${BUILT_APK}"; then
  log_info "APK is already signed by the build system."
elif [[ "${ALLOW_DEBUG_SIGN}" -eq 1 ]]; then
  DEBUG_KS="$(resolve_debug_keystore)" || die "No debug.keystore found for --allow-debug-sign."
  log_warn "Signing release APK with debug.keystore (--allow-debug-sign). Do NOT distribute to production!"
  align_and_sign_apk "${BUILT_APK}" "${SIGNED_TMP}" "${DEBUG_KS}" "androiddebugkey" "android" "android"
  BUILT_APK="${SIGNED_TMP}"
else
  die "Release APK is unsigned and no release keystore was configured.
Set RELEASE_KEYSTORE, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD (or pass --keystore / --alias / --store-pass),
or pass --allow-debug-sign for local smoke testing."
fi

VERSIONED_APK_NAME="${OUTPUT_NAME:-${APP_NAME}-release-${VERSION_NAME}.apk}"
stage_apk_to_bundled "${BUILT_APK}" "${VERSIONED_APK_NAME}" "${APP_NAME}-release.apk"

if [[ -n "${BUILT_AAB}" && -f "${BUILT_AAB}" ]]; then
  AAB_DEST="${BUNDLED_APK_DIR}/${APP_NAME}-release-${VERSION_NAME}.aab"
  cp -f "${BUILT_AAB}" "${AAB_DEST}"
  write_sha256_file "${AAB_DEST}" >/dev/null
  log_success "Staged AAB: ${AAB_DEST} ($(human_file_size "${AAB_DEST}"))"
fi
