package com.kismet.mobile.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.kismet.mobile.databinding.SheetSourceControlBinding
import com.kismet.mobile.service.PcapCaptureService
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel

class SourceControlSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSourceControlBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSourceControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uuid = arguments?.getString(ARG_UUID) ?: return

        viewModel.sources.observe(viewLifecycleOwner) { sources ->
            val src = sources.firstOrNull { it.uuid == uuid } ?: return@observe
            val isRunning = src.running == 1 && src.paused == 0 && src.error == 0
            val isPaused  = src.paused == 1
            val isError   = src.error == 1

            val srcName = src.name ?: ""
            val srcType = src.type ?: ""
            binding.tvSrcName.text = srcName.ifEmpty { srcType }.ifEmpty { src.interfaceName ?: "" }

            val (badgeLabel, badgeColor) = when {
                srcType.contains("ubertooth", true)   -> "UBERTOOTH" to "#00BFA5"
                srcType.contains("hackrf",    true)   -> "HACKRF"    to "#00BFA5"
                srcType.contains("rtl",       true)   -> "RTL-SDR"   to "#00BFA5"
                srcType.contains("wifi",      true)   -> "WIFI"      to "#39FF14"
                srcType.contains("bluetooth", true)   -> "BT"        to "#29B6F6"
                else                                  -> srcType.uppercase() to "#546E7A"
            }
            binding.tvSrcTypeBadge.text = badgeLabel
            binding.tvSrcTypeBadge.setTextColor(Color.parseColor(badgeColor))

            val ifaceText = buildString {
                append((src.interfaceName ?: "").ifEmpty { "—" })
                val hw = src.hardware ?: ""
                if (hw.isNotEmpty()) append("  ·  $hw")
            }
            binding.tvSrcIface.text = ifaceText

            val (dotColor, statusText) = when {
                isError   -> "#EF5350" to "ERROR"
                isRunning -> "#39FF14" to "RUNNING"
                isPaused  -> "#546E7A" to "PAUSED"
                else      -> "#546E7A" to "STOPPED"
            }
            binding.tvSrcStatusDot.setTextColor(Color.parseColor(dotColor))
            binding.tvSrcStatus.text = statusText

            val pkts = src.packets
            binding.tvSrcPackets.text = when {
                pkts >= 1_000_000 -> "%.1fM pkts".format(pkts / 1_000_000.0)
                pkts >= 1_000     -> "%.1fK pkts".format(pkts / 1_000.0)
                else              -> "$pkts pkts"
            }

            binding.btnPauseResume.text = if (isRunning) "PAUSE" else "RESUME"
            binding.btnPauseResume.setOnClickListener {
                if (isRunning) {
                    viewModel.pauseSource(uuid) { _, msg ->
                        val b = _binding ?: return@pauseSource
                        if (!isAdded) return@pauseSource
                        Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.resumeSource(uuid) { _, msg ->
                        val b = _binding ?: return@resumeSource
                        if (!isAdded) return@resumeSource
                        Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnPcap.setOnClickListener {
            val state = viewModel.connectionState.value
            val intent = Intent(requireContext(), PcapCaptureService::class.java).apply {
                putExtra(PcapCaptureService.EXTRA_UUID, uuid)
                if (state is ConnectionState.Connected) {
                    putExtra(PcapCaptureService.EXTRA_HOST, state.host)
                    putExtra(PcapCaptureService.EXTRA_PORT, state.port)
                    putExtra(PcapCaptureService.EXTRA_USER, state.username)
                    putExtra(PcapCaptureService.EXTRA_PASS, state.password)
                }
            }
            try {
                requireContext().startForegroundService(intent)
                Snackbar.make(binding.root, "Capture started", Snackbar.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                val b = _binding ?: return@setOnClickListener
                Snackbar.make(b.root, "Cannot start capture: ${e.message ?: "Failed"}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_UUID = "uuid"

        fun newInstance(uuid: String) = SourceControlSheet().apply {
            arguments = Bundle().apply { putString(ARG_UUID, uuid) }
        }
    }
}
