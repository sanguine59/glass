#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
OUT="$ASSETS/npm.zip"
NODE="$ROOT/app/src/main/jniLibs/arm64-v8a/libnode.so"

REPO="https://packages.termux.dev/apt/termux-main/pool/main/n/npm"

command -v zip >/dev/null    || { echo "zip not found" >&2; exit 1; }
command -v curl >/dev/null   || { echo "curl not found" >&2; exit 1; }
command -v ar >/dev/null     || { echo "ar not found (binutils)" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "==> finding the current npm package"
deb="$(curl -fsS "$REPO/" | grep -oE 'npm_[0-9][^"]*\.deb' | sort -u | tail -1)"
[ -n "$deb" ] || { echo "could not find an npm .deb in $REPO" >&2; exit 1; }
echo "    $deb"

echo "==> downloading"
curl -fsSL -o "$work/npm.deb" "$REPO/$deb"

echo "==> unpacking"
( cd "$work" && ar x npm.deb && tar xf data.tar.* )
SRC="$work/data/data/com.termux/files/usr/lib/node_modules/npm"
[ -d "$SRC" ] || { echo "npm tree not found in $deb" >&2; exit 1; }

version="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$SRC/package.json" | head -1)"
echo "    npm $version"

echo "==> sanity-checking the entrypoint"
[ -f "$SRC/bin/npm-cli.js" ] || { echo "MISSING bin/npm-cli.js" >&2; exit 1; }
echo "    ok: bin/npm-cli.js"

if [ -f "$NODE" ]; then
  engines="$(sed -n '/"engines"/,/}/p' "$SRC/package.json" | sed -n 's/.*"node": *"\([^"]*\)".*/\1/p')"
  echo "    npm $version wants node $engines"
  echo "    bundled node: $(strings "$NODE" 2>/dev/null | grep -oE '^node\.js/v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo 'unknown')"
fi

echo "==> zipping"
mkdir -p "$ASSETS"
rm -f "$OUT"
( cd "$SRC" && zip -q -r -9 "$OUT" . -x '*.DS_Store' )

echo
echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1), npm $version)"
echo
echo "NOTE: PrefixInstaller unpacks this to usr/lib/node_modules/npm and npm is"
echo "invoked as 'node .../npm/bin/npm-cli.js', so no executable bit is needed."
