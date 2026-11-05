package com.kismet.mobile.storage

import android.content.Context
import android.content.SharedPreferences
import com.kismet.mobile.model.WigleProfile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPrefsTest {
    @Test fun `JSON null and malformed preferences fall back to empty values`() {
        for (json in listOf("null", "{broken")) {
            val context = mockk<Context>()
            val storage = mockk<SharedPreferences>()
            every { context.getSharedPreferences("kismet_prefs", Context.MODE_PRIVATE) } returns storage
            every { storage.getString(any(), any()) } returns json
            val prefs = AppPrefs(context)
            assertEquals(emptyList<Any>(), prefs.getProfiles())
            assertEquals(emptyList<Any>(), prefs.getCampaigns())
            assertEquals(WigleProfile(), prefs.getWigleProfile())
        }
    }
}
