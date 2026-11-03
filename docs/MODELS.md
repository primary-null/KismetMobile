# Data Models

All models live in `model/`. Gson-serialised; field names match Kismet JSON keys via `@SerializedName`.

## Device  (`model/Device.kt`)

Top-level object from Kismet device list.

| Field | Kismet key | Notes |
|---|---|---|
| `key` | `kismet.device.base.key` | Unique stable ID |
| `mac` | `kismet.device.base.macaddr` | Uppercase colon-separated |
| `phyName` | `kismet.device.base.phyname` | "IEEE802.11", "Bluetooth", etc. |
| `displayName` | `kismet.device.base.commonname` | |
| `manufacturer` | `kismet.device.base.manuf` | |
| `channel` | `kismet.device.base.channel` | |
| `frequency` | `kismet.device.base.frequency` | Hz × 1000 → MHz |
| `signal` | `kismet.device.base.signal` | → `SignalData` |
| `dot11` | `dot11.device` | → `Dot11Data?` (WiFi only) |
| `location` | `kismet.device.base.location` | → `LocationData?` |
| `firstTime` | `kismet.device.base.first_time` | Unix seconds |
| `lastTime` | `kismet.device.base.last_time` | Unix seconds |
| `totalPackets` | `kismet.device.base.packets.total` | |
| `dataPackets` | `kismet.device.base.packets.data` | |
| `dataSize` | `kismet.device.base.datasize` | Bytes |
| `type` | `kismet.device.base.type` | |

`isWifiAp`: computed from `dot11` non-null.

## DataSource  (`model/SystemStatus.kt`)

| Field | Kismet key |
|---|---|
| `uuid` | `kismet.datasource.uuid` |
| `name` | `kismet.datasource.name` |
| `type` | `kismet.datasource.type` |
| `interfaceName` | `kismet.datasource.interface` |
| `hardware` | `kismet.datasource.hardware` |
| `running` | `kismet.datasource.running` (1=yes) |
| `error` | `kismet.datasource.error` (1=yes) |
| `packets` | `kismet.datasource.packets.total` |

## SystemStatus  (`model/SystemStatus.kt`)

`deviceCount`, `memoryRss`, `timestampSec` — from `kismet.system.*` keys.

## Campaign  (`model/Campaign.kt`)

Stored locally in SharedPreferences as JSON.

```kotlin
data class Campaign(
    val id: String,                        // UUID
    val name: String,
    val type: CampaignType,                // SURVEY | HUNT | ALERT | WARDRIVING
    val description: String,
    val alertRules: List<AlertRule>,
    val deviceKeys: MutableList<String>,   // tracked device keys
    val firedAlerts: MutableList<FiredAlert>,
    val status: CampaignStatus,            // DRAFT | ACTIVE | PAUSED | COMPLETE
    val startedAt: Long?,
    val endedAt: Long?,
    val createdAt: Long,
)
```

## AlertRule / FiredAlert  (`model/AlertRule.kt`)

```kotlin
enum class AlertTrigger { NEW_DEVICE, MAC_SEEN, SSID_SEEN, SIGNAL_THRESHOLD, CLIENT_COUNT }
enum class AlertAction  { NOTIFY, LOG }

data class AlertRule(val trigger: AlertTrigger, val value: String, val action: AlertAction)
data class FiredAlert(val ruleIndex: Int, val deviceKey: String, val message: String, val timestamp: Long)
```

## ConnectionProfile  (`model/ConnectionProfile.kt`)

Saved connection profile. Fields: `id`, `name`, `host`, `port`, `username`, `password`, `useSsh`, `sshHost`, `sshPort`, `sshUsername`, `sshPassword`.

## WigleProfile  (`model/WigleProfile.kt`)

```kotlin
data class WigleProfile(val apiName: String, val apiToken: String, val autoUpload: Boolean)
```
Saved in AppPrefs under key `"wigle_profile"`.
