# KismetMobile

An Android client for the [Kismet](https://github.com/kismetwireless/kismet) wireless intrusion detection system.

![Version](https://img.shields.io/badge/version-1.4.0--rc1-blue) ![Android](https://img.shields.io/badge/Android-10%2B-green) ![API](https://img.shields.io/badge/API-29%2B-brightgreen) ![Foldable](https://img.shields.io/badge/foldable-ready-purple)

Browse the [screenshot gallery](docs/index.html) or build the app locally using the instructions below. No hosted demo or APK download is currently available.

This is an unofficial Kismet client. Version **1.4.0-rc1** is a release candidate; debug builds are for evaluation.

---

## Screenshots

These captures show an earlier version using synthetic demo data. The [large-screen dashboard](docs/screenshots/fold_dashboard.png) shows the dashboard; the portrait gallery focuses on connection and device screens.

| Connect | Local Interfaces | Devices |
|---|---|---|
| ![Connect](docs/screenshots/connect.png) | ![Local Interfaces](docs/screenshots/local.png) | ![Devices](docs/screenshots/devices.png) |

| Device Detail | Campaigns | Local Interfaces |
|---|---|---|
| ![Device Detail](docs/screenshots/device_detail.png) | ![Campaigns](docs/screenshots/campaigns.png) | ![Local](docs/screenshots/local.png) |

### Fold — Adaptive Layout

| Dashboard | Devices |
|---|---|
| ![Fold Dashboard](docs/screenshots/fold_dashboard.png) | ![Fold Devices](docs/screenshots/fold_devices.png) |

| Campaigns | Local Interfaces |
|---|---|
| ![Fold Campaigns](docs/screenshots/fold_campaigns.png) | ![Fold Local](docs/screenshots/fold_local.png) |

---

## Features

### Dashboard
- Live PHY counts — WiFi, Bluetooth, SDR, Other — with server memory, uptime, and packet totals
- Pull-to-refresh for an immediate data sync
- Tap a PHY card to jump to a filtered device list
- Long-press a capture source to stream its traffic as a `.pcapng` file

### Device Browser
- Search by name, MAC, or manufacturer; filter chips for each PHY type
- Campaign filter toggle — show only devices enrolled in active campaigns
- **Second Latch badge** — devices also seen by the phone's own radios are marked with a phone indicator
- Tap any device for full detail + **EXPORT PCAP** button

### Second Latch — Local Cross-Reference
- **SCAN & MATCH** button runs a combined WiFi + Bluetooth survey directly from the phone
- BLE scan uses `SCAN_MODE_LOW_LATENCY` — dense sighting collection within the 12-second window
- Classic BT discovery uses `RECEIVER_EXPORTED` flag for full Android 13+ broadcast compatibility
- Results cross-referenced against Kismet's device database by MAC address
- Requires `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` at runtime (Android 12+)

### Campaigns
- Three supported types: **Survey** / **Hunt** / **Wardriving**
- Lifecycle: `DRAFT` → `ACTIVE` → `PAUSED` → `COMPLETE`
- Hunt campaigns record matching MACs or SSIDs once per device
- Legacy Alert campaigns remain stored, but rule execution and creation are disabled in this candidate
- Auto-collects matching devices while a campaign is active

### PCAP Capture
- `PcapCaptureService` foreground service streams a live `.pcapng` from the Kismet server
- Scope to a datasource UUID, a device key, or all traffic
- Stops automatically at 50 MiB; tap the completion notification to share the file via `FileProvider`
- Capture starts only when notifications are enabled; Android 13+ also requires notification permission

### WiGLE Export
- Converts campaign devices to **WiGLE WiFi 1.4 CSV** format (WiFi BSSIDs + Bluetooth MACs)
- One-tap upload using the API key configured in Settings

### SDR / HackRF Integration
- Detects installed Android SDR apps (RF Analyzer, SDR Touch, SDR++, CubicSDR) — links to Play Store if missing
- Channel signal and packet activity with a waterfall view from Kismet's `/channels/channels.json` (not a raw RF spectrum analyzer)
- Band filter chips: ALL / 2.4G / 5G / 6G
- Kali SDR Tools sheet — copy-to-clipboard source strings and run commands for hackrf_sweep, rtl_433, dump1090, gnuradio, urh, inspectrum, multimon-ng

### Connectivity
- **SSH tunnel** — three auth modes supported:
  - **Local only** — direct connection, no tunnel
  - **Username + password** — password auth over SSH to a jump host
  - **Private key** — paste PEM key content directly into the app; supports OpenSSH v1 format (ed25519, ecdsa, rsa)
- Works with modern OpenSSH key exchange (curve25519 / ECDH)
- **WireGuard** — launches the WireGuard app when a VPN profile is required
- **InterfaceGuard** — verifies the active network interface before triggering monitor-mode actions
- Saved connection profiles with one-tap reconnect

### Adaptive Layout
- **Landscape** — Dashboard and Connect switch to a two-column layout
- **Foldable / large screen** — screens ≥ 600dp wide automatically use the large-screen resource layouts
- **Tabletop mode** — when a foldable is half-opened with a horizontal hinge, content is pushed above the fold using `WindowInfoTracker` / `FoldingFeature`

---

## Requirements

- Android 10+ (API 29)
- Kismet server accessible on port `2501`
- For Second Latch Bluetooth scan on Android 12+: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` runtime permissions

## Build

Use JDK 17 or 21 and an Android SDK containing platform 36 and Build Tools 36.0.0. The Gradle wrapper downloads the project's Gradle distribution.

On Windows (PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testDemoDebugUnitTest :app:lintStandardDebug :app:lintDemoDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug :app:testStandardDebugUnitTest :app:testDemoDebugUnitTest :app:lintStandardDebug :app:lintDemoDebug
```

The standard APK is `app/build/outputs/apk/standard/debug/app-standard-debug.apk`. The offline demo APK is `app/build/outputs/apk/demo/debug/app-demo-debug.apk` and uses a separate application ID. Both debug APKs use the local Android debug key. Release builds are unsigned until an owner supplies release signing outside the repository.

To install on a chosen emulator or device, run `adb -s <serial> install -r <apk-path>`. See [testing](docs/TESTING.md) for validation scope and manual checks.

## Security and limitations

- Direct Kismet connections use HTTP Basic authentication over cleartext HTTP. Use a trusted network or an independently verified VPN/SSH connection; do not expose the API to an untrusted network.
- Saved server credentials, SSH keys, and WiGLE credentials are stored in private app preferences without encryption. Prefer temporary credentials and remove saved profiles when finished. See [storage](docs/STORAGE.md).
- SSH uses trust on first use: the first host key is pinned in private app storage, and a changed key is rejected. Make the first connection on a trusted network and verify the server independently. There is no in-app key reset; an intentional server key change currently requires clearing app data, which also removes saved profiles and campaigns.
- WiGLE upload sends campaign identifiers and location data to a third-party service when explicitly requested.
- Wardriving uses location supplied by Kismet; this app does not add phone GPS fixes to exports.
- Local scans depend on Android permissions, enabled radios/location services, and platform scan throttling.
- JVM tests and lint do not establish live-server compatibility or physical-device acceptance. See [security reporting](SECURITY.md).

## Usage

1. Launch the app and enter your Kismet server address, port, username, and password
2. Tap **CONNECT** (or tap a saved profile to auto-fill)
3. To connect through an SSH jump host, toggle **Use SSH Tunnel** and fill in the SSH host, port, and username — then either enter a password or paste your private key PEM directly into the key field
4. Navigate with the bottom bar: **Dashboard · Devices · Campaigns · Local Interfaces**
