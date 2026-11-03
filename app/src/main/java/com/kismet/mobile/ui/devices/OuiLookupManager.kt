package com.kismet.mobile.ui.devices

import android.content.Context
import android.util.Log
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object OuiLookupManager {
    private const val TAG = "OuiLookupManager"
    private val ouiMap = mutableMapOf<String, String>()
    private var isLoaded = false

    suspend fun init(context: Context) {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            try {
                context.assets.open("oui.json").use { inputStream ->
                    JsonReader(InputStreamReader(inputStream)).use { reader ->
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var prefix: String? = null
                            var name: String? = null

                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                if (key == "prefix") {
                                    prefix = reader.nextString()
                                } else if (key == "organization") {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        if (reader.nextName() == "name") {
                                            name = reader.nextString()
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (prefix != null && name != null) {
                                // OUI prefixes in JSON are usually like "000000" or "00:00:00" or "00-00-00".
                                // We store them internally as uppercase without colons or hyphens.
                                val cleanPrefix = prefix.replace(Regex("[:-]"), "").uppercase()
                                ouiMap[cleanPrefix] = name
                            }
                        }
                        reader.endArray()
                    }
                }
                isLoaded = true
                Log.d(TAG, "Loaded ${ouiMap.size} OUI entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load OUI database", e)
            }
        }
    }

    fun resolveMac(macAddress: String?): String? {
        if (!isLoaded || macAddress.isNullOrBlank()) return null
        
        // Clean MAC: remove colons, dashes, and make uppercase
        val cleanMac = macAddress.replace(Regex("[:-]"), "").uppercase()
        
        // OUI is usually the first 6 characters (24 bits), sometimes 7 or 9 for smaller allocations.
        // We will try 6 chars as that's standard for IEEE OUI.
        if (cleanMac.length >= 6) {
            val prefix = cleanMac.substring(0, 6)
            return ouiMap[prefix]
        }
        return null
    }
}
