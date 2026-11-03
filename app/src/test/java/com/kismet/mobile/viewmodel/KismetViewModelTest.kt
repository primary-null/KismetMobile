package com.kismet.mobile.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.Device
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KismetViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `filteredDevices applies campaignOnlyFilter correctly`() {
        val mockApp = mockk<android.app.Application>(relaxed = true)
        val viewModel = KismetViewModel(mockApp)

        val activeCampaign = Campaign(id = "1", name = "Active", status = CampaignStatus.ACTIVE, deviceKeys = mutableListOf("D1", "D3"))
        val inactiveCampaign = Campaign(id = "2", name = "Inactive", status = CampaignStatus.PAUSED, deviceKeys = mutableListOf("D2", "D3"))
        
        // Use reflection to set the internal state since we don't want to mock the whole API layer
        val devicesField = KismetViewModel::class.java.getDeclaredField("_devices")
        devicesField.isAccessible = true
        val _devices = devicesField.get(viewModel) as androidx.lifecycle.MutableLiveData<List<Device>>
        
        val campaignsField = KismetViewModel::class.java.getDeclaredField("_campaigns")
        campaignsField.isAccessible = true
        val _campaigns = campaignsField.get(viewModel) as androidx.lifecycle.MutableLiveData<List<Campaign>>

        val device1 = Device(key = "D1", mac = "00:11")
        val device2 = Device(key = "D2", mac = "00:22")
        val device3 = Device(key = "D3", mac = "00:33")
        val device4 = Device(key = "D4", mac = "00:44")

        _campaigns.value = listOf(activeCampaign, inactiveCampaign)
        _devices.value = listOf(device1, device2, device3, device4)

        // Test with campaignOnlyFilter = false (default)
        viewModel.setCampaignOnlyFilter(false)
        assertEquals(4, viewModel.filteredDevices().size)

        // Test with campaignOnlyFilter = true
        viewModel.setCampaignOnlyFilter(true)
        val filtered = viewModel.filteredDevices()
        
        assertEquals(2, filtered.size)
        assertEquals("D1", filtered[0].key)
        assertEquals("D3", filtered[1].key)
    }
}
