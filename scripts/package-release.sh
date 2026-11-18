#!/usr/bin/env bash
# Package release APK/AAB/mapping artifacts into a reproducible release bundle under ./bundled-apk/releases/.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Package built release artifacts into ./bundled-apk/releases/v<version>/ along with
SHA256SUMS.txt, release-manifest.json, and an archive (.zip or .tar.gz).

Options:
  --apk <path>          Explicit path to release APK (default: auto-discover in ./bundled-apk/ or build outputs)
  --aab <path>          Optional path to release AAB (.aab)
  --mapping <path>      Optional path to ProGuard/R8 mapping.txt
  --version <ver>       Override versionName used for bundle naming
  --code <int>          Override versionCode used in bundle metadata
  --build-if-missing    Invoke build-release.sh if no release APK is found
  --archive <fmt>       Archive format: auto (default), zip, tar.gz, or none
  --clean               Remove existing release directory for this version before packaging
  -h, --help            Show this help message
EOF
}

APK_PATH=""
AAB_PATH=""
MAPPING_PATH=""
VERSION_OVERRIDE=""
CODE_OVERRIDE=""
BUILD_IF_MISSING=0
ARCHIVE_FMT="auto"
CLEAN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      [[ $# -ge 2 ]] || die "Missing value for --apk"
      APK_PATH="$2"
      shift 2
      ;;
    --aab)
      [[ $# -ge 2 ]] || die "Missing value for --aab"
      AAB_PATH="$2"
      shift 2
      ;;
    --mapping)
      [[ $# -ge 2 ]] || die "Missing value for --mapping"
      MAPPING_PATH="$2"
      shift 2
      ;;
    --version)
      [[ $# -ge 2 ]] || die "Missing value for --version"
      VERSION_OVERRIDE="$2"
      shift 2
      ;;
    --code)
      [[ $# -ge 2 ]] || die "Missing value for --code"
      CODE_OVERRIDE="$2"
      shift 2
      ;;
    --build-if-missing)
      BUILD_IF_MISSING=1
      shift
      ;;
    --archive)
      [[ $# -ge 2 ]] || die "Missing value for --archive"
      ARCHIVE_FMT="$2"
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

ensure_releases_dir

VERSION_NAME="${VERSION_OVERRIDE:-$(get_version_name)}"
VERSION_CODE="${CODE_OVERRIDE:-$(get_version_code)}"

discover_release_apk() {
  local candidates=(
    "${BUNDLED_APK_DIR}/${APP_NAME}-release-${VERSION_NAME}.apk"
    "${BUNDLED_APK_DIR}/${APP_NAME}-release.apk"
  )
  local c=""
  for c in "${candidates[@]}"; do
    if [[ -f "${c}" ]]; then
      echo "${c}"
      return 0
    fi
  done

  local found=""
  found="$(find "${BUNDLED_APK_DIR}" -maxdepth 1 -name "*release*.apk" -type f 2>/dev/null | sort | head -n 1 || true)"
  if [[ -n "${found}" ]]; then
    echo "${found}"
    return 0
  fi

  found="$(find "${ROOT_DIR}" -path "*/build/outputs/apk/release/*.apk" -o -path "${BUILD_DIR_RELEASE}/apks/*.apk" 2>/dev/null | sort | head -n 1 || true)"
  if [[ -n "${found}" ]]; then
    echo "${found}"
    return 0
  fi
  return 1
}

if [[ -z "${APK_PATH}" ]]; then
  if ! APK_PATH="$(discover_release_apk)"; then
    if [[ "${BUILD_IF_MISSING}" -eq 1 ]]; then
      log_info "No release APK found; running build-release.sh..."
      "${SCRIPT_DIR}/build-release.sh"
      APK_PATH="$(discover_release_apk)" || die "build-release.sh finished, but release APK could not be located."
    else
      die "No release APK found in ${BUNDLED_APK_DIR} or build outputs.
Run ./scripts/build-release.sh first, pass --apk <path>, or pass --build-if-missing."
    fi
  fi
fi

[[ -f "${APK_PATH}" ]] || die "Release APK not found: ${APK_PATH}"

if [[ -z "${AAB_PATH}" ]]; then
  for aab_cand in \
    "${BUNDLED_APK_DIR}/${APP_NAME}-release-${VERSION_NAME}.aab" \
    "$(find "${ROOT_DIR}" -path "*/build/outputs/bundle/release/*.aab" -type f 2>/dev/null | sort | head -n 1 || true)"; do
    if [[ -n "${aab_cand}" && -f "${aab_cand}" ]]; then
      AAB_PATH="${aab_cand}"
      break
    fi
  done
fi

if [[ -z "${MAPPING_PATH}" ]]; then
  MAPPING_PATH="$(find "${ROOT_DIR}" -path "*/build/outputs/mapping/release/mapping.txt" -type f 2>/dev/null | sort | head -n 1 || true)"
fi

# Verify APK signature before packaging if apksigner is available
if apksigner_bin="$(find_sdk_build_tool apksigner 2>/dev/null)"; then
  log_info "Verifying APK signature with $(basename "${apksigner_bin}")..."
  if ! "${apksigner_bin}" verify "${APK_PATH}" >/dev/null 2>&1; then
    die "APK failed apksigner verification: ${APK_PATH}. Sign it before packaging."
  fi
  log_success "APK signature verified."
else
  log_warn "apksigner not found; skipping pre-package signature verification."
fi

RELEASE_TAG="v${VERSION_NAME}"
BUNDLE_DIR="${RELEASES_DIR}/${RELEASE_TAG}"

if [[ "${CLEAN}" -eq 1 && -d "${BUNDLE_DIR}" ]]; then
  rm -rf "${BUNDLE_DIR}"
fi
mkdir -p "${BUNDLE_DIR}"

log_step "Packaging Release ${RELEASE_TAG} (versionCode=${VERSION_CODE})"

PACKAGED_APK_NAME="${APP_NAME}-${RELEASE_TAG}-vc${VERSION_CODE}.apk"
PACKAGED_APK_PATH="${BUNDLE_DIR}/${PACKAGED_APK_NAME}"
cp -f "${APK_PATH}" "${PACKAGED_APK_PATH}"

# Keep convenient latest APK at top of ./bundled-apk/
cp -f "${PACKAGED_APK_PATH}" "${BUNDLED_APK_DIR}/${APP_NAME}-latest.apk"
write_sha256_file "${BUNDLED_APK_DIR}/${APP_NAME}-latest.apk" >/dev/null

PACKAGED_AAB_NAME=""
if [[ -n "${AAB_PATH}" && -f "${AAB_PATH}" ]]; then
  PACKAGED_AAB_NAME="${APP_NAME}-${RELEASE_TAG}-vc${VERSION_CODE}.aab"
  cp -f "${AAB_PATH}" "${BUNDLE_DIR}/${PACKAGED_AAB_NAME}"
  log_info "Included AAB: ${PACKAGED_AAB_NAME}"
fi

PACKAGED_MAPPING_NAME=""
if [[ -n "${MAPPING_PATH}" && -f "${MAPPING_PATH}" ]]; then
  PACKAGED_MAPPING_NAME="${APP_NAME}-${RELEASE_TAG}-vc${VERSION_CODE}-mapping.txt"
  cp -f "${MAPPING_PATH}" "${BUNDLE_DIR}/${PACKAGED_MAPPING_NAME}"
  log_info "Included mapping: ${PACKAGED_MAPPING_NAME}"
fi

# Extract badging metadata if aapt2 is available
APK_PACKAGE=""
APK_MIN_SDK=""
APK_TARGET_SDK=""
if aapt2_bin="$(find_sdk_build_tool aapt2 2>/dev/null)"; then
  badging="$("${aapt2_bin}" dump badging "${PACKAGED_APK_PATH}" 2>/dev/null || true)"
  APK_PACKAGE="$(printf '%s\n' "${badging}" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1 || true)"
  APK_MIN_SDK="$(printf '%s\n' "${badging}" | sed -n "s/^sdkVersion:'\([^']*\)'.*/\1/p" | head -n 1 || true)"
  APK_TARGET_SDK="$(printf '%s\n' "${badging}" | sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" | head -n 1 || true)"
fi

GIT_COMMIT="unknown"
GIT_BRANCH="unknown"
if has_cmd git && git -C "${ROOT_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  GIT_COMMIT="$(git -C "${ROOT_DIR}" rev-parse --short HEAD 2>/dev/null || echo "unknown")"
  GIT_BRANCH="$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")"
fi

APK_SHA256="$(compute_sha256 "${PACKAGED_APK_PATH}")"
APK_SIZE_BYTES="$(wc -c < "${PACKAGED_APK_PATH}" | tr -d '[:space:]')"
BUILD_TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

cat > "${BUNDLE_DIR}/release-manifest.json" <<EOF
{
  "appName": "${APP_NAME}",
  "versionName": "${VERSION_NAME}",
  "versionCode": ${VERSION_CODE},
  "packageName": "${APK_PACKAGE}",
  "minSdk": "${APK_MIN_SDK}",
  "targetSdk": "${APK_TARGET_SDK}",
  "apkFile": "${PACKAGED_APK_NAME}",
  "apkSizeBytes": ${APK_SIZE_BYTES},
  "apkSha256": "${APK_SHA256}",
  "aabFile": "${PACKAGED_AAB_NAME}",
  "mappingFile": "${PACKAGED_MAPPING_NAME}",
  "gitCommit": "${GIT_COMMIT}",
  "gitBranch": "${GIT_BRANCH}",
  "packagedAtUtc": "${BUILD_TIMESTAMP}"
}
EOF

# Generate SHA256SUMS.txt for all packaged files
(
  cd "${BUNDLE_DIR}"
  rm -f SHA256SUMS.txt
  for artifact in *; do
    [[ -f "${artifact}" && "${artifact}" != "SHA256SUMS.txt" ]] || continue
    printf '%s  %s\n' "$(compute_sha256 "${artifact}")" "${artifact}" >> SHA256SUMS.txt
  done
)

# Optional archive creation
if [[ "${ARCHIVE_FMT}" == "auto" ]]; then
  if has_cmd zip; then
    ARCHIVE_FMT="zip"
  else
    ARCHIVE_FMT="tar.gz"
  fi
fi

ARCHIVE_PATH=""
case "${ARCHIVE_FMT}" in
  zip)
    require_cmd zip
    ARCHIVE_PATH="${RELEASES_DIR}/${APP_NAME}-${RELEASE_TAG}-bundle.zip"
    rm -f "${ARCHIVE_PATH}"
    (cd "${RELEASES_DIR}" && zip -rq "${ARCHIVE_PATH}" "${RELEASE_TAG}")
    write_sha256_file "${ARCHIVE_PATH}" >/dev/null
    ;;
  tar.gz|tgz)
    ARCHIVE_PATH="${RELEASES_DIR}/${APP_NAME}-${RELEASE_TAG}-bundle.tar.gz"
    tar -C "${RELEASES_DIR}" -czf "${ARCHIVE_PATH}" "${RELEASE_TAG}"
    write_sha256_file "${ARCHIVE_PATH}" >/dev/null
    ;;
  none)
    ;;
  *)
    die "Unsupported --archive format '${ARCHIVE_FMT}'. Use auto, zip, tar.gz, or none."
    ;;
esac

log_success "Release bundle ready: ${BUNDLE_DIR}"
log_info "Manifest : ${BUNDLE_DIR}/release-manifest.json"
log_info "Checksums: ${BUNDLE_DIR}/SHA256SUMS.txt"
if [[ -n "${ARCHIVE_PATH}" ]]; then
  log_info "Archive  : ${ARCHIVE_PATH} ($(human_file_size "${ARCHIVE_PATH}"))"
fi
