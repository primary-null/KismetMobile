package com.kismet.mobile.sdr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.kismet.mobile.databinding.FragmentSdrSpectrumBinding
import com.kismet.mobile.model.Channel
import com.kismet.mobile.viewmodel.KismetViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SdrSpectrumFragment : Fragment() {

    private var _binding: FragmentSdrSpectrumBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private var pollJob: Job? = null

    private enum class BandFilter { ALL, G2, G5, G6 }
    private var bandFilter = BandFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSdrSpectrumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
            insets
        }

        setupChips()

        binding.spectrumView.onChannelSelected = { band -> updateStatBar(band) }

        viewModel.channels.observe(viewLifecycleOwner) { data ->
            val filtered = filterBands(data.channels)
            val bands = filtered.map { ch ->
                SpectrumView.Band(label = ch.channelLabel, signalDbm = ch.signal, packets = ch.packets)
            }
            binding.spectrumView.update(bands)
            binding.tvLastUpdate.text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            if (!binding.spectrumView.hasSelection) {
                showPeakStats(filtered.maxByOrNull { it.signal }, filtered.size)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pollJob = lifecycleScope.launch {
            while (true) {
                viewModel.refreshChannels()
                delay(POLL_MS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Stats bar ──────────────────────────────────────────────────────────

    private fun updateStatBar(selected: SpectrumView.Band?) {
        if (selected != null) {
            binding.tvLabelStat1.text = "CHANNEL"
            binding.tvLabelStat2.text = "SIGNAL"
            binding.tvLabelStat3.text = "PACKETS"
            binding.tvPeakChannel.text = selected.label
            binding.tvPeakSignal.text = "${selected.signalDbm} dBm"
            binding.tvChannelCount.text = if (selected.packets > 0) selected.packets.toString() else "—"
        } else {
            val data = viewModel.channels.value
            val filtered = if (data != null) filterBands(data.channels) else emptyList()
            showPeakStats(filtered.maxByOrNull { it.signal }, filtered.size)
        }
    }

    private fun showPeakStats(peak: Channel?, count: Int) {
        binding.tvLabelStat1.text = "PEAK CHANNEL"
        binding.tvLabelStat2.text = "PEAK SIGNAL"
        binding.tvLabelStat3.text = "CHANNELS"
        binding.tvPeakChannel.text = peak?.channelLabel ?: "—"
        binding.tvPeakSignal.text = if (peak != null) "${peak.signal} dBm" else "—"
        binding.tvChannelCount.text = count.toString()
    }

    // ── Band filter ────────────────────────────────────────────────────────

    private fun setupChips() {
        val chips = mapOf(
            binding.chipAllBands to BandFilter.ALL,
            binding.chip2g       to BandFilter.G2,
            binding.chip5g       to BandFilter.G5,
            binding.chip6g       to BandFilter.G6,
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener {
                bandFilter = filter
                chips.keys.forEach { c -> c.isSelected = c == chip }
                viewModel.channels.value?.let { data ->
                    val filtered = filterBands(data.channels)
                    val bands = filtered.map { ch ->
                        SpectrumView.Band(ch.channelLabel, ch.signal, ch.packets)
                    }
                    binding.spectrumView.update(bands)
                    if (!binding.spectrumView.hasSelection) {
                        showPeakStats(filtered.maxByOrNull { it.signal }, filtered.size)
                    }
                }
            }
        }
        binding.chipAllBands.isSelected = true
    }

    private fun filterBands(channels: List<Channel>): List<Channel> {
        return channels.filter { ch ->
            val mhz = (ch.frequencyKhz / 1000).toInt()
            when (bandFilter) {
                BandFilter.ALL -> true
                BandFilter.G2  -> mhz in 2400..2500
                BandFilter.G5  -> mhz in 5000..5925
                BandFilter.G6  -> mhz in 5925..7125
            }
        }.filter { it.signal != 0 }
    }

    companion object {
        private const val POLL_MS = 2_000L
    }
}
