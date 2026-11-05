package com.kismet.mobile.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kismet.mobile.model.DataSource
import com.kismet.mobile.model.Device
import com.kismet.mobile.databinding.ItemDeviceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAdapter(private val onClick: (Device) -> Unit = {}) : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DIFF) {

    var localSightings: Map<String, com.kismet.mobile.local.LocalSighting> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var sourcesMap: Map<String, DataSource> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.root.setOnClickListener { onClick(device) }

            val manufacturer = device.manufacturer ?: ""
            val phyName = device.phyName ?: ""
            val channel = device.channel ?: ""
            val seenBy = device.seenBy ?: emptyList()
            val resolvedManuf = if (manufacturer.isBlank()) {
                OuiLookupManager.resolveMac(device.mac ?: "") ?: ""
            } else {
                manufacturer
            }
            val iconRes = DeviceIconHelper.getIconForManufacturer(resolvedManuf)
            try {
                binding.ivIcon.setImageResource(iconRes)
            } catch (_: android.content.res.Resources.NotFoundException) {
                binding.ivIcon.setImageResource(com.kismet.mobile.R.drawable.ic_device_generic)
            }
            
            binding.tvName.text = device.displayName
            binding.tvMac.text = device.mac ?: ""
            binding.tvPhy.text = phyName.ifEmpty { "Unknown" }
            binding.tvManuf.text = resolvedManuf.ifEmpty { "—" }
            val signal = device.signal?.lastSignal
            binding.tvSignal.text = if (signal != null && signal != 0) "${signal} dBm" else "—"
            binding.tvLastSeen.text = if (device.lastTime <= 0) {
                "—"
            } else {
                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(device.lastTime * 1000))
            }
            binding.tvChannel.text = channel.ifEmpty { "—" }

            binding.tvPhy.setTextColor(phyColor(phyName))

            val isProximal = localSightings.containsKey(device.key)
            binding.tvProximalBadge.visibility = if (isProximal) android.view.View.VISIBLE else android.view.View.GONE

            val captureTag = seenBy.mapNotNull { sourcesMap[it.uuid] }
                .firstOrNull { src ->
                    src.type.orEmpty().contains("ubertooth", true) ||
                    src.type.orEmpty().contains("hackrf",    true) ||
                    src.type.orEmpty().contains("rtl",       true) ||
                    src.name.orEmpty().contains("hackrf",    true) ||
                    src.name.orEmpty().contains("ubertooth", true)
                }?.let { src ->
                    when {
                        src.type.orEmpty().contains("ubertooth", true) || src.name.orEmpty().contains("ubertooth", true) -> "UBERTOOTH"
                        src.type.orEmpty().contains("hackrf",    true) || src.name.orEmpty().contains("hackrf",    true) -> "HACKRF"
                        src.type.orEmpty().contains("rtl",       true)                                         -> "RTL-SDR"
                        else -> null
                    }
                }
            if (captureTag != null) {
                binding.tvSourceBadge.text = captureTag
                binding.tvSourceBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvSourceBadge.visibility = android.view.View.GONE
            }
        }

        private fun phyColor(phy: String): Int {
            val ctx = binding.root.context
            return when {
                phy.contains("802.11", ignoreCase = true) ->
                    ctx.getColor(com.kismet.mobile.R.color.phy_wifi)
                phy.contains("Bluetooth", ignoreCase = true) || phy.contains("BTLE", ignoreCase = true) ->
                    ctx.getColor(com.kismet.mobile.R.color.phy_bt)
                phy.contains("ADSB", ignoreCase = true) ->
                    ctx.getColor(com.kismet.mobile.R.color.phy_adsb)
                else -> ctx.getColor(com.kismet.mobile.R.color.phy_other)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Device>() {
            override fun areItemsTheSame(a: Device, b: Device) = a.key == b.key
            override fun areContentsTheSame(a: Device, b: Device) = a == b
        }
    }
}
