#!/usr/bin/env bash
# Bump or inspect Momentum's versionName and versionCode across project version files.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [COMMAND] [OPTIONS]

Commands:
  show              Print current versionName and versionCode (default)
  major             Increment major version (X.0.0) and versionCode
  minor             Increment minor version (X.Y.0) and versionCode
  patch             Increment patch version (X.Y.Z) and versionCode
  build             Increment 4th build/patch segment (for Chromium A.B.C.D) and versionCode
  set <version>     Set explicit versionName (e.g. 1.2.3 or 136.0.7103.42)

Options:
  --code <int>      Set explicit integer versionCode
  --no-code         Keep existing versionCode unchanged
  --file <path>     Target a specific version file (version.properties, chrome/VERSION,
                    app/build.gradle.kts, app/build.gradle, or VERSION)
  --init            Create version.properties if no version file exists yet
  --commit          Commit the version bump with git
  --tag             Create an annotated git tag v<version>
  --dry-run         Preview changes without writing files
  -h, --help        Show this help message
EOF
}

ACTION="show"
EXPLICIT_VERSION=""
EXPLICIT_CODE=""
KEEP_CODE=0
TARGET_FILE=""
INIT_IF_MISSING=0
DO_COMMIT=0
DO_TAG=0
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    show|major|minor|patch|build)
      ACTION="$1"
      shift
      ;;
    set)
      [[ $# -ge 2 ]] || die "Missing version argument after 'set'."
      ACTION="set"
      EXPLICIT_VERSION="$2"
      shift 2
      ;;
    --code)
      [[ $# -ge 2 ]] || die "Missing integer argument after '--code'."
      EXPLICIT_CODE="$2"
      [[ "${EXPLICIT_CODE}" =~ ^[0-9]+$ ]] || die "--code must be a non-negative integer."
      shift 2
      ;;
    --no-code)
      KEEP_CODE=1
      shift
      ;;
    --file)
      [[ $# -ge 2 ]] || die "Missing path argument after '--file'."
      TARGET_FILE="$2"
      shift 2
      ;;
    --init)
      INIT_IF_MISSING=1
      shift
      ;;
    --commit)
      DO_COMMIT=1
      shift
      ;;
    --tag)
      DO_TAG=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1 (run with --help for usage)"
      ;;
  esac
done

CURRENT_VER="$(get_version_name)"
CURRENT_CODE="$(get_version_code)"

if [[ "${ACTION}" == "show" ]]; then
  detected="$(detect_version_source)"
  log_info "Version Name : ${CURRENT_VER}"
  log_info "Version Code : ${CURRENT_CODE}"
  log_info "Version File : ${detected:-<none detected; using defaults>}"
  exit 0
fi

# Compute new versionName
compute_next_version() {
  local cur="$1"
  local mode="$2"

  if [[ "${mode}" == "set" ]]; then
    if [[ ! "${EXPLICIT_VERSION}" =~ ^[0-9]+(\.[0-9]+){1,3}([+-][A-Za-z0-9._-]+)?$ ]]; then
      die "Invalid version string '${EXPLICIT_VERSION}'. Expected semver (e.g. 1.2.3) or Chromium 4-part version."
    fi
    echo "${EXPLICIT_VERSION}"
    return 0
  fi

  local core="${cur%%[-+]*}"
  IFS='.' read -r -a parts <<< "${core}"
  local p0="${parts[0]:-0}"
  local p1="${parts[1]:-0}"
  local p2="${parts[2]:-0}"
  local p3="${parts[3]:-}"

  case "${mode}" in
    major)
      p0=$(( p0 + 1 ))
      p1=0
      p2=0
      [[ -n "${p3}" ]] && p3=0
      ;;
    minor)
      p1=$(( p1 + 1 ))
      p2=0
      [[ -n "${p3}" ]] && p3=0
      ;;
    patch)
      p2=$(( p2 + 1 ))
      [[ -n "${p3}" ]] && p3=0
      ;;
    build)
      if [[ -n "${p3}" ]]; then
        p3=$(( p3 + 1 ))
      else
        p2=$(( p2 + 1 ))
      fi
      ;;
  esac

  if [[ -n "${p3}" ]]; then
    echo "${p0}.${p1}.${p2}.${p3}"
  else
    echo "${p0}.${p1}.${p2}"
  fi
}

NEW_VER="$(compute_next_version "${CURRENT_VER}" "${ACTION}")"
if [[ -n "${EXPLICIT_CODE}" ]]; then
  NEW_CODE="${EXPLICIT_CODE}"
elif [[ "${KEEP_CODE}" -eq 1 ]]; then
  NEW_CODE="${CURRENT_CODE}"
else
  NEW_CODE=$(( CURRENT_CODE + 1 ))
fi

