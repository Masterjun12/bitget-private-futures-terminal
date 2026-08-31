#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
artifact="$repo_root/dist/bitget-private-terminal_3.1.1_all.deb"

fail() {
  printf 'REPRODUCIBLE_BUILD_FAIL: %s\n' "$1" >&2
  exit 1
}

[ -s "$artifact" ] || fail "first build artifact is missing"
first_line="$(sha256sum "$artifact")"
first_hash="${first_line%% *}"

"$repo_root/package/build-deb.sh"
"$repo_root/scripts/inspect-package.sh" "$artifact"

second_line="$(sha256sum "$artifact")"
second_hash="${second_line%% *}"
[ "$first_hash" = "$second_hash" ] || \
  fail "checksums differ: first=$first_hash second=$second_hash"

printf 'REPRODUCIBLE_BUILD_OK sha256=%s\n' "$second_hash"
