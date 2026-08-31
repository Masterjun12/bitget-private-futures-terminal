#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
scanner="$repo_root/scripts/scan-release.sh"
fixture="$(mktemp -d "${TMPDIR:-/tmp}/release-scan-test.XXXXXX")"
output="$fixture/output.log"

cleanup() {
  rm -rf -- "$fixture"
}
trap cleanup EXIT

fail() {
  printf 'RELEASE_SCAN_TEST_FAIL: %s\n' "$1" >&2
  exit 1
}

printf '%s\n' 'safe fixture' > "$fixture/README.md"
printf '%s\n' '.codex export-ignore' '.codex/** export-ignore' > "$fixture/.gitattributes"

RELEASE_SCAN_ROOT="$fixture" "$scanner" > "$output"
grep -Fq 'RELEASE_SCAN_OK' "$output" || fail 'safe fixture did not pass'

printf '%s\n' 'print(1)' > "$fixture/legacy.py"
if RELEASE_SCAN_ROOT="$fixture" "$scanner" > "$output" 2>&1; then
  fail 'Python fixture was not rejected'
fi
grep -Fq 'Python or legacy web files' "$output" || fail 'Python rejection reason missing'
rm -f -- "$fixture/legacy.py"

printf '%s\n' 'AKIA''1234567890ABCDEF' > "$fixture/leaked-token.txt"
if RELEASE_SCAN_ROOT="$fixture" "$scanner" > "$output" 2>&1; then
  fail 'secret fixture was not rejected'
fi
grep -Fq 'possible embedded secret' "$output" || fail 'secret rejection reason missing'
rm -f -- "$fixture/leaked-token.txt"

mkdir -p "$fixture/specs"
printf '%s\n' 'internal task' > "$fixture/specs/TASK_TEMPLATE.md"
if RELEASE_SCAN_ROOT="$fixture" "$scanner" > "$output" 2>&1; then
  fail 'internal metadata fixture was not rejected'
fi
grep -Fq 'internal project metadata' "$output" || fail 'metadata rejection reason missing'

printf 'RELEASE_SCAN_TEST_OK\n'
