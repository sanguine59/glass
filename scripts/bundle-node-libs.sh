#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/node-libs/arm64-v8a"
ASSETS="$ROOT/app/src/main/assets"
OUT="$ASSETS/node-libs.zip"
NODE="$ROOT/app/src/main/jniLibs/arm64-v8a/libnode.so"

command -v zip >/dev/null || { echo "zip not found" >&2; exit 1; }
[ -d "$SRC" ] || { echo "missing $SRC" >&2; exit 1; }

echo "==> checking the dependency closure"
system_libs="libc.so libm.so libdl.so liblog.so libstdc++.so libandroid.so libnetd_client.so"
missing=0
declare -A seen=()

needed_of() { readelf -d "$1" 2>/dev/null | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p'; }

queue=()
while read -r l; do [ -n "$l" ] && queue+=("$l"); done < <(needed_of "$NODE")

while [ ${#queue[@]} -gt 0 ]; do
  lib="${queue[0]}"; queue=("${queue[@]:1}")
  [ -n "${seen[$lib]:-}" ] && continue
  seen[$lib]=1
  if printf '%s\n' $system_libs | grep -qx "$lib"; then
    printf '    %-22s system\n' "$lib"
    continue
  fi
  if [ -f "$SRC/$lib" ]; then
    printf '    %-22s ok\n' "$lib"
    while read -r l; do [ -n "$l" ] && queue+=("$l"); done < <(needed_of "$SRC/$lib")
  else
    printf '    %-22s \033[31mMISSING\033[0m\n' "$lib"
    missing=$((missing + 1))
  fi
done

if [ "$missing" -gt 0 ]; then
  echo
  echo "$missing library/libraries missing from $SRC." >&2
  echo "Copy them from Termux's \$PREFIX/lib and re-run." >&2
  exit 1
fi

for f in "$SRC"/*; do
  name="$(basename "$f")"
  [ -n "${seen[$name]:-}" ] || echo "    note: $name is not in the closure (unused?)"
done

echo "==> zipping"
mkdir -p "$ASSETS"
rm -f "$OUT"
( cd "$SRC" && zip -q -r -0 "$OUT" . )

echo "==> wrote $OUT ($(du -h "$OUT" | cut -f1))"
