package com.kismet.mobile.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kismet.mobile.ui.navigateIfPresent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentDevicesBinding
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.PhyFilter
import com.kismet.mobile.viewmodel.WindowLayoutViewModel

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private lateinit var adapter: DeviceAdapter
    private var systemBarTop = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
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

        adapter = DeviceAdapter { device ->
            findNavController().navigateIfPresent(
                R.id.action_devices_to_detail,
                bundleOf("deviceKey" to device.key)
            )
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setFilter(newText ?: "")
                refreshList()
                return true
            }
        })

        binding.chipAll.setOnClickListener { viewModel.setPhyFilter(PhyFilter.ALL) }
        binding.chipWifi.setOnClickListener { viewModel.setPhyFilter(PhyFilter.WIFI) }
        binding.chipBt.setOnClickListener { viewModel.setPhyFilter(PhyFilter.BT) }
        binding.chipSdr.setOnClickListener { viewModel.setPhyFilter(PhyFilter.SDR) }
        binding.chipOther.setOnClickListener { viewModel.setPhyFilter(PhyFilter.OTHER) }

        try {
            val sw = binding.root.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_campaign)
            sw?.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setCampaignOnlyFilter(isChecked)
                refreshList()
            }
            viewModel.campaignOnlyFilter.observe(viewLifecycleOwner) { isChecked ->
                sw?.isChecked = isChecked
            }
        } catch (_: Exception) {}

        viewModel.phyFilter.observe(viewLifecycleOwner) { filter ->
            binding.chipAll.isSelected = filter == PhyFilter.ALL
            binding.chipWifi.isSelected = filter == PhyFilter.WIFI
            binding.chipBt.isSelected = filter == PhyFilter.BT
            binding.chipSdr.isSelected = filter == PhyFilter.SDR
            binding.chipOther.isSelected = filter == PhyFilter.OTHER
            updateLatchVisibility()
            refreshList()
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { updateLatchVisibility() }

        viewModel.devices.observe(viewLifecycleOwner) { refreshList() }
        viewModel.filter.observe(viewLifecycleOwner) { refreshList() }
        viewModel.localSightings.observe(viewLifecycleOwner) { adapter.localSightings = it ?: emptyMap() }
        viewModel.sources.observe(viewLifecycleOwner) { sources ->
            adapter.sourcesMap = sources.associateBy { it.uuid }
        }

        try {
            val btn = binding.root.findViewById<android.view.View>(R.id.btn_verify_proximity)
            btn?.setOnClickListener {
                val needed = mutableListOf<String>()
                if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        needed.add(android.Manifest.permission.BLUETOOTH_SCAN)
                    }
                    if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        needed.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                if (needed.isNotEmpty()) {
                    requestPermissions(needed.toTypedArray(), 101)
                } else {
                    promptAndScan()
                }
            }
        } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 101 && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            promptAndScan()
        }
    }

    private fun promptAndScan() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Verify Proximity")
            .setMessage("Ensure 'Wi-Fi scan throttling' is disabled in Developer Options for best results.")
            .setPositiveButton("SCAN") { _, _ ->
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Scanning local environment...", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch { viewModel.runLocalScan() }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun updateLatchVisibility() {
        val connected = viewModel.connectionState.value is ConnectionState.Connected
        val filter = viewModel.phyFilter.value ?: PhyFilter.ALL
        val relevant = filter == PhyFilter.ALL || filter == PhyFilter.WIFI || filter == PhyFilter.BT
        val btn = binding.root.findViewById<android.view.View>(R.id.btn_verify_proximity)
        btn?.visibility = if (connected && relevant) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun refreshList() {
        val filtered = viewModel.filteredDevices()
        binding.tvCount.text = "${filtered.size} devices"
        adapter.submitList(filtered)
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
