# API & Networking

## KismetApi  (`api/KismetApi.kt`)

All endpoints talk to `http://{host}:{port}/`.  
Retrofit with Gson converter. Auth via `@Header("Authorization")` Basic.

| Method | Endpoint | Returns |
|---|---|---|
| GET | `system/status.json` | `SystemStatus` |
| GET | `datasource/all_sources.json` | `List<DataSource>` |
| GET | `phy/all_phys.json` | `List<PhyEntry>` |
| GET | `devices/all_devices.json` | `List<Device>` |
| GET | `devices/last-time/{ts}/devices.json` | `List<Device>` |
| POST | `datasource/by-uuid/{uuid}/set_channel` | `Response<Unit>` |
| GET `@Streaming` | `pcap/all_packets.pcapng` | `ResponseBody` |
| GET `@Streaming` | `datasource/pcap/by-uuid/{uuid}/packets.pcapng` | `ResponseBody` |
| GET `@Streaming` | `devices/by-key/{key}/pcap/packets.pcapng` | `ResponseBody` |

## KismetClient  (`api/KismetClient.kt`)

```kotlin
KismetClient.build(host, port, username, password)
// → OkHttpClient with BasicAuthInterceptor + 30s timeouts
// → Retrofit instance pointed at http://$host:$port/
```

## WigleApi  (`api/WigleApi.kt`)

Base URL: `https://api.wigle.net/`  
Auth: Basic (`apiName:apiToken` base64).

```kotlin
@Multipart @POST("api/v2/file/upload")
suspend fun upload(@Part file: MultipartBody.Part): Response<WigleUploadResponse>
```

`WigleClient.build(apiName, apiToken)` constructs the client.

## KismetRepository  (`repository/KismetRepository.kt`)

Wraps every API call in `Result<T>`:

```kotlin
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val message: String) : Result<T>()
}
```

Key methods:
- `checkLogin()` — GET system status, used as auth probe
- `getSystemStatus()`, `getAllSources()`, `getAllPhys()`, `getAllDevices()`, `getDevicesSince(ts)`
- `setSourceHop(uuid)` — POST `{"hop":true}` to set_channel
- `streamPcap(outputStream, uuid?, deviceKey?)` — streams pcapng; 50 MB cap; picks endpoint by which param is non-null

## SshTunnel  (`sdr/SshTunnel.kt`)

Uses **sshj** library.  
`SshTunnel.connect(host, sshPort, username, password?, remoteApiPort)` → returns local port number of the forwarded tunnel.  
`SshTunnel.disconnect()` closes the session.  
Used by `KismetViewModel.connect()` when `profile.useSsh == true`; actual host becomes `127.0.0.1`, port becomes the tunnel's local port.
