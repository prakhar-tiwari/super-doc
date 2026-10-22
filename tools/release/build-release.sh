#!/usr/bin/env bash
# Builds the signed App Bundle (for Play) and APK (for installing directly on a phone).
# The signing key and passwords are read from the environment only; nothing is stored in the repo.
#
#   export SUPERDOC_KEYSTORE_PATH=/path/outside/repo/upload.p12
#   export SUPERDOC_KEY_ALIAS=upload
#   export SUPERDOC_KEYSTORE_PASSWORD=...   # e.g. from a password manager CLI
#   export SUPERDOC_KEY_PASSWORD=...
#   tools/release/build-release.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

for v in SUPERDOC_KEYSTORE_PATH SUPERDOC_KEY_ALIAS SUPERDOC_KEYSTORE_PASSWORD SUPERDOC_KEY_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Missing $v. See docs/release/play-internal-test.md." >&2
    exit 1
  fi
done
case "$SUPERDOC_KEYSTORE_PATH" in
  "$PWD"/*) echo "Keep the keystore outside the project folder." >&2; exit 1 ;;
esac

./gradlew testDebugUnitTest bundleRelease assembleRelease

BT="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)"
"$BT/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk | grep -E "DN|SHA-256"
ls -la app/build/outputs/bundle/release/app-release.aab app/build/outputs/apk/release/app-release.apk
