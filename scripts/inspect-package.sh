#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
artifact="${1:-$repo_root/dist/bitget-private-terminal_3.1.1_all.deb}"
checksum_file="$(dirname -- "$artifact")/SHA256SUMS"
extract_dir="$(mktemp -d "${TMPDIR:-/tmp}/bitget-private-terminal-inspect.XXXXXX")"

cleanup() {
  rm -rf -- "$extract_dir"
}
trap cleanup EXIT HUP INT TERM

fail() {
  printf 'PACKAGE_INSPECTION_FAIL: %s\n' "$1" >&2
  exit 1
}

run_jar() {
  if command -v jar >/dev/null 2>&1; then
    jar "$@"
  else
    java -m jdk.jartool/sun.tools.jar.Main "$@"
  fi
}

[ -s "$artifact" ] || fail "DEB artifact is missing: $artifact"
[ -s "$checksum_file" ] || fail "checksum file is missing: $checksum_file"
[ "$(wc -l < "$checksum_file")" -eq 1 ] || fail "checksum manifest must contain exactly one entry"

(
  cd "$(dirname -- "$artifact")"
  sha256sum --check --strict "$(basename -- "$checksum_file")"
)

[ "$(dpkg-deb --field "$artifact" Package)" = "bitget-private-terminal" ] || fail "unexpected package name"
[ "$(dpkg-deb --field "$artifact" Version)" = "3.1.1" ] || fail "unexpected package version"
[ "$(dpkg-deb --field "$artifact" Architecture)" = "all" ] || fail "unexpected package architecture"

dpkg-deb --extract "$artifact" "$extract_dir/root"
required_payload=(
  opt/bitget-private-terminal/bitget-private-terminal.jar
  usr/bin/bitget-private-terminal
  usr/share/applications/bitget-private-terminal.desktop
  etc/xdg/autostart/bitget-private-terminal.desktop
  usr/share/icons/hicolor/scalable/apps/bitget-private-terminal.svg
  usr/share/doc/bitget-private-terminal/README.md
  usr/share/doc/bitget-private-terminal/LICENSE
  usr/share/doc/bitget-private-terminal/SECURITY.md
  usr/share/doc/bitget-private-terminal/PRIVACY.md
)
for path in "${required_payload[@]}"; do
  [ -f "$extract_dir/root/$path" ] || fail "package payload missing: $path"
done

[ "$(stat -c %a "$extract_dir/root/usr/bin/bitget-private-terminal")" = "755" ] || \
  fail "launcher mode must be 0755"
if find "$extract_dir/root" -type f -perm /022 -print -quit | grep -q .; then
  fail "package contains a group- or world-writable file"
fi
if find "$extract_dir/root" -type f -name '*.py' -print -quit | grep -q .; then
  fail "package contains Python files"
fi

jar_file="$extract_dir/root/opt/bitget-private-terminal/bitget-private-terminal.jar"
run_jar tf "$jar_file" | grep -Fqx 'com/local/bitget/Main.class' || fail "main class missing from JAR"
mkdir -p "$extract_dir/jar"
(
  cd "$extract_dir/jar"
  run_jar xf "$jar_file" META-INF/MANIFEST.MF
)
tr -d '\r' < "$extract_dir/jar/META-INF/MANIFEST.MF" | \
  grep -Fqx 'Main-Class: com.local.bitget.Main' || \
  fail "JAR manifest main class is incorrect"

if dpkg-deb --contents "$artifact" | awk 'NF >= 3 && $2 !~ /^root\/root$/ { exit 1 }'; then
  :
else
  fail "package payload ownership is not root/root"
fi

hash="$(sha256sum "$artifact" | awk '{print $1}')"
printf 'PACKAGE_INSPECTION_OK artifact=%s sha256=%s\n' "$(basename -- "$artifact")" "$hash"
