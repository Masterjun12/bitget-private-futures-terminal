# Privacy

Bitget Private Futures Terminal has no telemetry, analytics, advertising, crash-reporting service, or project-operated backend.

## Data processed

The application requests public futures tickers from Bitget. When private API credentials are configured, it also requests account balances and open futures positions. Those responses are displayed in memory and are not written to project-managed history or analytics files.

## Credential storage

The API key, secret key, passphrase, and selected account mode are serialized into one GNOME Keyring item under the service name `bitget-private-terminal`. The application invokes `/usr/bin/secret-tool` when available, or resolves `secret-tool` from the process path. Credential persistence therefore depends on the Secret Service provider and desktop keyring configured for the current operating-system user.

The project does not write credentials to a plain-text configuration file. Clearing credentials from `CFG` asks GNOME Keyring to remove the stored item. Uninstalling the Debian package does not necessarily delete a user's keyring item; remove it from the keyring application or clear it in the application before uninstalling.

## Local window state

The following non-secret values are stored in `~/.config/bitget-private-terminal/state.properties`:

- window x and y position;
- window width and height;
- compact-mode state.

This file does not contain account data or API credentials. Removing it resets the window layout.

## Network destinations

Application API requests are sent over HTTPS to `api.bitget.com`. Your operating system, Java runtime, DNS resolver, network provider, and Bitget may process normal connection metadata. Their privacy practices are outside this project's control.
