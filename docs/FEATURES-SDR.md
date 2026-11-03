# SDR & Spectrum Features

## SdrLaunchSheet  (`sdr/SdrLaunchSheet.kt`)

Bottom sheet dialog for launching SDR tools against a source.

```kotlin
SdrLaunchSheet.newInstance(host, port, sourceName).show(parentFragmentManager, "sdr_launch")
```

- Shows available SDR tool integrations (HackRF, RTL-SDR, etc.)
- Triggered from: Dashboard source row tap (SDR sources), source long-press menu, cardSdr click

## SdrSpectrumFragment  (`sdr/SdrSpectrumFragment.kt`)
**Layout:** `fragment_sdr_spectrum.xml`

- Real-time spectrum visualisation using `SpectrumView` custom view
- Navigate to it: long-press `cardSdr` on Dashboard → `R.id.action_dashboard_to_spectrum`

## SpectrumView  (`sdr/SpectrumView.kt`)

Custom `View` that draws spectrum data as a bar graph / waterfall.  
Receives `FloatArray` of power levels via `updateSpectrum(data)`.

## InterfaceGuard  (`sdr/InterfaceGuard.kt`)

Prevents auto-starting monitor mode on the interface used to connect to the Kismet server.

```kotlin
sealed class GuardResult {
    object Safe : GuardResult()
    data class Warning(val reason: String) : GuardResult()
}

InterfaceGuard.check(context, source, serverHost): GuardResult
InterfaceGuard.isActiveInterface(context, source): Boolean
```

**Logic:**
1. Gets active network's interface name via `ConnectivityManager.getLinkProperties(activeNetwork)`
2. Compares `source.interfaceName` to active interface name
3. Also warns if active interface starts with tunnel prefix: `tun`, `wg`, `ipsec`, `vpn`, `ppp`

Used in Dashboard `showSourceActions()` — if `Warning`, shows a confirmation dialog before setting hop.  
Also used to render the 🔗 emoji on source rows.

## RemoteSdrTools / RemoteToolsSheet  (`sdr/RemoteSdrTools.kt`, `sdr/RemoteToolsSheet.kt`)

Helper sheet for launching remote SDR utilities via SSH.  
Provides a list of common tools (gqrx, rtl_tcp, hackrf_info, etc.) with one-tap remote execution.

## SdrIntegration  (`sdr/SdrIntegration.kt`)

Utilities for SDR source detection and integration hooks.  
Checks source type/name strings to classify HackRF vs RTL vs generic SDR.

## SshTunnel  (`sdr/SshTunnel.kt`)

Static singleton managing one SSH session at a time.

```kotlin
// Connect and return local forwarded port
SshTunnel.connect(host, sshPort, username, password?, remoteApiPort): Int

// Tear down session
SshTunnel.disconnect()
```

Uses **sshj** (`net.schmizz.sshj`). Key exchange handles OpenSSH modern algorithms.  
On `ConnectionState.Disconnected`, `KismetViewModel.disconnect()` does NOT call `SshTunnel.disconnect()` automatically — this is called in the error/exception path of `connect()` if login fails after tunnel is up.

## VPN Detection (Dashboard)

```kotlin
// DashboardFragment.updateVpnBadge()
val iface = ConnectivityManager.getLinkProperties(activeNetwork)?.interfaceName ?: ""
val isVpn = listOf("tun", "wg", "ipsec", "vpn", "ppp").any { iface.startsWith(it) }
binding.tvStatus.text = if (isVpn) "ONLINE 🔒" else "ONLINE"
```
