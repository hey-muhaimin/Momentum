#!/usr/bin/env bash
# Shared utilities, environment discovery, logging, versioning, and signing helpers
# for Momentum Android build, packaging, and verification scripts.

if [[ -n "${_MOMENTUM_COMMON_SH_LOADED:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi
readonly _MOMENTUM_COMMON_SH_LOADED=1

# Ensure strict mode when sourced by scripts
set -euo pipefail

# ------------------------------------------------------------------------------
# Directory & Project Defaults
# ------------------------------------------------------------------------------
COMMON_SH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${ROOT_DIR:-$(cd "${COMMON_SH_DIR}/.." && pwd)}"
SCRIPTS_DIR="${ROOT_DIR}/scripts"
BUNDLED_APK_DIR="${BUNDLED_APK_DIR:-${ROOT_DIR}/bundled-apk}"
RELEASES_DIR="${RELEASES_DIR:-${BUNDLED_APK_DIR}/releases}"

APP_NAME="${APP_NAME:-momentum}"
BUILD_DIR_DEBUG="${BUILD_DIR_DEBUG:-${ROOT_DIR}/out/Debug}"
BUILD_DIR_RELEASE="${BUILD_DIR_RELEASE:-${ROOT_DIR}/out/Release}"

# ------------------------------------------------------------------------------
# Logging Helpers
# ------------------------------------------------------------------------------
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  _C_RESET=$'\033[0m'
  _C_BOLD=$'\033[1m'
  _C_RED=$'\033[0;31m'
  _C_GREEN=$'\033[0;32m'
  _C_YELLOW=$'\033[0;33m'
  _C_BLUE=$'\033[0;34m'
  _C_CYAN=$'\033[0;36m'
else
  _C_RESET=""
  _C_BOLD=""
  _C_RED=""
  _C_GREEN=""
  _C_YELLOW=""
  _C_BLUE=""
  _C_CYAN=""
fi

log_info() {
  printf '%s[INFO]%s %s\n' "${_C_BLUE}" "${_C_RESET}" "$*"
}

log_step() {
  printf '%s==>%s %s%s%s\n' "${_C_CYAN}" "${_C_RESET}" "${_C_BOLD}" "$*" "${_C_RESET}"
}

log_success() {
  printf '%s[OK]%s %s\n' "${_C_GREEN}" "${_C_RESET}" "$*"
}

log_warn() {
  printf '%s[WARN]%s %s\n' "${_C_YELLOW}" "${_C_RESET}" "$*" >&2
}

log_error() {
  printf '%s[ERROR]%s %s\n' "${_C_RED}" "${_C_RESET}" "$*" >&2
}

die() {
  log_error "$*"
  exit 1
}

# ------------------------------------------------------------------------------
# Command & Tool Discovery
# ------------------------------------------------------------------------------
has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

require_cmd() {
  local cmd="$1"
  local hint="${2:-Install '${cmd}' and ensure it is on PATH.}"
  if ! has_cmd "${cmd}"; then
    die "Required command '${cmd}' not found. ${hint}"
  fi
}

detect_android_sdk() {
  local candidate=""
  for candidate in \
    "${ANDROID_SDK_ROOT:-}" \
    "${ANDROID_HOME:-}" \
    "/opt/android/sdk" \
    "${HOME}/Android/Sdk" \
    "${HOME}/Library/Android/sdk"; do
    if [[ -n "${candidate}" && -d "${candidate}" ]]; then
      echo "${candidate}"
      return 0
    fi
  done
  return 1
}

find_sdk_build_tool() {
  local tool="$1"
  local sdk_root=""
  if sdk_root="$(detect_android_sdk 2>/dev/null)"; then
    if [[ -d "${sdk_root}/build-tools" ]]; then
      local latest_dir=""
      latest_dir="$(ls -1 "${sdk_root}/build-tools" 2>/dev/null | sort -V | tail -n 1 || true)"
      if [[ -n "${latest_dir}" && -x "${sdk_root}/build-tools/${latest_dir}/${tool}" ]]; then
        echo "${sdk_root}/build-tools/${latest_dir}/${tool}"
        return 0
      fi
    fi
  fi

  if has_cmd "${tool}"; then
    command -v "${tool}"
    return 0
  fi
  return 1
}

resolve_gradle_cmd() {
  if [[ -x "${ROOT_DIR}/gradlew" ]]; then
    echo "${ROOT_DIR}/gradlew"
    return 0
  elif has_cmd gradle; then
    command -v gradle
    return 0
  fi
  return 1
}

resolve_ninja_cmd() {
  if has_cmd autoninja; then
    command -v autoninja
    return 0
  elif has_cmd ninja; then
    command -v ninja
    return 0
  fi
  return 1
}

detect_build_system() {
  if [[ -f "${ROOT_DIR}/settings.gradle.kts" || -f "${ROOT_DIR}/settings.gradle" || -f "${ROOT_DIR}/build.gradle.kts" || -f "${ROOT_DIR}/build.gradle" ]]; then
    echo "gradle"
    return 0
  fi

  if [[ -f "${ROOT_DIR}/BUILD.gn" ]] && has_cmd gn && resolve_ninja_cmd >/dev/null 2>&1; then
    if [[ -d "${ROOT_DIR}/build" ]]; then
      echo "chromium"
      return 0
    fi
  fi

  echo "none"
  return 0
}

# ------------------------------------------------------------------------------
# Directory & File Helpers
# ------------------------------------------------------------------------------
ensure_bundled_apk_dir() {
  mkdir -p "${BUNDLED_APK_DIR}"
}

ensure_releases_dir() {
  ensure_bundled_apk_dir
  mkdir -p "${RELEASES_DIR}"
}

compute_sha256() {
  local file_path="$1"
  if [[ ! -f "${file_path}" ]]; then
    die "Cannot compute SHA-256: file not found: ${file_path}"
  fi
  if has_cmd sha256sum; then
    sha256sum "${file_path}" | awk '{print $1}'
  elif has_cmd shasum; then
    shasum -a 256 "${file_path}" | awk '{print $1}'
  elif has_cmd openssl; then
    openssl dgst -sha256 "${file_path}" | awk '{print $NF}'
  else
    die "No SHA-256 utility found (tried sha256sum, shasum, openssl)."
  fi
}

write_sha256_file() {
  local file_path="$1"
  local hash=""
  hash="$(compute_sha256 "${file_path}")"
  local base_name=""
  base_name="$(basename "${file_path}")"
  printf '%s  %s\n' "${hash}" "${base_name}" > "${file_path}.sha256"
  echo "${hash}"
}

human_file_size() {
  local file_path="$1"
  local bytes=0
  bytes="$(wc -c < "${file_path}" | tr -d '[:space:]')"
  if (( bytes >= 1048576 )); then
    awk -v b="${bytes}" 'BEGIN { printf "%.2f MiB (%d bytes)", b / 1048576, b }'
  elif (( bytes >= 1024 )); then
    awk -v b="${bytes}" 'BEGIN { printf "%.1f KiB (%d bytes)", b / 1024, b }'
  else
    printf '%d bytes' "${bytes}"
  fi
}

# ------------------------------------------------------------------------------
# Version Discovery Helpers
# ------------------------------------------------------------------------------
detect_version_source() {
  if [[ -f "${ROOT_DIR}/version.properties" ]]; then
    echo "${ROOT_DIR}/version.properties"
  elif [[ -f "${ROOT_DIR}/chrome/VERSION" ]]; then
    echo "${ROOT_DIR}/chrome/VERSION"
  elif [[ -f "${ROOT_DIR}/app/build.gradle.kts" ]]; then
    echo "${ROOT_DIR}/app/build.gradle.kts"
  elif [[ -f "${ROOT_DIR}/app/build.gradle" ]]; then
    echo "${ROOT_DIR}/app/build.gradle"
  elif [[ -f "${ROOT_DIR}/VERSION" ]]; then
    echo "${ROOT_DIR}/VERSION"
  else
    echo ""
  fi
}

_read_prop() {
  local file="$1"
  local key="$2"
  grep -E "^[[:space:]]*${key}[[:space:]]*=" "${file}" 2>/dev/null \
    | head -n 1 \
    | sed -E "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//; s/[[:space:]]*$//; s/^[\"']//; s/[\"']$//"
}

get_version_name() {
  local ver=""
  if [[ -f "${ROOT_DIR}/version.properties" ]]; then
    ver="$(_read_prop "${ROOT_DIR}/version.properties" "VERSION_NAME" || true)"
  fi
  if [[ -z "${ver}" && -f "${ROOT_DIR}/chrome/VERSION" ]]; then
    local maj min bld pat
    maj="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "MAJOR" || true)"
    min="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "MINOR" || true)"
    bld="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "BUILD" || true)"
    pat="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "PATCH" || true)"
    if [[ -n "${maj}" && -n "${min}" && -n "${bld}" && -n "${pat}" ]]; then
      ver="${maj}.${min}.${bld}.${pat}"
    fi
  fi
  if [[ -z "${ver}" && -f "${ROOT_DIR}/app/build.gradle.kts" ]]; then
    ver="$(grep -E 'versionName[[:space:]]*=' "${ROOT_DIR}/app/build.gradle.kts" 2>/dev/null \
      | head -n 1 | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/' || true)"
  fi
  if [[ -z "${ver}" && -f "${ROOT_DIR}/app/build.gradle" ]]; then
    ver="$(grep -E 'versionName[[:space:]]+' "${ROOT_DIR}/app/build.gradle" 2>/dev/null \
      | head -n 1 | sed -E 's/.*versionName[[:space:]]+["'\'']([^"'\'']+)["'\''].*/\1/' || true)"
  fi
  if [[ -z "${ver}" && -f "${ROOT_DIR}/VERSION" ]]; then
    ver="$(head -n 1 "${ROOT_DIR}/VERSION" | tr -d '[:space:]')"
  fi
  if [[ -z "${ver}" ]] && has_cmd git && git -C "${ROOT_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    ver="$(git -C "${ROOT_DIR}" describe --tags --abbrev=0 2>/dev/null | sed 's/^v//' || true)"
  fi
  echo "${ver:-0.1.0}"
}

get_version_code() {
  local code=""
  if [[ -f "${ROOT_DIR}/version.properties" ]]; then
    code="$(_read_prop "${ROOT_DIR}/version.properties" "VERSION_CODE" || true)"
  fi
  if [[ -z "${code}" && -f "${ROOT_DIR}/app/build.gradle.kts" ]]; then
    code="$(grep -E 'versionCode[[:space:]]*=' "${ROOT_DIR}/app/build.gradle.kts" 2>/dev/null \
      | head -n 1 | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/' || true)"
  fi
  if [[ -z "${code}" && -f "${ROOT_DIR}/app/build.gradle" ]]; then
    code="$(grep -E 'versionCode[[:space:]]+' "${ROOT_DIR}/app/build.gradle" 2>/dev/null \
      | head -n 1 | sed -E 's/.*versionCode[[:space:]]+([0-9]+).*/\1/' || true)"
  fi
  if [[ -z "${code}" && -f "${ROOT_DIR}/chrome/VERSION" ]]; then
    local bld pat
    bld="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "BUILD" || true)"
    pat="$(_read_prop "${ROOT_DIR}/chrome/VERSION" "PATCH" || true)"
    if [[ -n "${bld}" && -n "${pat}" ]]; then
      code="$(( bld * 1000 + pat ))"
    fi
  fi
  if [[ -z "${code}" ]] && has_cmd git && git -C "${ROOT_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    code="$(git -C "${ROOT_DIR}" rev-list --count HEAD 2>/dev/null || true)"
  fi
  if [[ -z "${code}" || ! "${code}" =~ ^[0-9]+$ ]]; then
    code="1"
  fi
  echo "${code}"
}

# ------------------------------------------------------------------------------
# Signing & Alignment Helpers
# ------------------------------------------------------------------------------
resolve_debug_keystore() {
  local ks="${DEBUG_KEYSTORE:-${ROOT_DIR}/debug.keystore}"
  if [[ -f "${ks}" ]]; then
    echo "${ks}"
    return 0
  fi
  if [[ -f "${HOME}/.android/debug.keystore" ]]; then
    echo "${HOME}/.android/debug.keystore"
    return 0
  fi
  return 1
}

is_apk_signed() {
  local apk_path="$1"
  local apksigner_bin=""
  if apksigner_bin="$(find_sdk_build_tool apksigner 2>/dev/null)"; then
    "${apksigner_bin}" verify "${apk_path}" >/dev/null 2>&1
    return $?
  fi
  return 1
}

align_and_sign_apk() {
  local input_apk="$1"
  local output_apk="$2"
  local keystore="$3"
  local key_alias="$4"
  local store_pass="$5"
  local key_pass="${6:-${store_pass}}"

  if [[ ! -f "${input_apk}" ]]; then
    die "Input APK does not exist: ${input_apk}"
  fi
  if [[ ! -f "${keystore}" ]]; then
    die "Keystore not found: ${keystore}"
  fi

  local tmp_aligned=""
  tmp_aligned="$(mktemp "${TMPDIR:-/tmp}/aligned-XXXXXX.apk")"
  trap 'rm -f "${tmp_aligned:-}"' RETURN

  local zipalign_bin=""
  if zipalign_bin="$(find_sdk_build_tool zipalign 2>/dev/null)"; then
    log_info "Aligning APK with $(basename "${zipalign_bin}")..."
    "${zipalign_bin}" -p -f 4 "${input_apk}" "${tmp_aligned}"
  else
    log_warn "zipalign not found in Android SDK build-tools; skipping alignment step."
    cp "${input_apk}" "${tmp_aligned}"
  fi

  local apksigner_bin=""
  if apksigner_bin="$(find_sdk_build_tool apksigner 2>/dev/null)"; then
    log_info "Signing APK with $(basename "${apksigner_bin}") (alias: ${key_alias})..."
    "${apksigner_bin}" sign \
      --ks "${keystore}" \
      --ks-key-alias "${key_alias}" \
      --ks-pass "pass:${store_pass}" \
      --key-pass "pass:${key_pass}" \
      --out "${output_apk}" \
      "${tmp_aligned}"
  else
    die "apksigner not found in Android SDK build-tools or PATH; cannot sign ${output_apk}."
  fi
}

stage_apk_to_bundled() {
  local src_apk="$1"
  local versioned_name="$2"
  local latest_alias="${3:-}"

  ensure_bundled_apk_dir
  local dest_path="${BUNDLED_APK_DIR}/${versioned_name}"
  cp -f "${src_apk}" "${dest_path}"
  local sha=""
  sha="$(write_sha256_file "${dest_path}")"
  log_success "Staged APK: ${dest_path} ($(human_file_size "${dest_path}"), sha256=${sha:0:12}...)"

  if [[ -n "${latest_alias}" ]]; then
    local alias_path="${BUNDLED_APK_DIR}/${latest_alias}"
    cp -f "${dest_path}" "${alias_path}"
    write_sha256_file "${alias_path}" >/dev/null
    log_info "Updated alias: ${alias_path}"
  fi
}
