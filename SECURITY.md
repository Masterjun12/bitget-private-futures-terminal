# Security Policy

## Reporting a vulnerability

Never include API keys, secret keys, passphrases, session tokens, private keys, account identifiers, screenshots containing private data, or other credentials in a vulnerability report. Secrets must not be reported even through a private channel.

If a secret may have been exposed, revoke or rotate it with the provider first. Remove the secret from logs and screenshots before sharing any diagnostic material. Rewriting a report later does not make an exposed credential safe.

Use the repository host's private vulnerability-reporting feature when it is enabled. Otherwise, open a minimal public issue that contains no exploit details or private data and asks the maintainers to establish a private channel. Include the affected version, operating system, Java version, impact, and sanitized reproduction steps.

## Safe operation

- Create a dedicated API key with the least privilege available.
- Use read-only permissions and do not grant order, transfer, wallet, or withdrawal access.
- Apply an IP allowlist when the provider and deployment model support it.
- Do not launch the application on an untrusted desktop session.
- Lock the desktop or keyring when leaving the machine unattended.
- Verify the downloaded DEB against the published SHA-256 checksum.

The client signs authenticated HTTPS GET requests locally. It does not implement order, transfer, or withdrawal requests. That boundary reduces risk but does not make a leaked read credential harmless.

## Supported versions

Security fixes are made against the latest 3.1.x release. Older versions may not receive backports.
