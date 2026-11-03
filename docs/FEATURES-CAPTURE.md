# Capture & Export Features

## PCAP Capture — PcapCaptureService  (`service/PcapCaptureService.kt`)

Foreground service that streams a `.pcapng` file from the Kismet server.

### Starting

```kotlin
val intent = Intent(ctx, PcapCaptureService::class.java).apply {
    putExtra(PcapCaptureService.EXTRA_UUID, uuid)          // datasource UUID (optional)
    putExtra(PcapCaptureService.EXTRA_DEVICE_KEY, key)     // device key (optional)
    putExtra(PcapCaptureService.EXTRA_HOST, host)
    putExtra(PcapCaptureService.EXTRA_PORT, port)
    putExtra(PcapCaptureService.EXTRA_USER, username)
    putExtra(PcapCaptureService.EXTRA_PASS, password)
}
ctx.startForegroundService(intent)
```

Pass either `EXTRA_UUID` (capture one source) or `EXTRA_DEVICE_KEY` (capture one device) or neither (capture all packets).

### Stopping

Send `Intent(action = "STOP")` to the service, or the service stops itself after:
- Stream ends (server closes connection)
- 50 MB written (`streamPcap` cap in repository)

On destroy: shares the file via `FileProvider` + `ACTION_SEND` chooser if file is non-empty.

### Manifest requirements

```xml
<service android:name=".service.PcapCaptureService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

`res/xml/file_paths.xml` maps `cacheDir` as the share root (`<cache-path name="pcap" path="." />`).

### Entry points

| Where | How |
|---|---|
| Dashboard source long-press → Export PCAP | `startPcapCapture(uuid = src.uuid)` |
| Device detail → EXPORT PCAP button | `startPcapCapture(deviceKey = device.key)` |

---

## WiGLE Export — WigleExporter  (`export/WigleExporter.kt`)

Converts a `List<Device>` to WiGLE WiFi 1.4 CSV format.

```kotlin
WigleExporter.toCsv(devices, latitude, longitude): String
```

Output format:
```
WigleWifi-1.4,appRelease=KismetMobile,...
MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,...
```

Handles both WiFi (uses BSSID/SSID/channel) and Bluetooth devices (type="BT").

### WiGLE Upload Flow (CampaignDetailFragment)

1. Load `WigleProfile` from AppPrefs — abort with Snackbar if empty
2. Get `campaignDevices(campaignId)` — abort if empty
3. `WigleExporter.toCsv(devices)` → write to `cacheDir/wigle_export.csv`
4. Build `MultipartBody.Part` from file
5. `WigleClient.build(apiName, apiToken).upload(body)` on `Dispatchers.IO`
6. Show success/failure Snackbar on main thread

---

## Second Latch — LocalScanner  (`local/LocalScanner.kt`)

Scans the phone's local radio environment and returns sightings for cross-referencing with Kismet.

### WiFi scan (`scanWifi()`)
Uses deprecated `WifiManager.startScan()` + `scanResults`.  
Requires `ACCESS_FINE_LOCATION`.

### Bluetooth scan (`scanBluetooth()`)
- BLE: `BluetoothLeScanner.startScan()` callback — adds results continuously
- Classic: `BluetoothAdapter.startDiscovery()` via `BroadcastReceiver` on `ACTION_FOUND`
- Times out after 12 seconds via `withTimeoutOrNull(12_000)`
- Cleans up: `stopScan()`, `cancelDiscovery()`, `unregisterReceiver()`

Requires (Android 12+): `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` at runtime.

### SecondLatchMatcher  (`local/SecondLatchMatcher.kt`)

```kotlin
SecondLatchMatcher.match(sightings, devices): Map<String, LocalSighting>
// Returns: Map<deviceKey, LocalSighting> for devices seen by both
```

MAC normalisation: uppercase + strip colons (`AA:BB:CC` → `AABBCC`) before comparing.

### DevOptionsChecker  (`local/DevOptionsChecker.kt`)

```kotlin
DevOptionsChecker.isEnabled(context): Boolean
// Reads Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
// Used to prompt user to disable Wi-Fi scan throttling for better results
```
