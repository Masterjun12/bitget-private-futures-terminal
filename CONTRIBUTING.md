# Contributing

Thank you for helping improve Bitget Private Futures Terminal.

## Before opening a change

- Never add real credentials, private account data, personal paths, screenshots containing account information, or generated build artifacts.
- Keep the application read-only. Changes that add order, transfer, wallet, or withdrawal operations are outside the project scope.
- Keep Java compatibility at release 11 unless a version change is discussed first.
- Use synthetic test fixtures and neutral identities.

## Development workflow

1. Create a focused branch.
2. Update or add regression checks under `test/` or `tests/`.
3. Run `./scripts/quality-gate.sh` on Debian or Ubuntu.
4. Review the complete diff and ensure `build/` and `dist/` are not included.
5. Submit a pull request describing behavior, tests, limitations, and any security impact.

The project intentionally has no external Java dependencies. Prefer standard Java 11 APIs and small, reviewable changes.

For vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of opening a detailed public issue.
