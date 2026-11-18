#!/usr/bin/env bash
# Build the Momentum debug APK and stage it in ./bundled-apk/.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Build a debug APK (Gradle or Chromium GN/Ninja) and copy it into ./bundled-apk/.

Options:
  --target <name>       Gradle task (default: :app:assembleDebug) or GN target
                        (default: chrome_public_apk)
  --out-dir <dir>       Chromium GN output directory (default: out/Debug)
  --arch <cpu>          Target CPU architecture for GN builds: arm64, arm, x64, x86
                        (default: arm64)
  --output-name <name>  Override output filename inside ./bundled-apk/
  --clean               Clean build outputs before building
  --no-bundle           Build only; do not copy APK to ./bundled-apk/
  -h, --help            Show this help message
EOF
}

TARGET=""
OUT_DIR="${BUILD_DIR_DEBUG}"
TARGET_ARCH="arm64"
OUTPUT_NAME=""
CLEAN=0
NO_BUNDLE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || die "Missing value for --target"
      TARGET="$2"
      shift 2
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
    --output-name)
      [[ $# -ge 2 ]] || die "Missing value for --output-name"
      OUTPUT_NAME="$2"
      shift 2
      ;;
    --clean)
      CLEAN=1
      shift
      ;;
    --no-bundle)
      NO_BUNDLE=1
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

log_step "Building Debug APK (build_system=${BUILD_SYSTEM}, version=${VERSION_NAME})"

BUILT_APK=""

case "${BUILD_SYSTEM}" in
  gradle)
    GRADLE_CMD="$(resolve_gradle_cmd)" || die "Gradle wrapper or 'gradle' binary not found."
    GRADLE_TASK="${TARGET:-:app:assembleDebug}"

    if [[ "${CLEAN}" -eq 1 ]]; then
      log_info "Cleaning Gradle build outputs..."
      (cd "${ROOT_DIR}" && "${GRADLE_CMD}" clean)
    fi

    log_info "Running ${GRADLE_CMD} ${GRADLE_TASK}..."
    (cd "${ROOT_DIR}" && "${GRADLE_CMD}" "${GRADLE_TASK}")

    BUILT_APK="$(find "${ROOT_DIR}" -path "*/build/outputs/apk/debug/*.apk" -type f 2>/dev/null | sort | head -n 1 || true)"
    [[ -n "${BUILT_APK}" && -f "${BUILT_APK}" ]] || die "Debug build completed, but no debug APK was found under */build/outputs/apk/debug/."
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
      log_info "Generating debug args.gn in ${OUT_DIR} (arch=${TARGET_ARCH})..."
      cat > "${OUT_DIR}/args.gn" <<EOF
target_os = "android"
target_cpu = "${TARGET_ARCH}"
is_debug = true
is_component_build = true
symbol_level = 1
ffmpeg_branding = "Chrome"
proprietary_codecs = true
enable_desktop_android_extensions = true
enable_guest_view = true
enable_platform_apps = false
EOF
    fi

    log_info "Running gn gen ${OUT_DIR}..."
    (cd "${ROOT_DIR}" && gn gen "${OUT_DIR}")

    log_info "Building target ${GN_TARGET} with $(basename "${NINJA_CMD}")..."
    (cd "${ROOT_DIR}" && "${NINJA_CMD}" -C "${OUT_DIR}" "${GN_TARGET}")

    BUILT_APK="$(find "${OUT_DIR}/apks" -maxdepth 2 -name "*.apk" -type f 2>/dev/null | sort | head -n 1 || true)"
    [[ -n "${BUILT_APK}" && -f "${BUILT_APK}" ]] || die "GN build finished, but no APK was found in ${OUT_DIR}/apks."
    ;;

  none)
    die "No active Android build configuration detected in ${ROOT_DIR}.
Expected either:
  - A Gradle project (settings.gradle.kts / build.gradle.kts / app/), or
  - A full Chromium/Ultimatum checkout with build/ and depot_tools (gn + autoninja/ninja) on PATH."
    ;;
esac

if ! is_apk_signed "${BUILT_APK}"; then
  DEBUG_KS="$(resolve_debug_keystore)" || die "APK is unsigned and no debug.keystore was found."
  SIGNED_TMP="$(mktemp "${TMPDIR:-/tmp}/${APP_NAME}-debug-XXXXXX.apk")"
  trap 'rm -f "${SIGNED_TMP:-}"' EXIT
  align_and_sign_apk "${BUILT_APK}" "${SIGNED_TMP}" "${DEBUG_KS}" "androiddebugkey" "android" "android"
  BUILT_APK="${SIGNED_TMP}"
fi

if [[ "${NO_BUNDLE}" -eq 0 ]]; then
  VERSIONED_APK_NAME="${OUTPUT_NAME:-${APP_NAME}-debug-${VERSION_NAME}.apk}"
  stage_apk_to_bundled "${BUILT_APK}" "${VERSIONED_APK_NAME}" "${APP_NAME}-debug.apk"
else
  log_success "Built debug APK (not staged due to --no-bundle): ${BUILT_APK}"
fi