# Collect target files to update
declare -a FILES_TO_UPDATE=()
if [[ -n "${TARGET_FILE}" ]]; then
  if [[ "${TARGET_FILE}" != /* ]]; then
    TARGET_FILE="${ROOT_DIR}/${TARGET_FILE}"
  fi
  FILES_TO_UPDATE+=("${TARGET_FILE}")
else
  for candidate in \
    "${ROOT_DIR}/version.properties" \
    "${ROOT_DIR}/chrome/VERSION" \
    "${ROOT_DIR}/app/build.gradle.kts" \
    "${ROOT_DIR}/app/build.gradle" \
    "${ROOT_DIR}/VERSION"; do
    if [[ -f "${candidate}" ]]; then
      FILES_TO_UPDATE+=("${candidate}")
    fi
  done
fi

if [[ ${#FILES_TO_UPDATE[@]} -eq 0 ]]; then
  if [[ "${INIT_IF_MISSING}" -eq 1 ]]; then
    FILES_TO_UPDATE+=("${ROOT_DIR}/version.properties")
  else
    log_warn "No version file found (checked version.properties, chrome/VERSION, app/build.gradle.kts, VERSION)."
    log_info "Initializing ${ROOT_DIR}/version.properties..."
    FILES_TO_UPDATE+=("${ROOT_DIR}/version.properties")
  fi
fi

log_step "Version Bump (${CURRENT_VER} [${CURRENT_CODE}] -> ${NEW_VER} [${NEW_CODE}])"

update_file() {
  local file="$1"
  local base
  base="$(basename "${file}")"

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    log_info "[dry-run] Would update ${file} -> versionName=${NEW_VER}, versionCode=${NEW_CODE}"
    return 0
  fi

  if [[ "${base}" == "version.properties" ]]; then
    cat > "${file}" <<EOF
VERSION_NAME=${NEW_VER}
VERSION_CODE=${NEW_CODE}
EOF
    log_success "Updated ${file}"
    return 0
  fi

  if [[ "${base}" == "VERSION" && "${file}" != *"/chrome/VERSION" ]]; then
    printf '%s\n' "${NEW_VER}" > "${file}"
    log_success "Updated ${file}"
    return 0
  fi

  if [[ "${file}" == *"/chrome/VERSION" ]]; then
    local core="${NEW_VER%%[-+]*}"
    IFS='.' read -r -a parts <<< "${core}"
    cat > "${file}" <<EOF
MAJOR=${parts[0]:-0}
MINOR=${parts[1]:-0}
BUILD=${parts[2]:-0}
PATCH=${parts[3]:-0}
EOF
    log_success "Updated ${file}"
    return 0
  fi

  if [[ "${base}" == "build.gradle.kts" || "${base}" == "build.gradle" ]]; then
    require_cmd python3 "python3 is required to safely update Gradle build files."
    python3 - "${file}" "${NEW_VER}" "${NEW_CODE}" <<'PYEOF'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
new_ver = sys.argv[2]
new_code = sys.argv[3]
text = path.read_text(encoding="utf-8")

if path.name.endswith(".kts"):
    text, c1 = re.subn(r'(versionCode\s*=\s*)\d+', rf'\g<1>{new_code}', text, count=1)
    text, c2 = re.subn(r'(versionName\s*=\s*)"[^"]*"', rf'\g<1>"{new_ver}"', text, count=1)
else:
    text, c1 = re.subn(r'(versionCode\s+)\d+', rf'\g<1>{new_code}', text, count=1)
    text, c2 = re.subn(r'(versionName\s+)["\'][^"\']*["\']', rf'\g<1>"{new_ver}"', text, count=1)

if c1 == 0 or c2 == 0:
    sys.stderr.write(f"Warning: Could not match both versionCode and versionName in {path}\n")
path.write_text(text, encoding="utf-8")
PYEOF
    log_success "Updated ${file}"
    return 0
  fi

  die "Unsupported version file format: ${file}"
}

for f in "${FILES_TO_UPDATE[@]}"; do
  update_file "${f}"
done

if [[ "${DO_COMMIT}" -eq 1 ]]; then
  require_cmd git
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    log_info "[dry-run] Would git add and commit: chore(release): bump version to ${NEW_VER} (${NEW_CODE})"
  else
    git -C "${ROOT_DIR}" add "${FILES_TO_UPDATE[@]}"
    git -C "${ROOT_DIR}" commit -m "chore(release): bump version to ${NEW_VER} (${NEW_CODE})"
    log_success "Committed version bump."
  fi
fi

if [[ "${DO_TAG}" -eq 1 ]]; then
  require_cmd git
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    log_info "[dry-run] Would create git tag v${NEW_VER}"
  else
    git -C "${ROOT_DIR}" tag -a "v${NEW_VER}" -m "Release v${NEW_VER} (versionCode ${NEW_CODE})"
    log_success "Created git tag v${NEW_VER}"
  fi
fi
