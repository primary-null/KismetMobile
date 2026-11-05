package com.kismet.mobile.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.CampaignType
import com.kismet.mobile.model.ConnectionProfile
import com.kismet.mobile.model.WigleProfile

class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("kismet_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- Connection Profiles ---

    fun getProfiles(): List<ConnectionProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try { gson.fromJson<List<ConnectionProfile>>(json, object : TypeToken<List<ConnectionProfile>>() {}.type) ?: emptyList() }
        catch (_: Exception) { emptyList() }
    }

    fun saveProfile(profile: ConnectionProfile) {
        val list = getProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(0, profile)
        prefs.edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    fun deleteProfile(id: String) {
        val list = getProfiles().filter { it.id != id }
        prefs.edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    // --- Campaigns ---

    fun getCampaigns(): List<Campaign> {
        val json = prefs.getString(KEY_CAMPAIGNS, null) ?: return emptyList()
        return try { gson.fromJson<List<Campaign>>(json, object : TypeToken<List<Campaign>>() {}.type) ?: emptyList() }
        catch (_: Exception) { emptyList() }
    }

    fun saveCampaigns(campaigns: List<Campaign>) {
        prefs.edit().putString(KEY_CAMPAIGNS, gson.toJson(campaigns)).apply()
    }

    fun updateCampaign(campaign: Campaign) {
        val list = getCampaigns().toMutableList()
        val idx = list.indexOfFirst { it.id == campaign.id }
        if (idx >= 0) list[idx] = campaign else list.add(0, campaign)
        saveCampaigns(list)
    }

    fun addDeviceToCampaign(campaignId: String, deviceKey: String) {
        val campaigns = getCampaigns().toMutableList()
        val idx = campaigns.indexOfFirst { it.id == campaignId }
        if (idx >= 0 && !campaigns[idx].deviceKeys.contains(deviceKey)) {
            campaigns[idx].deviceKeys.add(deviceKey)
            saveCampaigns(campaigns)
        }
    }

    fun createCampaign(
        name: String,
        type: CampaignType = CampaignType.SURVEY,
        description: String = "",
    ): Campaign {
        val campaign = Campaign(name = name, type = type, description = description)
        val list = getCampaigns().toMutableList()
        list.add(0, campaign)
        saveCampaigns(list)
        return campaign
    }

    fun deleteCampaign(id: String) {
        saveCampaigns(getCampaigns().filter { it.id != id })
    }

    // --- WiGLE ---

    fun getWigleProfile(): WigleProfile {
        val json = prefs.getString(KEY_WIGLE, null) ?: return WigleProfile()
        return try { gson.fromJson(json, WigleProfile::class.java) ?: WigleProfile() }
        catch (_: Exception) { WigleProfile() }
    }

    fun saveWigleProfile(profile: WigleProfile) {
        prefs.edit().putString(KEY_WIGLE, gson.toJson(profile)).apply()
    }

    companion object {
        private const val KEY_PROFILES = "connection_profiles"
        private const val KEY_CAMPAIGNS = "campaigns"
        private const val KEY_WIGLE = "wigle_profile"
    }
}
