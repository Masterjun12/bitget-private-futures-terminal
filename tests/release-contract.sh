#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fail() {
  printf 'RELEASE_CONTRACT_FAIL: %s\n' "$1" >&2
  exit 1
}

required_files=(
  README.md LICENSE SECURITY.md PRIVACY.md CONTRIBUTING.md
  package/build-deb.sh package/control package/launcher.sh
  package/bitget-private-terminal.desktop package/bitget-private-terminal.svg
  scripts/quality-gate.sh scripts/scan-release.sh scripts/inspect-package.sh
  scripts/verify-reproducible-build.sh
  test/com/local/bitget/BitgetClientTest.java
  tests/scan-release-test.sh
  .github/workflows/build.yml
)
for path in "${required_files[@]}"; do
  [ -s "$path" ] || fail "required file is missing or empty: $path"
done

[ -x package/build-deb.sh ] || fail "root build command is not executable"
[ -x scripts/quality-gate.sh ] || fail "quality gate is not executable"

java_source_count="$(find src -type f -name '*.java' | wc -l)"
[ "$java_source_count" -eq 8 ] || fail "expected 8 Java application source files, found $java_source_count"

grep -Fqx 'Package: bitget-private-terminal' package/control || fail 'unexpected package name'
grep -Fqx 'Version: 3.1.1' package/control || fail 'unexpected package version'
grep -Fqx 'Architecture: all' package/control || fail 'unexpected package architecture'
grep -Fq 'SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-1788134400}' package/build-deb.sh || \
  fail 'reproducible build epoch is missing'
grep -Fqx 'Copyright (c) 2026 Bitget Private Futures Terminal Contributors' LICENSE || \
  fail 'license holder is not neutral'
grep -Fq '# Bitget Private Futures Terminal 3.1.1' README.md || fail 'English README heading missing'
grep -Fq '## 한국어 안내' README.md || fail 'Korean README section missing'
grep -Fq 'GNOME Keyring' PRIVACY.md || fail 'keyring privacy disclosure missing'
grep -Fq 'Secrets must not be reported' SECURITY.md || fail 'secret-reporting warning missing'

grep -Fq 'actions/checkout@v6' .github/workflows/build.yml || fail 'checkout action version missing'
grep -Fq 'actions/setup-java@v5' .github/workflows/build.yml || fail 'Java setup action version missing'
grep -Fq "java-version: '11'" .github/workflows/build.yml || fail 'Java 11 workflow target missing'
grep -Fq 'actions/upload-artifact@v6' .github/workflows/build.yml || fail 'artifact upload action missing'
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  [ "$(git check-attr export-ignore -- .codex/ops.json)" = \
    '.codex/ops.json: export-ignore: set' ] || fail 'management metadata export exclusion is ineffective'
fi

sh -n package/build-deb.sh package/launcher.sh
bash -n scripts/quality-gate.sh scripts/scan-release.sh scripts/inspect-package.sh \
  scripts/verify-reproducible-build.sh tests/release-contract.sh tests/scan-release-test.sh
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

printf 'RELEASE_CONTRACT_OK java_sources=%s\n' "$java_source_count"
