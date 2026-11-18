#!/usr/bin/env bash
# Verify build scripts, Android SDK toolchain, APK artifacts in ./bundled-apk/, and optional project tests.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Verify Momentum scripts, Android SDK tooling, and APK artifacts in ./bundled-apk/.

Options:
  --apk <path>        Verify a specific APK file instead of scanning ./bundled-apk/
  --require-apk       Fail if no APK artifacts are present in ./bundled-apk/
  --scripts-only      Verify only scripts/ syntax, permissions, and ./bundled-apk/ directory
  --run-tests         Also run project unit tests / lint if configured
  -h, --help          Show this help message
EOF
}

TARGET_APK=""
REQUIRE_APK=0
SCRIPTS_ONLY=0
RUN_TESTS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      [[ $# -ge 2 ]] || die "Missing value for --apk"
      TARGET_APK="$2"
      shift 2
      ;;
    --require-apk)
      REQUIRE_APK=1
      shift
      ;;
    --scripts-only)
      SCRIPTS_ONLY=1
      shift
      ;;
    --run-tests)
      RUN_TESTS=1
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

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

record_pass() {
  PASS_COUNT=$(( PASS_COUNT + 1 ))
  log_success "$1"
}

record_fail() {
  FAIL_COUNT=$(( FAIL_COUNT + 1 ))
  log_error "$1"
}

record_warn() {
  WARN_COUNT=$(( WARN_COUNT + 1 ))
  log_warn "$1"
}

# 1. Verify scripts & ./bundled-apk directory
log_step "1. Verifying scripts and ./bundled-apk directory"

if [[ -d "${BUNDLED_APK_DIR}" ]]; then
  record_pass "Directory exists: ${BUNDLED_APK_DIR}"
else
  record_fail "Missing directory: ${BUNDLED_APK_DIR}"
fi

expected_scripts=(
  "common.sh"
  "bump-version.sh"
  "build-debug.sh"
  "build-release.sh"
  "package-release.sh"
  "verify.sh"
)

for name in "${expected_scripts[@]}"; do
  s="${SCRIPTS_DIR}/${name}"
  if [[ ! -f "${s}" ]]; then
    record_fail "Missing script: ${s}"
    continue
  fi
  if bash -n "${s}"; then
    record_pass "Syntax valid (bash -n): scripts/${name}"
  else
    record_fail "Syntax error in scripts/${name}"
  fi
  if [[ "${name}" != "common.sh" && ! -x "${s}" ]]; then
    record_warn "Script is not executable (chmod +x recommended): scripts/${name}"
  fi
done

if [[ -f "${SCRIPTS_DIR}/README.md" ]]; then
  record_pass "Documentation exists: scripts/README.md"
else
  record_fail "Missing documentation: scripts/README.md"
fi

