package com.kismet.mobile.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import androidx.navigation.fragment.findNavController
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentDeviceDetailBinding
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import com.kismet.mobile.model.Device
import com.kismet.mobile.viewmodel.KismetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceDetailFragment : Fragment() {

    private var _binding: FragmentDeviceDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private var systemBarTop = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTop = bars.top
            v.setPadding(0, bars.top, 0, v.paddingBottom)
            insets
        }

        windowViewModel.foldingFeature.observe(viewLifecycleOwner) { fold ->
            applyFoldPadding(fold)
        }

        val device = arguments?.getParcelable<Device>("device") ?: return

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        bind(device)

        binding.btnAddCampaign.setOnClickListener { showCampaignPicker(device.key) }

        if (device.isWifiAp && (device.dot11?.numClients ?: 0) > 0) {
            binding.btnViewClients.visibility = View.VISIBLE
            binding.btnViewClients.text = "VIEW ${device.dot11?.numClients} CLIENTS"
            binding.btnViewClients.setOnClickListener {
                viewModel.fetchClientsOf(device.key)
                findNavController().navigate(
                    R.id.action_detail_to_clients,
                    bundleOf("apName" to device.displayName)
                )
            }
        }

        try {
            val btn = binding.root.findViewById<View>(R.id.btn_export_pcap)
            btn?.setOnClickListener { startPcapCapture(device.key) }
        } catch (_: Exception) {}
    }

    private fun bind(d: Device) {
        binding.tvName.text = d.displayName
        binding.tvMac.text = d.mac
        binding.tvPhy.text = d.phyName.ifEmpty { "Unknown" }
        binding.tvType.text = d.type.ifEmpty { "—" }
        binding.tvManuf.text = d.manufacturer.ifEmpty { "—" }
        binding.tvChannel.text = d.channel.ifEmpty { "—" }
        binding.tvFrequency.text = if (d.frequency > 0) "%.1f MHz".format(d.frequency / 1000.0) else "—"

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        binding.tvFirstSeen.text = if (d.firstTime > 0) fmt.format(Date(d.firstTime * 1000)) else "—"
        binding.tvLastSeen.text = if (d.lastTime > 0) fmt.format(Date(d.lastTime * 1000)) else "—"

        binding.tvPackets.text = d.totalPackets.toString()
        binding.tvDataPackets.text = d.dataPackets.toString()
        binding.tvDataSize.text = formatBytes(d.dataSize)

        val sig = d.signal
        if (sig != null && sig.lastSignal != 0) {
            binding.tvSignalLast.text = "${sig.lastSignal} dBm"
            binding.tvSignalMin.text = "${sig.minSignal} dBm"
            binding.tvSignalMax.text = "${sig.maxSignal} dBm"
            val pct = ((sig.lastSignal + 100).coerceIn(0, 100))
            binding.signalBar.progress = pct
            binding.cardSignal.visibility = View.VISIBLE
        } else {
            binding.cardSignal.visibility = View.GONE
        }

        val loc = d.location?.avgLocation
        if (loc != null && loc.lat != 0.0) {
            binding.tvGps.text = "%.6f, %.6f".format(loc.lat, loc.lon)
            binding.rowGps.visibility = View.VISIBLE
        } else {
            binding.rowGps.visibility = View.GONE
        }

        d.dot11?.let { dot11 ->
            binding.tvClients.text = dot11.numClients.toString()
            binding.tvSsids.text = dot11.numSsids.toString()
            binding.cardDot11.visibility = View.VISIBLE
        } ?: run {
            binding.cardDot11.visibility = View.GONE
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private fun showCampaignPicker(deviceKey: String) {
        val campaigns = viewModel.campaigns.value ?: emptyList()
        if (campaigns.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("No Campaigns")
                .setMessage("Create a campaign first from the Campaigns tab.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = campaigns.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add to Campaign")
            .setItems(names) { _, i ->
                viewModel.addDeviceToCampaign(campaigns[i].id, deviceKey)
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, "Added to ${campaigns[i].name}", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .show()
            }
            .show()
    }

    private fun startPcapCapture(deviceKey: String) {
        val state = viewModel.connectionState.value as? com.kismet.mobile.viewmodel.ConnectionState.Connected
        val intent = android.content.Intent(requireContext(), com.kismet.mobile.service.PcapCaptureService::class.java).apply {
            putExtra(com.kismet.mobile.service.PcapCaptureService.EXTRA_DEVICE_KEY, deviceKey)
            if (state != null) {
                putExtra(com.kismet.mobile.service.PcapCaptureService.EXTRA_HOST, state.host)
                putExtra(com.kismet.mobile.service.PcapCaptureService.EXTRA_PORT, state.port)
                putExtra(com.kismet.mobile.service.PcapCaptureService.EXTRA_USER, state.username)
                putExtra(com.kismet.mobile.service.PcapCaptureService.EXTRA_PASS, state.password)
            }
        }
        requireContext().startForegroundService(intent)
        com.google.android.material.snackbar.Snackbar.make(binding.root, "Capture started...", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
    }

    private fun applyFoldPadding(fold: FoldingFeature?) {
        binding.root.doOnLayout {
            val bottomPad = if (fold?.state == FoldingFeature.State.HALF_OPENED &&
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                val viewTop = IntArray(2).also { binding.root.getLocationInWindow(it) }[1]
                (binding.root.height - (fold.bounds.top - viewTop)).coerceAtLeast(0)
            } else 0
            binding.root.setPadding(0, systemBarTop, 0, bottomPad)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
