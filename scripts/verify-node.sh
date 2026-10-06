#!/usr/bin/env bash

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a/libnode.so"
TARGET="${1:-$DEST}"

fail=0
ok()   { printf '  \033[32mok\033[0m    %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; fail=1; }
warn() { printf '  \033[33mwarn\033[0m  %s\n' "$1"; }

echo "checking $TARGET"

if [ ! -f "$TARGET" ]; then
  bad "not found. Copy the binary here and rename it to libnode.so"
  exit 1
fi
ok "exists ($(du -h "$TARGET" | cut -f1))"

info="$(file -b "$TARGET")"
echo "  $info"
case "$info" in
  *"ARM aarch64"*) ok "arm64" ;;
  *"ELF"*)         bad "wrong architecture (need ARM aarch64)" ;;
  *)               bad "not an ELF binary -- is this a shell wrapper?" ;;
esac
case "$info" in
  *"/system/bin/linker64"*) ok "bionic-linked (Android)" ;;
  *"ld-linux-aarch64"*)     bad "glibc-linked -- this is a Linux build, not Android" ;;
  *"statically linked"*)    ok "static (no runtime deps to worry about)" ;;
  *)                        warn "could not identify the dynamic linker" ;;
esac

echo "  NEEDED:"
needed="$(readelf -d "$TARGET" 2>/dev/null | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p')"
bundle_count=0
unextractable=0
if [ -z "$needed" ]; then
  ok "none (static binary)"
else
  system_libs="libc.so libm.so libdl.so liblog.so libstdc++.so libandroid.so libnetd_client.so"
  while read -r lib; do
    [ -z "$lib" ] && continue
    if printf '%s\n' $system_libs | grep -qx "$lib"; then
      printf '    %-22s system\n' "$lib"
    else
      bundle_count=$((bundle_count + 1))
      case "$lib" in
        lib*.so)
          printf '    %-22s \033[33mbundle\033[0m (jniLibs-compatible name)\n' "$lib" ;;
        *)
          unextractable=$((unextractable + 1))
          printf '    %-22s \033[31mbundle\033[0m (name is NOT lib*.so -- aapt will not extract it)\n' "$lib" ;;
      esac
    fi
  done <<< "$needed"
fi

rp="$(readelf -d "$TARGET" 2>/dev/null | sed -n 's/.*R\(UN\)\?PATH.*\[\(.*\)\]/\2/p')"
if [ -n "$rp" ]; then
  echo "  RUNPATH: $rp"
  case "$rp" in
    *com.termux*) warn "hardcodes the Termux prefix; harmless if NEEDED is all-system, otherwise bundle the libs" ;;
    *)            ok "runpath present, no Termux prefix" ;;
  esac
else
  ok "no RUNPATH"
fi

if [ "$TARGET" = "$DEST" ]; then
  ok "in jniLibs/arm64-v8a as libnode.so"
else
  warn "not at the jniLibs path; copy it to:"
  echo "        $DEST"
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "The binary itself is wrong. Node will not start."
  exit 1
fi
if [ "$unextractable" -gt 0 ]; then
  echo "Binary is fine, but $bundle_count libraries must ship with it and"
  echo "$unextractable of them have .so.N names that aapt will not extract."
  echo "=> jniLibs alone cannot work. Bundle \$PREFIX/lib into assets and"
  echo "   unpack to filesDir/usr/lib with LD_LIBRARY_PATH pointed at it."
  exit 1
fi
if [ "$bundle_count" -gt 0 ]; then
  echo "Binary is fine, but $bundle_count non-system libraries must be copied"
  echo "into jniLibs/arm64-v8a/ alongside it (names are all lib*.so, so they"
  echo "will extract correctly)."
  exit 1
fi
echo "runnable with no extra libraries. Next: ./gradlew :app:installDebug"
