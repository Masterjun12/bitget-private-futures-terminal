#!/usr/bin/env bash
set -euo pipefail

repo_root="${RELEASE_SCAN_ROOT:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$repo_root"

fail() {
  printf 'RELEASE_SCAN_FAIL: %s\n' "$1" >&2
  exit 1
}

command -v rg >/dev/null 2>&1 || fail "required scanner command not found: rg"

mapfile -d '' release_files < <(
  find . -type f \
    ! -path './.git' ! -path './.git/*' \
    ! -path './.agents/*' ! -path './.codex/*' \
    ! -path './build/*' ! -path './dist/*' -print0
)

[ "${#release_files[@]}" -gt 0 ] || fail "no release files found"

if find . -type f \( \
    -name '*.py' -o -name '*.pyc' -o -name '*.pyo' -o \
    -name '*.js' -o -name '*.jsx' -o -name '*.ts' -o -name '*.tsx' -o \
    -name '*.html' -o -name '*.htm' -o -name '*.css' \
  \) ! -path './.git/*' ! -path './.agents/*' ! -path './.codex/*' -print -quit | grep -q .; then
  fail "Python or legacy web files are present"
fi

if find . -type f \( \
    -name '*.class' -o -name '*.jar' -o -name '*.deb' -o -name '*.pyc' \
  \) ! -path './.git/*' -print -quit | grep -q .; then
  fail "generated build artifacts are present before the build"
fi

for forbidden in AGENTS.md .gitlab-ci.yml .gitlab-ci.codex-ops.yml specs/TASK_TEMPLATE.md; do
  [ ! -e "$forbidden" ] || fail "internal project metadata is present: $forbidden"
done

if find specs -type f -print -quit 2>/dev/null | grep -q .; then
  fail "internal task specifications are present"
fi

grep -Fqx '.codex export-ignore' .gitattributes || \
  fail "management directory is not excluded from public archives"
grep -Fqx '.codex/** export-ignore' .gitattributes || \
  fail "management metadata is not excluded from public archives"

data_root='/''data4'
server_home='/''home/'
mac_home='/''Users/'
local_user='a202''520153'
if rg -nI --fixed-strings \
    -e "$data_root" -e "$server_home" -e "$mac_home" -e "$local_user" \
    "${release_files[@]}"; then
  fail "personal or server filesystem path found"
fi

email_hits="$(rg -nI --pcre2 '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}' "${release_files[@]}" || true)"
email_hits="$(printf '%s\n' "$email_hits" | grep -Fv 'maintainers@example.invalid' || true)"
if [ -n "$email_hits" ]; then
  printf '%s\n' "$email_hits" >&2
  fail "personal email-like value found"
fi

if rg -nI --pcre2 \
    -e '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' \
    -e 'AKIA[0-9A-Z]{16}' \
    -e 'ASIA[0-9A-Z]{16}' \
    -e 'gh[pousr]_[A-Za-z0-9]{30,}' \
    -e 'github_pat_[A-Za-z0-9_]{40,}' \
    -e 'glpat-[A-Za-z0-9_-]{20,}' \
    -e 'xox[baprs]-[A-Za-z0-9-]{20,}' \
    -e '[0-9]{6,}:[A-Za-z0-9_-]{30,}' \
    -e '"[A-Za-z0-9]{28,}"' \
    -e "'[A-Za-z0-9]{28,}'" \
    "${release_files[@]}"; then
  fail "possible embedded secret found"
fi

printf 'RELEASE_SCAN_OK files=%s\n' "${#release_files[@]}"
