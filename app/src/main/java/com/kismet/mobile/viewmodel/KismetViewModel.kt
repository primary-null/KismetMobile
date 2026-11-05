package com.kismet.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kismet.mobile.api.KismetClient
import com.kismet.mobile.demo.DemoData
import com.kismet.mobile.local.LocalScanner
import com.kismet.mobile.local.LocalSighting
import com.kismet.mobile.local.SecondLatchMatcher
import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.CampaignType
import com.kismet.mobile.model.ChannelData
import com.kismet.mobile.model.ConnectionProfile
import com.kismet.mobile.model.DataSource
import com.kismet.mobile.model.Device
import com.kismet.mobile.model.FiredAlert
import com.kismet.mobile.model.SystemStatus
import com.kismet.mobile.repository.KismetRepository
import com.kismet.mobile.repository.Result
import com.kismet.mobile.storage.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KismetViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPrefs(app)

    private val _connectionState = MutableLiveData<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _systemStatus = MutableLiveData<SystemStatus?>()
    val systemStatus: LiveData<SystemStatus?> = _systemStatus

    private val _sources = MutableLiveData<List<DataSource>>(emptyList())
    val sources: LiveData<List<DataSource>> = _sources

    private val _totalPackets = MutableLiveData<Long>(0L)
    val totalPackets: LiveData<Long> = _totalPackets

    private val _devices = MutableLiveData<List<Device>>(emptyList())
    val devices: LiveData<List<Device>> = _devices

    private val _clients = MutableLiveData<List<Device>>(emptyList())
    val clients: LiveData<List<Device>> = _clients

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _filter = MutableLiveData("")
    val filter: LiveData<String> = _filter

    private val _phyFilter = MutableLiveData(PhyFilter.ALL)
    val phyFilter: LiveData<PhyFilter> = _phyFilter

    private val _campaignOnlyFilter = MutableLiveData(false)
    val campaignOnlyFilter: LiveData<Boolean> = _campaignOnlyFilter

    fun setCampaignOnlyFilter(onlyActive: Boolean) {
        _campaignOnlyFilter.value = onlyActive
    }

    private val _channels = MutableLiveData<ChannelData>(ChannelData())
    val channels: LiveData<ChannelData> = _channels

    private val _profiles = MutableLiveData<List<ConnectionProfile>>()
    val profiles: LiveData<List<ConnectionProfile>> = _profiles

    private val _campaigns = MutableLiveData<List<Campaign>>()
    val campaigns: LiveData<List<Campaign>> = _campaigns

    private val _localSightings = MutableLiveData<Map<String, LocalSighting>>(emptyMap())
    val localSightings: LiveData<Map<String, LocalSighting>> = _localSightings

    private var repository: KismetRepository? = null
    private var pollJob: Job? = null
    private var lastPollTime: Long = 0

    init {
        refreshProfiles()
        refreshCampaigns()
    }

    var isDemoMode = false
        private set

    fun enterDemoMode() {
        isDemoMode = true
        pollJob?.cancel()
        pollJob = null
        repository = null
        _connectionState.value = ConnectionState.Connected(
            host = "demo", port = 2501, username = "", password = "", isVpnOrSsh = false
        )
        _devices.value = DemoData.devices
        _sources.value = DemoData.sources
        _systemStatus.value = DemoData.systemStatus
        _totalPackets.value = DemoData.sources.sumOf { it.packets }
        _campaigns.postValue(DemoData.campaigns)
    }

    // --- Connection ---

    fun connect(profile: ConnectionProfile) {
        _connectionState.value = ConnectionState.Connecting
        viewModelScope.launch {
            // ── Live mode ─────────────────────────────────────────────
            try {
                var actualHost = profile.host
                var actualPort = profile.port
                
                if (profile.useSsh) {
                    actualPort = com.kismet.mobile.sdr.SshTunnel.connect(
                        host = profile.sshHost,
                        sshPort = profile.sshPort,
                        username = profile.sshUsername,
                        password = profile.sshPassword.ifEmpty { null },
                        privateKey = profile.sshPrivateKey.ifEmpty { null },
                        remoteApiPort = profile.port
                    )
                    actualHost = "127.0.0.1"
                }

                val api = KismetClient.build(actualHost, actualPort, profile.username, profile.password)
                repository = KismetRepository(api)
                when (val result = repository!!.checkLogin()) {
                    is Result.Success -> {
                        _connectionState.value = ConnectionState.Connected(
                            host = actualHost, 
                            port = actualPort, 
                            username = profile.username, 
                            password = profile.password,
                            isVpnOrSsh = profile.useSsh
                        )
                        startPolling()
                    }
                    is Result.Error -> {
                        if (profile.useSsh) com.kismet.mobile.sdr.SshTunnel.disconnect()
                        _connectionState.value = ConnectionState.Disconnected
                        _error.value = result.message
                    }
                }
            } catch (e: Exception) {
                if (profile.useSsh) com.kismet.mobile.sdr.SshTunnel.disconnect()
                _connectionState.value = ConnectionState.Disconnected
                _error.value = e.message ?: "SSH Tunnel failed"
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        repository = null
        com.kismet.mobile.sdr.SshTunnel.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _devices.value = emptyList()
        _systemStatus.value = null
        _sources.value = emptyList()
        _totalPackets.value = 0L
        _localSightings.value = emptyMap()
        lastPollTime = 0
    }

    fun forceRefresh() {
        if (_connectionState.value is ConnectionState.Connected) {
            viewModelScope.launch {
                fetchStatus()
                fetchDevices()
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                fetchStatus()
                fetchDevices()
                delay(5_000)
            }
        }
    }

    private suspend fun fetchStatus() {
        when (val r = repository?.getSystemStatus()) {
            is Result.Success -> _systemStatus.value = r.data
            is Result.Error -> _error.value = r.message
            null -> Unit
        }
        when (val r = repository?.getAllPhys()) {
            is Result.Success -> _totalPackets.value = r.data.sumOf { it.packetCount }
            else -> Unit
        }
        when (val r = repository?.getAllSources()) {
            is Result.Success -> _sources.value = r.data
            else -> Unit
        }
    }

    private suspend fun fetchDevices() {
        val repo = repository ?: return
        val result = if (lastPollTime == 0L) repo.getAllDevices()
                     else repo.getDevicesSince(lastPollTime)
        when (result) {
            is Result.Success -> {
                val incomingDevices = result.data
                val currentDevices = _devices.value
                val currentStatus = _systemStatus.value
                withContext(Dispatchers.Default) {
                    val newDevicesList = if (lastPollTime == 0L) {
                        incomingDevices
                    } else {
                        val current = currentDevices?.toMutableList() ?: mutableListOf()
                        val newMap = incomingDevices.associateBy { it.key }
                        current.replaceAll { newMap[it.key] ?: it }
                        val keys = current.map { it.key }.toSet()
                        incomingDevices.forEach { if (it.key !in keys) current.add(it) }
                        current
                    }
                    _devices.postValue(newDevicesList)

                    val maxTime = incomingDevices.maxOfOrNull { it.lastTime } ?: 0L
                    if (maxTime > lastPollTime) {
                        lastPollTime = maxTime
                    } else if (lastPollTime == 0L) {
                        lastPollTime = currentStatus?.timestampSec ?: (System.currentTimeMillis() / 1000)
                    }
                    runCampaignAlerts(incomingDevices)
                }
            }
            is Result.Error -> _error.postValue(result.message)
        }
    }

    // --- Campaign Alert Engine ---

    private fun runCampaignAlerts(newDevices: List<Device>) {
        val campaigns = _campaigns.value ?: return
        val active = campaigns.filter {
            it.status == CampaignStatus.ACTIVE && it.type != CampaignType.ALERT
        }
        if (active.isEmpty()) return
        var anyChanged = false
        val updated = campaigns.map { original ->
            if (original.status != CampaignStatus.ACTIVE || original.type == CampaignType.ALERT) {
                return@map original
            }
            val keys = original.deviceKeys.toMutableList()
            val alerts = original.firedAlerts.toMutableList()
            var changed = false
            if (original.type == CampaignType.SURVEY || original.type == CampaignType.WARDRIVING) {
                val existing = keys.toHashSet()
                val toAdd = newDevices.filter { d ->
                    d.key !in existing && when (original.surveyScope) {
                        "WIFI" -> isWifi(d.phyName)
                        "BT"   -> isBluetooth(d.phyName)
                        else   -> true
                    }
                }
                if (toAdd.isNotEmpty()) {
                    keys.addAll(toAdd.map { it.key })
                    changed = true
                }
            }
            if (original.type == CampaignType.HUNT) {
                val existing = keys.toHashSet()
                newDevices.forEach { device ->
                    if (device.key in existing) return@forEach
                    val macMatch  = original.targetMacs.any  { (device.mac ?: "").equals(it.trim(), ignoreCase = true) }
                    val ssidMatch = original.targetSsids.any { device.displayName.contains(it.trim(), ignoreCase = true) }
                    if (macMatch || ssidMatch) {
                        keys.add(device.key)
                        alerts.add(
                            FiredAlert(0, device.key, "HUNT: ${device.displayName} (${device.mac ?: "No MAC"})")
                        )
                        changed = true
                    }
                }
            }
            if (changed) {
                anyChanged = true
                val next = original.copy(deviceKeys = keys, firedAlerts = alerts)
                prefs.updateCampaign(next)
                next
            } else original
        }
        if (anyChanged) {
            _campaigns.postValue(updated)
        }
    }

    // --- Filters ---

    fun setFilter(q: String) { _filter.value = q }

    fun setPhyFilter(f: PhyFilter) { _phyFilter.value = f }

    fun filteredDevices(): List<Device> {
        val q = _filter.value?.lowercase() ?: ""
        val phy = _phyFilter.value ?: PhyFilter.ALL
        val campOnly = _campaignOnlyFilter.value ?: false
        val activeCampaignIds = _campaigns.value?.filter { it.status == CampaignStatus.ACTIVE }?.map { it.id } ?: emptyList()
        
        return (_devices.value ?: emptyList()).filter { d ->
            val matchesPhy = when (phy) {
                PhyFilter.ALL -> true
                PhyFilter.WIFI -> isWifi(d.phyName)
                PhyFilter.BT -> isBluetooth(d.phyName)
                PhyFilter.SDR -> isSDR(d.phyName)
                PhyFilter.OTHER -> !isWifi(d.phyName) && !isBluetooth(d.phyName) && !isSDR(d.phyName)
            }
            val matchesQ = q.isEmpty() ||
                d.displayName.lowercase().contains(q) ||
                d.mac.lowercase().contains(q) ||
                d.phyName.lowercase().contains(q) ||
                d.manufacturer.lowercase().contains(q)
                
            val matchesCamp = if (campOnly) {
                val activeCampaigns = _campaigns.value?.filter { it.status == com.kismet.mobile.model.CampaignStatus.ACTIVE } ?: emptyList()
                activeCampaigns.any { it.deviceKeys.contains(d.key) }
            } else true

            matchesPhy && matchesQ && matchesCamp
        }
    }

    fun campaignDevices(campaignId: String): List<Device> {
        val campaign = _campaigns.value?.find { it.id == campaignId } ?: return emptyList()
        return (_devices.value ?: emptyList()).filter { it.key in campaign.deviceKeys }
    }

    // --- Clients ---

    fun fetchClientsOf(deviceKey: String) {
        val ap = _devices.value?.find { it.key == deviceKey }
        val clientKeys = ap?.dot11?.associatedClientMap?.values
            ?.map { it.toString() }?.toHashSet() ?: emptySet()
        
        _clients.value = if (clientKeys.isEmpty()) emptyList()
            else (_devices.value ?: emptyList()).filter { it.key in clientKeys }
    }

    fun clearClients() { _clients.value = emptyList() }

    // --- Source control ---

    fun setSourceHop(uuid: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository?.setSourceHop(uuid)) {
                is Result.Success -> onResult(true, "Hopping enabled")
                is Result.Error -> onResult(false, r.message)
                null -> onResult(false, "Not connected")
            }
        }
    }

    private fun refreshSources() {
        viewModelScope.launch {
            when (val r = repository?.getAllSources()) {
                is Result.Success -> _sources.value = r.data
                else -> Unit
            }
        }
    }

    fun pauseSource(uuid: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository?.pauseSource(uuid)) {
                is Result.Success -> { refreshSources(); onResult(true, "Paused") }
                is Result.Error   -> onResult(false, r.message)
                null              -> onResult(false, "Not connected")
            }
        }
    }

    fun resumeSource(uuid: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository?.resumeSource(uuid)) {
                is Result.Success -> { refreshSources(); onResult(true, "Resumed") }
                is Result.Error   -> onResult(false, r.message)
                null              -> onResult(false, "Not connected")
            }
        }
    }

    fun addSource(definition: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository?.addSource(definition)) {
                is Result.Success -> { refreshSources(); onResult(true, "Source added") }
                is Result.Error   -> onResult(false, r.message)
                null              -> onResult(false, "Not connected")
            }
        }
    }

    // --- Second Latch ---

    suspend fun runLocalScan() {
        val scanner = LocalScanner(getApplication())
        val wifi = scanner.scanWifi()
        val bt = scanner.scanBluetooth()
        val matched = SecondLatchMatcher.match(wifi + bt, _devices.value ?: emptyList())
        _localSightings.postValue(matched)
    }

    fun localConfirmCount(): Int = _localSightings.value?.size ?: 0

    // --- Saved Profiles ---

    fun refreshProfiles() { _profiles.postValue(prefs.getProfiles()) }

    fun saveProfile(profile: ConnectionProfile) {
        prefs.saveProfile(profile)
        refreshProfiles()
    }

    fun deleteProfile(id: String) {
        prefs.deleteProfile(id)
        refreshProfiles()
    }

    // --- Campaigns ---

    fun refreshCampaigns() {
        if (isDemoMode) return
        _campaigns.postValue(prefs.getCampaigns())
    }

    fun createCampaign(
        name: String,
        type: CampaignType = CampaignType.SURVEY,
        description: String = "",
    ): Campaign {
        val c = prefs.createCampaign(name, type, description)
        refreshCampaigns()
        return c
    }

    fun updateCampaign(campaign: Campaign) {
        prefs.updateCampaign(campaign)
        refreshCampaigns()
    }

    fun setCampaignStatus(id: String, status: CampaignStatus) {
        val campaigns = _campaigns.value?.toMutableList() ?: return
        val idx = campaigns.indexOfFirst { it.id == id }
        if (idx < 0) return
        val c = campaigns[idx]
        val updated = c.copy(
            status = status,
            startedAt = if (status == CampaignStatus.ACTIVE && c.startedAt == null)
                System.currentTimeMillis() else c.startedAt,
            endedAt = if (status == CampaignStatus.COMPLETE)
                System.currentTimeMillis() else c.endedAt,
        )
        campaigns[idx] = updated
        prefs.saveCampaigns(campaigns)
        refreshCampaigns()
    }

    fun addDeviceToCampaign(campaignId: String, deviceKey: String) {
        prefs.addDeviceToCampaign(campaignId, deviceKey)
        refreshCampaigns()
    }

    fun deleteCampaign(id: String) {
        prefs.deleteCampaign(id)
        refreshCampaigns()
    }

    fun refreshChannels() {
        viewModelScope.launch {
            when (val r = repository?.getChannels()) {
                is Result.Success -> _channels.value = r.data
                else -> Unit
            }
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        fun isWifi(phy: String?) = (phy ?: "").contains("802.11", ignoreCase = true)
        fun isBluetooth(phy: String?) =
            (phy ?: "").contains("Bluetooth", ignoreCase = true) ||
            (phy ?: "").contains("BTLE", ignoreCase = true) ||
            (phy ?: "").contains("BT", ignoreCase = true) ||
            (phy ?: "").contains("bt_", ignoreCase = true)
        fun isSDR(phy: String?) =
            (phy ?: "").contains("ADSB", ignoreCase = true) ||
            (phy ?: "").contains("ADS-B", ignoreCase = true) ||
            (phy ?: "").contains("UAV", ignoreCase = true) ||
            (phy ?: "").contains("RTL", ignoreCase = true) ||
            (phy ?: "").contains("SDR", ignoreCase = true) ||
            (phy ?: "").contains("Spectrum", ignoreCase = true) ||
            (phy ?: "").contains("hackrf", ignoreCase = true)
    }
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val host: String, 
        val port: Int, 
        val username: String, 
        val password: String,
        val isVpnOrSsh: Boolean = false
    ) : ConnectionState()
}

enum class PhyFilter { ALL, WIFI, BT, SDR, OTHER }
