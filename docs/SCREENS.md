# Screens & Fragments

## Connect Screen
**Fragment:** `ui/connect/ConnectFragment.kt`  
**Layout:** `fragment_connect.xml`

- Saved profiles shown in `RecyclerView` via `ProfileAdapter`; tap to pre-fill form
- Fields: Host/IP, Port, Username, Password, SSH toggle (expands SSH fields)
- CONNECT → `viewModel.connect(profile)` → navigates to dashboard on `Connected` state
- SAVE → dialog asks for profile name → `viewModel.saveProfile()`
- Error shown as Snackbar from `viewModel.error`

## Dashboard
**Fragment:** `ui/dashboard/DashboardFragment.kt`  
**Layout:** `fragment_dashboard.xml` (SwipeRefreshLayout > ScrollView)

- Header: server address, ONLINE/ONLINE🔒 badge (VPN detection), DISCONNECT button
- PHY cards: WiFi / BT / SDR / Other counts — tap to jump to Devices with filter applied
- Stats grid: total devices, sources, packets, memory, server time
- CAPTURE SOURCES list (dynamic rows built in code):
  - Colored dot (green=running, red=error, teal=SDR)
  - 🔗 badge if source interface = phone's active network interface
  - SDR sources tappable → SdrLaunchSheet
  - Long-press → showSourceActions (Set Hopping / Open SDR Sheet / Export PCAP)
- Pull-to-refresh → `viewModel.forceRefresh()`

## Devices
**Fragment:** `ui/devices/DevicesFragment.kt`  
**Layout:** `fragment_devices.xml`  
**Adapter:** `DeviceAdapter.kt`

- SearchView filters by name/MAC/phy/manufacturer
- PHY chips filter: ALL / WiFi / BT / SDR / Other
- Campaign-only toggle
- Phone badge (📱) on items in `localSightings` map
- Tap item → DeviceDetailFragment; long-press → Second Latch scan prompt

## Device Detail
**Fragment:** `ui/detail/DeviceDetailFragment.kt`  
**Layout:** `fragment_device_detail.xml`

- Shows: name, MAC, phy, type, manufacturer, channel, frequency, first/last seen, packets, data size
- Signal card (bar + dBm min/max/last) — hidden if no signal data
- GPS row — hidden if no location
- Dot11 card (clients, SSIDs) — hidden if not WiFi
- ADD TO CAMPAIGN → picker dialog
- VIEW N CLIENTS → navigates to ClientsFragment (hidden if 0 clients)
- EXPORT PCAP → starts PcapCaptureService with device key

## Campaigns
**Fragment:** `ui/campaigns/CampaignsFragment.kt`  
**Layout:** `fragment_campaigns.xml`  
**Adapter:** `CampaignAdapter.kt`

- FAB → type picker (Survey / Hunt / Wardriving) → name dialog → create; stored legacy Alert campaigns have disabled rules
- List shows type badge (color-coded), status chip, alert count
- Tap → CampaignDetailFragment

## Campaign Detail
**Fragment:** `ui/campaigns/CampaignDetailFragment.kt`  
**Layout:** `fragment_campaign_detail.xml`

- Header: name, type label, status (colored), device count
- START / PAUSE / END buttons → `viewModel.setCampaignStatus()`
- WIGLE button → `uploadToWigle()` (builds CSV, POSTs to WiGLE API)
- ALERTS section: last 5 fired alerts with ⚡ prefix
- Device RecyclerView (campaign-tracked devices only)

## Local Interfaces
**Fragment:** `ui/local/LocalDeviceFragment.kt`  
**Layout:** `fragment_local_device.xml`

- Permission banner (location + BT scan + BT connect) — requests at runtime on Android 12+
- WiFi card: SSID, BSSID, IP, signal (dBm + /5 level), channel (2.4/5/6 GHz), speed
- Bluetooth card: state, name, address, paired device list
- SCAN & MATCH → `viewModel.runLocalScan()` (suspend, ~12s) → snackbar with match count
- REFRESH → re-reads current interface state

## Settings
**Fragment:** `ui/settings/SettingsFragment.kt`  
**Layout:** `fragment_settings.xml`

- WiGLE API name + token fields → `AppPrefs.saveWigleProfile()`
- (Other settings TBD)

## Clients
**Fragment:** `ui/clients/ClientsFragment.kt`  
**Layout:** `fragment_clients.xml`

- Shows associated clients of a selected AP
- Populated by `viewModel.fetchClientsOf(deviceKey)` — cross-references `dot11.associatedClientMap` MACs against device list
