#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT/template"
ASSETS="$ROOT/app/src/main/assets"
OUT="$ASSETS/template.zip"

TARGET_OS="android"
TARGET_CPU="arm64"

command -v npm >/dev/null || { echo "npm not found" >&2; exit 1; }
command -v zip >/dev/null || { echo "zip not found (apt install zip / pacman -S zip)" >&2; exit 1; }

echo "==> installing deps for $TARGET_OS/$TARGET_CPU (host: $(uname -s)/$(uname -m))"
cd "$TEMPLATE"
rm -rf node_modules

npm install \
  --os="$TARGET_OS" \
  --cpu="$TARGET_CPU" \
  --ignore-scripts \
  --no-audit \
  --no-fund

echo "==> verifying the android binary actually landed"
ESBUILD_BIN="node_modules/@esbuild/${TARGET_OS}-${TARGET_CPU}/bin/esbuild"
if [ ! -f "$ESBUILD_BIN" ]; then
  echo "MISSING: $ESBUILD_BIN" >&2
  echo "Present @esbuild packages:" >&2
  ls node_modules/@esbuild 2>/dev/null >&2 || echo "  (none)" >&2
  echo "Without this, Vite cannot transform anything on-device." >&2
  exit 1
fi
file "$ESBUILD_BIN" 2>/dev/null || true
echo "    ok: $ESBUILD_BIN"

for d in node_modules/@esbuild/*/; do
  name="$(basename "$d")"
  [ "$name" = "${TARGET_OS}-${TARGET_CPU}" ] || echo "    warning: unexpected $name in bundle"
done

echo "==> typechecking the template (host tsc; pure JS, platform-agnostic)"
node node_modules/typescript/bin/tsc --noEmit

echo "==> zipping"
mkdir -p "$ASSETS"
rm -f "$OUT"
zip -q -r -0 "$OUT" . \
  -x '*.DS_Store' \
  -x 'node_modules/.cache/*' \
  -x 'node_modules/.vite/*'

echo
echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1))"
echo
echo "NOTE: zip entries do not carry the executable bit through Android's"
echo "java.util.zip reader. WorkspaceInstaller must chmod +x the esbuild"
echo "binary and node_modules/.bin/* after unpacking, or Vite fails with"
echo "EACCES on first transform."
