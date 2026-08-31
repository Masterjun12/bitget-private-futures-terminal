# Bitget Private Futures Terminal 3.1.1

A compact, always-on-top Java 11 Swing dashboard for monitoring Bitget USDT futures. It displays public BTC, ETH, and SOL prices, the top five futures gainers, and—when you provide read-only API credentials—account equity and open positions.

This is an independent, community-maintained project. It is not affiliated with, endorsed by, or supported by Bitget. It is monitoring software, not financial advice.

## Highlights

- Read-only client: there are no order, transfer, or withdrawal operations.
- Supports Classic and UTA account data, with an automatic detection mode.
- Refreshes public market and private account data every second.
- Borderless, draggable, resizable, responsive, always-on-top window.
- Compact 920×82 price bar and a full account/position dashboard.
- Stores API key, secret key, and passphrase in GNOME Keyring through `secret-tool`.
- Keeps only window position, size, and compact mode in a local properties file.

## Requirements

Build requirements:

- Linux on an `amd64` or `arm64` machine
- Java Development Kit 11 or newer
- `dpkg-deb`, GNU coreutils, and Bash

Runtime requirements are declared by the Debian package: Java 11+, `libsecret-tools`, and DejaVu fonts. A desktop session with a Secret Service provider such as GNOME Keyring is required for private credentials.

## Build and test

```bash
./package/build-deb.sh
```

The root build command compiles with `--release 11`, compiles and runs the Java regression tests, builds `dist/bitget-private-terminal_3.1.1_all.deb`, and writes `dist/SHA256SUMS`.

To run the complete release checks, including source sanitation and package inspection:

```bash
./scripts/quality-gate.sh
```

The local quality gate builds twice and requires identical SHA-256 checksums. It removes its generated `build/` and `dist/` directories when it exits. Set `KEEP_BUILD_ARTIFACTS=1` only when you intentionally want to retain them.

## Install

```bash
sudo apt install ./dist/bitget-private-terminal_3.1.1_all.deb
```

Launch **Bitget Private Futures Terminal** from the application menu. Open `CFG`, choose `AUTO`, `CLASSIC`, or `UTA`, enter credentials, and test the connection before saving. Use the least-privileged, read-only API permissions available for the account and never enable withdrawal permissions for this application.

The package also installs an XDG autostart entry. Remove it with:

```bash
sudo apt remove bitget-private-terminal
```

## Data and security

- Credentials are sent only to the Bitget HTTPS API and stored as one item in the current user's GNOME Keyring.
- Credentials are never written to the window-state file.
- Window state is stored at `~/.config/bitget-private-terminal/state.properties`.
- This project contains no telemetry or analytics.

Read [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md) before using a private API key.

## Limitations

- The desktop UI requires a graphical Linux session; headless operation is limited to built-in diagnostic arguments.
- API availability, response formats, account permissions, and rate limits are controlled by Bitget and may change.
- The software is provided without warranty. Verify all displayed account data against the exchange before relying on it.

## 한국어 안내

Bitget Private Futures Terminal은 Bitget USDT 선물 정보를 확인하는 Java 11 Swing 데스크톱 대시보드입니다. BTC·ETH·SOL 시세, 선물 상승률 상위 5개 종목, 계정 자산과 보유 포지션을 1초 간격으로 표시합니다.

이 프로젝트는 Bitget과 제휴하거나 Bitget의 보증을 받은 제품이 아닙니다. 주문·이체·출금 API는 구현하지 않은 조회 전용 클라이언트이며 투자 자문을 제공하지 않습니다.

### 빌드와 테스트

Java 11 이상 JDK와 `dpkg-deb`가 설치된 Debian/Ubuntu 계열 환경에서 실행합니다.

```bash
./package/build-deb.sh
./scripts/quality-gate.sh
```

첫 번째 명령은 Java 컴파일, 회귀 테스트, DEB 생성을 수행합니다. 두 번째 명령은 Python/레거시 웹 파일, 비밀정보, 개인 경로를 추가로 검사하고 패키지 구조와 SHA-256 체크섬을 검증합니다.

### 설치와 설정

```bash
sudo apt install ./dist/bitget-private-terminal_3.1.1_all.deb
```

앱 메뉴에서 프로그램을 실행한 뒤 `CFG`에서 계정 모드와 API 정보를 입력합니다. 가능한 최소 범위의 조회 전용 API 권한만 사용하고 출금 권한은 활성화하지 마십시오.

API Key, Secret Key, Passphrase는 `secret-tool`을 통해 현재 사용자의 GNOME Keyring에 저장됩니다. 창 위치·크기·미니 모드만 `~/.config/bitget-private-terminal/state.properties`에 저장되며, 이 파일에는 API 정보가 들어가지 않습니다.

보안 취약점 제보 시 실제 API 정보나 다른 비밀값을 절대 첨부하지 마십시오. 자세한 내용은 [SECURITY.md](SECURITY.md)와 [PRIVACY.md](PRIVACY.md)를 확인하십시오.

## License

MIT — see [LICENSE](LICENSE).
