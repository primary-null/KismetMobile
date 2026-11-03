package com.kismet.mobile.ui.devices

import android.content.Context
import androidx.annotation.DrawableRes
import com.kismet.mobile.R
import java.util.Locale

object DeviceIconHelper {

    @DrawableRes
    fun getIconForManufacturer(manufacturer: String): Int {
        if (manufacturer.isBlank()) return R.drawable.ic_device_generic

        val man = manufacturer.lowercase(Locale.ROOT)
        
        return when {
            man.contains("apple") -> R.drawable.ic_brand_apple
            man.contains("samsung") -> R.drawable.ic_brand_samsung
            man.contains("google") -> R.drawable.ic_brand_google
            man.contains("sony") -> R.drawable.ic_brand_sony
            man.contains("nintendo") -> R.drawable.ic_device_generic
            man.contains("microsoft") -> R.drawable.ic_device_generic
            man.contains("intel") -> R.drawable.ic_brand_intel
            man.contains("cisco") -> R.drawable.ic_brand_cisco
            man.contains("xiaomi") -> R.drawable.ic_brand_xiaomi
            man.contains("huawei") -> R.drawable.ic_brand_huawei
            man.contains("dell") -> R.drawable.ic_brand_dell
            man.contains("hp") || man.contains("hewlett-packard") -> R.drawable.ic_brand_hp
            man.contains("lg") || man.contains("lg electronics") -> R.drawable.ic_brand_lg
            man.contains("asus") -> R.drawable.ic_brand_asus
            man.contains("amazon") -> R.drawable.ic_device_generic
            man.contains("lenovo") -> R.drawable.ic_brand_lenovo
            man.contains("tp-link") -> R.drawable.ic_device_generic
            man.contains("netgear") -> R.drawable.ic_brand_netgear
            man.contains("ubiquiti") -> R.drawable.ic_brand_ubiquiti
            man.contains("aruba") -> R.drawable.ic_device_generic
            man.contains("motorola") -> R.drawable.ic_brand_motorola
            man.contains("nokia") -> R.drawable.ic_brand_nokia
            man.contains("htc") -> R.drawable.ic_brand_htc
            man.contains("zte") -> R.drawable.ic_device_generic
            man.contains("oneplus") -> R.drawable.ic_brand_oneplus
            else -> R.drawable.ic_device_generic
        }
    }
}