if has_cmd shellcheck; then
  if shellcheck -x "${SCRIPTS_DIR}"/*.sh; then
    record_pass "shellcheck passed for all scripts"
  else
    record_fail "shellcheck reported warnings/errors"
  fi
else
  log_info "shellcheck not installed; skipped static lint."
fi

if [[ "${SCRIPTS_ONLY}" -eq 1 ]]; then
  log_step "Verification Summary (--scripts-only): ${PASS_COUNT} passed, ${WARN_COUNT} warnings, ${FAIL_COUNT} failed"
  [[ "${FAIL_COUNT}" -eq 0 ]]
  exit $?
fi

# 2. Verify Android SDK & Build Toolchain
log_step "2. Verifying Android SDK & Build Environment"

if sdk_root="$(detect_android_sdk 2>/dev/null)"; then
  record_pass "Android SDK detected: ${sdk_root}"
else
  record_warn "ANDROID_SDK_ROOT / ANDROID_HOME not found."
fi

for tool in apksigner zipalign aapt2; do
  if tool_path="$(find_sdk_build_tool "${tool}" 2>/dev/null)"; then
    record_pass "Found ${tool}: ${tool_path}"
  else
    record_warn "SDK build-tool '${tool}' not found."
  fi
done

if ks="$(resolve_debug_keystore 2>/dev/null)"; then
  record_pass "Debug keystore available: ${ks}"
else
  record_warn "debug.keystore not found."
fi

BUILD_SYSTEM="$(detect_build_system)"
log_info "Detected build system: ${BUILD_SYSTEM} | Version: $(get_version_name) (code $(get_version_code))"

# 3. Verify APK & Checksum Artifacts
log_step "3. Verifying APK artifacts and checksums"

declare -a APKS_TO_CHECK=()
if [[ -n "${TARGET_APK}" ]]; then
  [[ -f "${TARGET_APK}" ]] || die "Specified APK not found: ${TARGET_APK}"
  APKS_TO_CHECK+=("${TARGET_APK}")
elif [[ -d "${BUNDLED_APK_DIR}" ]]; then
  while IFS= read -r apk_file; do
    APKS_TO_CHECK+=("${apk_file}")
  done < <(find "${BUNDLED_APK_DIR}" -name "*.apk" -type f 2>/dev/null | sort)
fi

if [[ ${#APKS_TO_CHECK[@]} -eq 0 ]]; then
  if [[ "${REQUIRE_APK}" -eq 1 ]]; then
    record_fail "No APK files found in ${BUNDLED_APK_DIR} (--require-apk is set)."
  else
    record_warn "No APK files currently staged in ${BUNDLED_APK_DIR}."
  fi
else
  ZIPALIGN_BIN="$(find_sdk_build_tool zipalign 2>/dev/null || true)"
  APKSIGNER_BIN="$(find_sdk_build_tool apksigner 2>/dev/null || true)"
  AAPT2_BIN="$(find_sdk_build_tool aapt2 2>/dev/null || true)"

  for apk in "${APKS_TO_CHECK[@]}"; do
    log_info "Checking APK: ${apk} ($(human_file_size "${apk}"))"
    if [[ ! -s "${apk}" ]]; then
      record_fail "APK file is empty: ${apk}"
      continue
    fi

    if [[ -n "${ZIPALIGN_BIN}" ]]; then
      if "${ZIPALIGN_BIN}" -c -v 4 "${apk}" >/dev/null 2>&1; then
        record_pass "zipalign 4-byte alignment OK: $(basename "${apk}")"
      else
        record_fail "zipalign check failed: ${apk}"
      fi
    fi

    if [[ -n "${APKSIGNER_BIN}" ]]; then
      if "${APKSIGNER_BIN}" verify --print-certs "${apk}" >/dev/null 2>&1; then
        record_pass "apksigner signature verified: $(basename "${apk}")"
      else
        record_fail "apksigner signature verification failed: ${apk}"
      fi
    fi

    if [[ -n "${AAPT2_BIN}" ]]; then
      if badging="$("${AAPT2_BIN}" dump badging "${apk}" 2>/dev/null)"; then
        pkg_line="$(printf '%s\n' "${badging}" | head -n 1)"
        record_pass "aapt2 manifest OK (${pkg_line})"
      else
        record_fail "aapt2 failed to parse APK: ${apk}"
      fi
    fi

    if [[ -f "${apk}.sha256" ]]; then
      expected_hash="$(awk '{print $1}' "${apk}.sha256")"
      actual_hash="$(compute_sha256 "${apk}")"
      if [[ "${expected_hash}" == "${actual_hash}" ]]; then
        record_pass "SHA-256 checksum matches: $(basename "${apk}.sha256")"
      else
        record_fail "SHA-256 checksum mismatch for ${apk} (expected ${expected_hash}, got ${actual_hash})"
      fi
    fi
  done
fi

# Verify any SHA256SUMS.txt files under ./bundled-apk/releases/
if [[ -d "${RELEASES_DIR}" ]]; then
  while IFS= read -r sum_file; do
    sum_dir="$(dirname "${sum_file}")"
    if (cd "${sum_dir}" && sha256sum -c "$(basename "${sum_file}")" >/dev/null 2>&1); then
      record_pass "Release checksum manifest verified: ${sum_file}"
    else
      record_fail "Release checksum manifest failed: ${sum_file}"
    fi
  done < <(find "${RELEASES_DIR}" -name "SHA256SUMS.txt" -type f 2>/dev/null | sort)
fi

# 4. Optional Project Tests
if [[ "${RUN_TESTS}" -eq 1 ]]; then
  log_step "4. Running Project Tests"
  case "${BUILD_SYSTEM}" in
    gradle)
      GRADLE_CMD="$(resolve_gradle_cmd)" || die "Gradle not found."
      if (cd "${ROOT_DIR}" && "${GRADLE_CMD}" testDebugUnitTest); then
        record_pass "Gradle unit tests passed."
      else
        record_fail "Gradle unit tests failed."
      fi
      ;;
    *)
      record_warn "No Gradle test suite configured for --run-tests in current checkout."
      ;;
  esac
fi

log_step "Verification Summary: ${PASS_COUNT} passed, ${WARN_COUNT} warnings, ${FAIL_COUNT} failed"
[[ "${FAIL_COUNT}" -eq 0 ]]
