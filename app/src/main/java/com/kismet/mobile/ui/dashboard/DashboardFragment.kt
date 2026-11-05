package com.kismet.mobile.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kismet.mobile.ui.navigateIfPresent
import com.google.android.material.snackbar.Snackbar
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentDashboardBinding
import com.kismet.mobile.model.DataSource
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kismet.mobile.sdr.GuardResult
import com.kismet.mobile.sdr.InterfaceGuard
import com.kismet.mobile.sdr.SdrLaunchSheet
import com.kismet.mobile.service.PcapCaptureService
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.PhyFilter
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private var systemBarTop = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
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

        binding.swipeRefresh.setColorSchemeColors(
            android.graphics.Color.parseColor("#39FF14")
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            android.graphics.Color.parseColor("#1C2B30")
        )
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.forceRefresh()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnDisconnect.setOnClickListener { viewModel.disconnect() }

        binding.cardWifi.setOnClickListener { goToDevices(PhyFilter.WIFI) }
        binding.cardBt.setOnClickListener { goToDevices(PhyFilter.BT) }
        binding.cardOther.setOnClickListener { goToDevices(PhyFilter.OTHER) }
        binding.cellTotalDevices.setOnClickListener { goToDevices(PhyFilter.ALL) }
        binding.cardSdr.setOnClickListener { openSdrSheet() }
        binding.cardSdr.setOnLongClickListener {
            findNavController().navigateIfPresent(R.id.action_dashboard_to_spectrum)
            true
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            if (state is ConnectionState.Connected) {
                binding.tvServerAddress.text = "${state.host}:${state.port}"
                updateVpnBadge()
            }
        }

        viewModel.systemStatus.observe(viewLifecycleOwner) { status ->
            if (status == null) return@observe
            binding.tvDeviceCount.text = status.deviceCount.toString()
            binding.tvMemory.text = formatBytes(status.memoryRss)
            val uptime = Date(status.timestampSec * 1000)
            binding.tvUptime.text = SimpleDateFormat("HH:mm:ss", Locale.US).format(uptime)
        }

        viewModel.totalPackets.observe(viewLifecycleOwner) { total ->
            binding.tvPacketTotal.text = formatLong(total)
        }

        viewModel.sources.observe(viewLifecycleOwner) { sources ->
            binding.tvSourceCount.text = sources.count { it.running == 1 }.toString()
            populateSources(sources)
        }

        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            val wifi  = devices.count { KismetViewModel.isWifi(it.phyName) }
            val bt    = devices.count { KismetViewModel.isBluetooth(it.phyName) }
            val sdr   = devices.count { KismetViewModel.isSDR(it.phyName) }
            val other = devices.size - wifi - bt - sdr
            binding.tvWifiCount.text  = wifi.toString()
            binding.tvBtCount.text    = bt.toString()
            binding.tvSdrCount.text   = sdr.toString()
            binding.tvOtherCount.text = other.coerceAtLeast(0).toString()
        }
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

    private fun updateVpnBadge() {
        val cm = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val iface = cm.getLinkProperties(cm.activeNetwork)?.interfaceName ?: ""
        val isVpn = listOf("tun", "wg", "ipsec", "vpn", "ppp").any { iface.startsWith(it) }
        binding.tvStatus.text = if (isVpn) "ONLINE 🔒" else "ONLINE"
    }

    private fun goToDevices(filter: PhyFilter) {
        viewModel.setPhyFilter(filter)
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId =
            R.id.devicesFragment
    }

    private fun openSdrSheet() {
        val state = viewModel.connectionState.value
        val host = if (state is ConnectionState.Connected) state.host else ""
        val port = if (state is ConnectionState.Connected) state.port else 2501
        SdrLaunchSheet.newInstance(host, port, "").show(parentFragmentManager, "sdr_launch")
    }

    private fun populateSources(sources: List<DataSource>) {
        val container = binding.llSources
        container.removeAllViews()
        if (sources.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "No sources"
                setTextColor(Color.parseColor("#546E7A"))
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            })
            return
        }
        val dp = resources.displayMetrics.density
        val connState = viewModel.connectionState.value
        val host = if (connState is ConnectionState.Connected) connState.host else ""
        val port = if (connState is ConnectionState.Connected) connState.port else 2501

        sources.forEach { src ->
            val srcName = src.name ?: ""
            val srcType = src.type ?: ""
            val isUbertooth = srcType.contains("ubertooth", ignoreCase = true)
            val isHackRF    = srcType.contains("hackrf",    ignoreCase = true) ||
                              srcName.contains("hackrf",    ignoreCase = true)
            val isRtl       = srcType.contains("rtl",       ignoreCase = true)
            val isSdr       = isUbertooth || isHackRF || isRtl ||
                              srcType.contains("sdr",       ignoreCase = true)
            val isRunning   = src.running == 1 && src.paused == 0 && src.error == 0
            val isPaused    = src.paused == 1
            val isError     = src.error == 1
            val isActiveIface = InterfaceGuard.isActiveInterface(requireContext(), src)

            val accentColor = when {
                isSdr  -> "#00BFA5"
                srcType.contains("bluetooth", ignoreCase = true) -> "#29B6F6"
                else   -> "#CFD8DC"
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                isClickable = true
                isFocusable = true
                isLongClickable = true
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1A546E7A")),
                    null, null
                )
                setOnClickListener {
                    if (src.uuid.isNotEmpty())
                        SourceControlSheet.newInstance(src.uuid)
                            .show(parentFragmentManager, "src_ctrl")
                    else
                        SdrLaunchSheet.newInstance(host, port, srcName.ifEmpty { srcType })
                            .show(parentFragmentManager, "sdr_launch")
                }
                setOnLongClickListener {
                    showSourceActions(src, host, port)
                    true
                }
            }

            val dot = TextView(requireContext()).apply {
                text = "●"
                textSize = 10f
                setTextColor(when {
                    isError   -> Color.parseColor("#EF5350")
                    isPaused  -> Color.parseColor("#FFA726")
                    isRunning -> Color.parseColor(if (isSdr) "#00BFA5" else "#39FF14")
                    else      -> Color.parseColor("#546E7A")
                })
                setPadding(0, 0, (8 * dp).toInt(), 0)
            }

            val label = TextView(requireContext()).apply {
                val displayName = srcName.ifEmpty { srcType }
                text = when {
                    isActiveIface -> "$displayName 🔗"
                    isUbertooth   -> "$displayName  ▶ UBERTOOTH"
                    isHackRF      -> "$displayName  ▶ HACKRF"
                    isRtl         -> "$displayName  ▶ RTL-SDR"
                    else          -> displayName
                }
                setTextColor(Color.parseColor(accentColor))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val pkts = TextView(requireContext()).apply {
                text = formatLong(src.packets)
                setTextColor(Color.parseColor("#546E7A"))
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            }

            row.addView(dot)
            row.addView(label)
            row.addView(pkts)
            container.addView(row)
        }

        // ADD SOURCE button
        val addBtn = TextView(requireContext()).apply {
            text = "+ ADD SOURCE"
            setTextColor(Color.parseColor("#546E7A"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, (10 * dp).toInt(), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { showAddSourceDialog() }
        }
        container.addView(addBtn)
    }

    private fun showAddSourceDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "e.g. ubertooth-0, wlan0, hci0"
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#CFD8DC"))
            setHintTextColor(Color.parseColor("#546E7A"))
            setPadding(40, 24, 40, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("ADD SOURCE")
            .setView(input)
            .setPositiveButton("ADD") { _, _ ->
                val def = input.text.toString().trim()
                if (def.isNotEmpty()) {
                    viewModel.addSource(def) { _, msg -> snack(msg) }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showSourceActions(src: DataSource, host: String, port: Int) {
        val guard = InterfaceGuard.check(requireContext(), src, host)
        AlertDialog.Builder(requireContext())
            .setTitle((src.name ?: "").ifEmpty { src.type ?: "" }.ifEmpty { "Source" })
            .setItems(arrayOf("Set Hopping", "Open SDR Sheet", "Export PCAP")) { _, which ->
                when (which) {
                    0 -> if (guard is GuardResult.Warning) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Interface Warning")
                                .setMessage(guard.reason)
                                .setPositiveButton("PROCEED") { _, _ -> doSetHop(src) }
                                .setNegativeButton("CANCEL", null)
                                .show()
                        } else {
                            doSetHop(src)
                        }
                    1 -> SdrLaunchSheet.newInstance(host, port, (src.name ?: "").ifEmpty { src.type ?: "" })
                            .show(parentFragmentManager, "sdr_launch")
                    2 -> startPcapCapture(uuid = src.uuid.ifEmpty { null })
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun doSetHop(src: DataSource) {
        if (src.uuid.isEmpty()) {
            snack("Source has no UUID")
            return
        }
        viewModel.setSourceHop(src.uuid) { _, msg -> snack(msg) }
    }

    private fun startPcapCapture(uuid: String? = null, deviceKey: String? = null) {
        val state = viewModel.connectionState.value
        val intent = Intent(requireContext(), PcapCaptureService::class.java).apply {
            uuid?.let { putExtra(PcapCaptureService.EXTRA_UUID, it) }
            deviceKey?.let { putExtra(PcapCaptureService.EXTRA_DEVICE_KEY, it) }
            if (state is ConnectionState.Connected) {
                putExtra(PcapCaptureService.EXTRA_HOST, state.host)
                putExtra(PcapCaptureService.EXTRA_PORT, state.port)
                putExtra(PcapCaptureService.EXTRA_USER, state.username)
                putExtra(PcapCaptureService.EXTRA_PASS, state.password)
            }
        }
        try {
            requireContext().startForegroundService(intent)
            snack("Capture started — check notification to stop", Snackbar.LENGTH_LONG)
        } catch (e: Exception) {
            snack("Cannot start capture: ${e.message ?: "Failed"}", Snackbar.LENGTH_LONG)
        }
    }

    private fun snack(msg: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val b = _binding ?: return
        if (!isAdded) return
        Snackbar.make(b.root, msg, duration).show()
    }

    private fun formatLong(value: Long): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000     -> "%.1fK".format(value / 1_000.0)
        else               -> value.toString()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
