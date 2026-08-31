#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

cleanup() {
  if [ "${KEEP_BUILD_ARTIFACTS:-0}" != "1" ]; then
    rm -rf -- "$repo_root/build" "$repo_root/dist"
  fi
}
trap cleanup EXIT

run_step() {
  local label="$1"
  shift
  printf '\n[quality-gate] %s\n' "$label"
  "$@"
}

for command_name in bash sh find rg java dpkg-deb sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'Required quality-gate command not found: %s\n' "$command_name" >&2
    exit 1
  }
done

java_modules="$(java --list-modules)"
if ! command -v javac >/dev/null 2>&1 && ! grep -q '^jdk.compiler@' <<<"$java_modules"; then
  printf 'Java compiler not found as javac or jdk.compiler module.\n' >&2
  exit 1
fi
if ! command -v jar >/dev/null 2>&1 && ! grep -q '^jdk.jartool@' <<<"$java_modules"; then
  printf 'JAR tool not found as jar or jdk.jartool module.\n' >&2
  exit 1
fi

rm -rf -- "$repo_root/build" "$repo_root/dist"

run_step "release source sanitation" ./scripts/scan-release.sh
run_step "release contract regression tests" ./tests/release-contract.sh
run_step "release scanner negative tests" ./tests/scan-release-test.sh
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  run_step "cached diff whitespace check" git diff --cached --check
fi
run_step "compile Java 11, run tests, and build DEB" ./package/build-deb.sh
run_step "package metadata, payload, and checksum inspection" ./scripts/inspect-package.sh
run_step "reproducible second build" ./scripts/verify-reproducible-build.sh

printf '\n[quality-gate] PASS\n'
