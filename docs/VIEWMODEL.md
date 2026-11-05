# KismetViewModel

`viewmodel/KismetViewModel.kt` — shared across all fragments via `activityViewModels()`.

## LiveData Streams

| LiveData | Type | Updated by |
|---|---|---|
| `connectionState` | `ConnectionState` | `connect()` / `disconnect()` |
| `systemStatus` | `SystemStatus?` | poll every 5s |
| `sources` | `List<DataSource>` | poll every 5s |
| `totalPackets` | `Long` | poll every 5s (sum of phy packet counts) |
| `devices` | `List<Device>` | poll every 5s (incremental after first fetch) |
| `clients` | `List<Device>` | `fetchClientsOf(key)` |
| `channels` | `ChannelData` | `refreshChannels()` |
| `filter` | `String` | `setFilter(q)` |
| `phyFilter` | `PhyFilter` | `setPhyFilter(f)` |
| `campaignOnlyFilter` | `Boolean` | `setCampaignOnlyFilter(b)` |
| `campaigns` | `List<Campaign>` | `refreshCampaigns()` (reads AppPrefs) |
| `profiles` | `List<ConnectionProfile>` | `refreshProfiles()` (reads AppPrefs) |
| `localSightings` | `Map<String, LocalSighting>` | `runLocalScan()` |
| `error` | `String?` | any failed API call |

## ConnectionState

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting   : ConnectionState()
    data class Connected(
        val host: String, val port: Int,
        val username: String, val password: String,
        val isVpnOrSsh: Boolean = false
    ) : ConnectionState()
}
```

## Polling Loop

`startPolling()` runs in `viewModelScope`:
```
loop every 5s:
  fetchStatus()   → systemStatus, totalPackets, sources
  fetchDevices()  → devices (full first time, incremental after)
                  → runCampaignAlerts(newDevices)
```

Incremental device fetch uses `lastPollTime` (Unix seconds). New devices are appended; existing keys are not duplicated.

## Campaign Alert Engine

`runCampaignAlerts(newDevices)` — called on every poll tick, only for ACTIVE campaigns:

- **SURVEY / WARDRIVING**: auto-adds new devices, respecting the survey PHY scope.
- **HUNT**: matches nonblank target MACs or SSID substrings and records each device once.
- **ALERT**: legacy records are retained; rule execution and new Alert campaign creation are disabled.
- Campaign updates copy the lists and publish on the main thread to avoid mutating the previous UI snapshot.

## PHY Classification (companion object)

```kotlin
isWifi(phy)      → contains "802.11"
isBluetooth(phy) → contains "Bluetooth" | "BTLE" | "BT" | "bt_"
isSDR(phy)       → contains "ADSB" | "ADS-B" | "UAV" | "RTL" | "SDR" | "Spectrum" | "hackrf"
```

## Second Latch

```kotlin
suspend fun runLocalScan()   // WiFi scan waits up to 8s, then BT discovery waits up to 12s
fun localConfirmCount(): Int // reads current localSightings size
```

`localSightings` is a `Map<deviceKey, LocalSighting>` — devices the server sees AND the phone currently sees locally.

## PhyFilter enum

`ALL | WIFI | BT | SDR | OTHER`  
`filteredDevices()` applies PHY filter + text search + campaign-only filter in one pass.
