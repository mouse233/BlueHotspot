#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${HARMONY_TOOLS_VERSION:-}" || -z "${HARMONY_TOOLS_URL:-}" || -z "${HARMONY_TOOLS_SHA256:-}" ]]; then
  echo "version, tools-url, and sha256 inputs are required." >&2
  exit 1
fi

cache_base="${HARMONY_CACHE_PATH/#\~/$HOME}"
cache_root="$cache_base/$HARMONY_TOOLS_VERSION"
mkdir -p "$cache_root"

hvigorw_path="$(find "$cache_root" -type f -path '*/bin/hvigorw' -print -quit)"
if [[ -z "$hvigorw_path" ]]; then
  archive="$RUNNER_TEMP/harmony-command-line-tools-$HARMONY_TOOLS_VERSION.zip"
  if ! command -v aria2c >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq aria2
  fi
  aria2c \
    --allow-overwrite=true \
    --auto-file-renaming=false \
    --continue=true \
    --file-allocation=none \
    --max-connection-per-server=16 \
    --split=16 \
    --min-split-size=1M \
    --max-tries=5 \
    --retry-wait=5 \
    --timeout=60 \
    --connect-timeout=30 \
    --dir="$RUNNER_TEMP" \
    --out="$(basename "$archive")" \
    "$HARMONY_TOOLS_URL"

  actual_sha256="$(sha256sum "$archive" | awk '{print $1}')"
  expected_sha256="${HARMONY_TOOLS_SHA256,,}"
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "HarmonyOS command line tools SHA-256 mismatch. Expected $expected_sha256, got $actual_sha256." >&2
    exit 1
  fi

  unzip -q "$archive" -d "$cache_root"
  hvigorw_path="$(find "$cache_root" -type f -path '*/bin/hvigorw' -print -quit)"
fi

ohpm_path="$(find "$cache_root" -type f -path '*/bin/ohpm' -print -quit)"
if [[ -z "$hvigorw_path" || -z "$ohpm_path" ]]; then
  echo "Command line tools must contain both bin/hvigorw and bin/ohpm." >&2
  exit 1
fi

tools_root="$(dirname "$hvigorw_path")"
while [[ "$tools_root" != "$cache_root" && "$tools_root" != "/" ]]; do
  if [[ -d "$tools_root/sdk" ]]; then
    break
  fi
  tools_root="$(dirname "$tools_root")"
done
if [[ ! -d "$tools_root/sdk" ]]; then
  echo "Could not find the embedded HarmonyOS SDK beside the command line tools." >&2
  exit 1
fi

chmod +x "$hvigorw_path" "$ohpm_path"
echo "HARMONY_TOOLS_ROOT=$tools_root" >> "$GITHUB_ENV"
echo "HARMONY_HVIGORW=$hvigorw_path" >> "$GITHUB_ENV"
echo "HARMONY_OHPM=$ohpm_path" >> "$GITHUB_ENV"
echo "DEVECO_SDK_HOME=$tools_root/sdk" >> "$GITHUB_ENV"
echo "HOS_SDK_HOME=$tools_root/sdk" >> "$GITHUB_ENV"
echo "$(dirname "$hvigorw_path")" >> "$GITHUB_PATH"
echo "$(dirname "$ohpm_path")" >> "$GITHUB_PATH"
echo "tools-root=$tools_root" >> "$GITHUB_OUTPUT"
echo "sdk-root=$tools_root/sdk" >> "$GITHUB_OUTPUT"
