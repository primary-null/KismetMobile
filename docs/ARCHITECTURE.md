# KismetApp — Architecture Overview

## Package Layout

```
com.kismet.mobile/
├── MainActivity.kt          — single-activity host, bottom nav setup
├── api/                     — Retrofit interfaces + OkHttp clients
├── export/                  — file export helpers (WiGLE CSV)
├── local/                   — on-device scanner (Second Latch)
├── model/                   — plain data classes (Gson-serialised)
├── repository/              — thin layer over API, returns Result<T>
├── sdr/                     — SDR/spectrum UI + SSH tunnel + InterfaceGuard
├── service/                 — PcapCaptureService foreground service
├── storage/                 — AppPrefs SharedPreferences wrapper
├── ui/                      — fragments & adapters per feature
│   ├── campaigns/
│   ├── clients/
│   ├── connect/
│   ├── dashboard/
│   ├── detail/
│   ├── devices/
│   ├── local/
│   └── settings/
└── viewmodel/               — KismetViewModel (shared, activityViewModels)
```

## Data Flow

```
Kismet server (HTTP/REST)
        │
        ▼
  KismetApi (Retrofit)
        │
        ▼
  KismetRepository          ← Result.Success / Result.Error
        │
        ▼
  KismetViewModel           ← LiveData; polls every 5s
   ├── devices
   ├── sources
   ├── systemStatus
   ├── totalPackets
   ├── campaigns             ← persisted in AppPrefs (JSON)
   └── localSightings        ← Second Latch cross-reference map
        │
        ▼
  Fragments (observe LiveData)
```

## Key Design Decisions

| Decision | Reason |
|---|---|
| Single Activity + NavComponent | Back-stack management across bottom tabs |
| `activityViewModels()` everywhere | ViewModel survives tab switches |
| `Result<T>` wrapper | Avoids exceptions crossing repo→VM boundary |
| Campaign alert engine in ViewModel | Runs on every device poll tick, no background service needed |
| `postValue` for localSightings | `runLocalScan` is suspend; called from non-main coroutine |

## Navigation Graph (nav_graph.xml)

```
connectFragment
  └─ action_connect_to_dashboard ──► dashboardFragment
                                         │ (bottom nav tabs — independent back stacks)
                                         ├── devicesFragment ──► deviceDetailFragment ──► clientsFragment
                                         ├── campaignsFragment ──► campaignDetailFragment
                                         ├── localDeviceFragment
                                         └── dashboardFragment ──► sdrSpectrumFragment
```

## Edge-to-Edge Insets Pattern (applied to every fragment)

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.setPadding(0, bars.top, 0, 0)
    insets
}
```
