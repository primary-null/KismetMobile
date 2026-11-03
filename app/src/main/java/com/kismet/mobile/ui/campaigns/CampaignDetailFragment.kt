package com.kismet.mobile.ui.campaigns

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.window.layout.FoldingFeature
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentCampaignDetailBinding
import com.kismet.mobile.model.AlertAction
import com.kismet.mobile.model.AlertRule
import com.kismet.mobile.model.AlertTrigger
import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.CampaignType
import com.kismet.mobile.ui.devices.DeviceAdapter
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class CampaignDetailFragment : Fragment() {

    private var _binding: FragmentCampaignDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private var campaignId = ""
    private var systemBarTop = 0
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCampaignDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTop = bars.top
            v.setPadding(0, bars.top, 0, v.paddingBottom)
            insets
        }
        windowViewModel.foldingFeature.observe(viewLifecycleOwner) { applyFoldPadding(it) }

        campaignId = arguments?.getString("campaignId") ?: return
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        val adapter = DeviceAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.root.findViewById<EditText>(R.id.et_search_devices)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                updateDeviceList(adapter)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnStart.setOnClickListener { viewModel.setCampaignStatus(campaignId, CampaignStatus.ACTIVE) }
        binding.btnPause.setOnClickListener { viewModel.setCampaignStatus(campaignId, CampaignStatus.PAUSED) }
        binding.btnEnd.setOnClickListener   { viewModel.setCampaignStatus(campaignId, CampaignStatus.COMPLETE) }
        binding.root.findViewById<View>(R.id.btn_wigle)?.setOnClickListener { uploadToWigle() }

        viewModel.campaigns.observe(viewLifecycleOwner) { _ ->
            val c = currentCampaign() ?: return@observe
            updateHeader(c)
            updateConfigSections(c)
            updateDeviceList(adapter)
        }
        viewModel.devices.observe(viewLifecycleOwner) { updateDeviceList(adapter) }
    }

    private fun currentCampaign() = viewModel.campaigns.value?.find { it.id == campaignId }

    // --- Device list ---

    private fun updateDeviceList(adapter: DeviceAdapter) {
        val all = viewModel.campaignDevices(campaignId)
        val filtered = if (searchQuery.isEmpty()) all else all.filter { d ->
            d.displayName.lowercase().contains(searchQuery) ||
            d.mac.lowercase().contains(searchQuery) ||
            d.manufacturer.lowercase().contains(searchQuery)
        }
        binding.tvCount.text = if (searchQuery.isEmpty()) "${all.size} tracked" else "${filtered.size}/${all.size}"
        binding.tvEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(filtered)
    }

    // --- Header ---

    private fun updateHeader(c: Campaign) {
        binding.tvTitle.text = c.name.uppercase()
        binding.tvCampaignType.text = c.type.name

        val (statusText, statusColor) = when (c.status) {
            CampaignStatus.ACTIVE   -> "● ACTIVE"   to Color.parseColor("#39FF14")
            CampaignStatus.PAUSED   -> "⏸ PAUSED"  to Color.parseColor("#FFA726")
            CampaignStatus.COMPLETE -> "✓ COMPLETE" to Color.parseColor("#42A5F5")
            else                    -> "○ DRAFT"    to Color.parseColor("#546E7A")
        }
        binding.tvCampaignStatus.text = statusText
        binding.tvCampaignStatus.setTextColor(statusColor)

        binding.btnStart.isEnabled = c.status != CampaignStatus.ACTIVE && c.status != CampaignStatus.COMPLETE
        binding.btnPause.isEnabled = c.status == CampaignStatus.ACTIVE
        binding.btnEnd.isEnabled   = c.status != CampaignStatus.COMPLETE

        binding.tvAlertsSummary.text = if (c.firedAlerts.isEmpty()) "No alerts fired"
        else c.firedAlerts.takeLast(5).joinToString("\n") { "⚡ ${it.message}" }

        val fmt = SimpleDateFormat("MMM dd HH:mm:ss", Locale.US)
        val parts = mutableListOf<String>()
        c.startedAt?.let { parts.add("started ${fmt.format(Date(it))}") }
        c.endedAt?.let   { parts.add("ended ${fmt.format(Date(it))}") }
        val tsView = binding.root.findViewById<TextView>(R.id.tv_timestamps)
        if (parts.isNotEmpty()) {
            tsView?.text = parts.joinToString("  ·  ")
            tsView?.visibility = View.VISIBLE
        } else {
            tsView?.visibility = View.GONE
        }
    }

    // --- Config sections ---

    private fun updateConfigSections(c: Campaign) {
        setupNotes(c)
        binding.root.findViewById<View>(R.id.section_hunt)?.visibility =
            if (c.type == CampaignType.HUNT) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.section_scope)?.visibility =
            if (c.type == CampaignType.SURVEY || c.type == CampaignType.WARDRIVING) View.VISIBLE else View.GONE
        binding.root.findViewById<View>(R.id.section_rules)?.visibility =
            if (c.type == CampaignType.ALERT) View.VISIBLE else View.GONE

        when (c.type) {
            CampaignType.HUNT                              -> setupHuntSection(c)
            CampaignType.SURVEY, CampaignType.WARDRIVING  -> setupScopeSection(c)
            CampaignType.ALERT                             -> setupRulesSection(c)
        }
    }

    private fun setupNotes(c: Campaign) {
        val et = binding.root.findViewById<EditText>(R.id.et_notes) ?: return
        if (et.tag != "loaded") {
            et.setText(c.notes)
            et.tag = "loaded"
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val cur = currentCampaign() ?: return@setOnFocusChangeListener
                    val newNotes = et.text.toString()
                    if (newNotes != cur.notes) viewModel.updateCampaign(cur.copy(notes = newNotes))
                }
            }
        }
    }

    private fun setupHuntSection(c: Campaign) {
        val et = binding.root.findViewById<EditText>(R.id.et_hunt_targets) ?: return
        if (et.tag != "loaded") {
            et.setText((c.targetMacs + c.targetSsids).joinToString("\n"))
            et.tag = "loaded"
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveHuntTargets(et)
            }
        }
    }

    private fun saveHuntTargets(et: EditText) {
        val cur = currentCampaign() ?: return
        val lines = et.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val macPattern = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
        val macs  = lines.filter { it.matches(macPattern) }
        val ssids = lines - macs.toSet()
        viewModel.updateCampaign(cur.copy(targetMacs = macs, targetSsids = ssids))
    }

    private fun setupScopeSection(c: Campaign) {
        val chipAll  = binding.root.findViewById<TextView>(R.id.chip_scope_all)  ?: return
        val chipWifi = binding.root.findViewById<TextView>(R.id.chip_scope_wifi) ?: return
        val chipBt   = binding.root.findViewById<TextView>(R.id.chip_scope_bt)   ?: return

        fun select(scope: String) {
            chipAll.isSelected  = scope == "ALL"
            chipWifi.isSelected = scope == "WIFI"
            chipBt.isSelected   = scope == "BT"
        }
        select(c.surveyScope)

        if (chipAll.tag != "wired") {
            chipAll.tag = "wired"
            chipAll.setOnClickListener  { saveScope("ALL");  select("ALL") }
            chipWifi.setOnClickListener { saveScope("WIFI"); select("WIFI") }
            chipBt.setOnClickListener   { saveScope("BT");   select("BT") }
        }
    }

    private fun saveScope(scope: String) {
        val cur = currentCampaign() ?: return
        if (cur.surveyScope != scope) viewModel.updateCampaign(cur.copy(surveyScope = scope))
    }

    private fun setupRulesSection(c: Campaign) {
        val tv  = binding.root.findViewById<TextView>(R.id.tv_rules_list) ?: return
        val btn = binding.root.findViewById<View>(R.id.btn_add_rule) ?: return

        tv.text = if (c.alertRules.isEmpty()) "No rules. Tap + ADD to create one."
        else c.alertRules.joinToString("\n") { rule ->
            val v = if (rule.value.isNotEmpty()) " = ${rule.value}" else ""
            "▸ ${rule.trigger.name}$v  [${rule.action.name}]"
        }

        if (btn.tag != "wired") {
            btn.tag = "wired"
            btn.setOnClickListener { currentCampaign()?.let { showAddRuleDialog(it) } }
        }
    }

    private fun showAddRuleDialog(c: Campaign) {
        val triggers = AlertTrigger.values()
        val labels = triggers.map { t ->
            when (t) {
                AlertTrigger.NEW_DEVICE       -> "NEW_DEVICE — any new device"
                AlertTrigger.MAC_SEEN         -> "MAC_SEEN — specific MAC"
                AlertTrigger.SSID_SEEN        -> "SSID_SEEN — SSID contains text"
                AlertTrigger.SIGNAL_THRESHOLD -> "SIGNAL — min dBm (e.g. -50)"
                AlertTrigger.CLIENT_COUNT     -> "CLIENT_COUNT — min client count"
            }
        }.toTypedArray()

        var selectedIdx = 0
        val valueInput = EditText(requireContext()).apply {
            hint = "value (leave blank for NEW_DEVICE)"
            setPadding(48, 24, 48, 16)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Alert Rule")
            .setSingleChoiceItems(labels, 0) { _, which ->
                selectedIdx = which
                valueInput.hint = when (triggers[which]) {
                    AlertTrigger.NEW_DEVICE       -> "(no value needed)"
                    AlertTrigger.MAC_SEEN         -> "AA:BB:CC:DD:EE:FF"
                    AlertTrigger.SSID_SEEN        -> "SSID substring"
                    AlertTrigger.SIGNAL_THRESHOLD -> "-50"
                    AlertTrigger.CLIENT_COUNT     -> "5"
                }
            }
            .setView(valueInput)
            .setPositiveButton("ADD") { _, _ ->
                val rule = AlertRule(
                    trigger = triggers[selectedIdx],
                    value   = valueInput.text.toString().trim(),
                    action  = AlertAction.NOTIFY
                )
                val cur = currentCampaign() ?: return@setPositiveButton
                viewModel.updateCampaign(cur.copy(alertRules = cur.alertRules + rule))
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // --- WiGLE ---

    private fun uploadToWigle() {
        val prefs = com.kismet.mobile.storage.AppPrefs(requireContext())
        val profile = prefs.getWigleProfile()
        if (profile.apiName.isBlank() || profile.apiToken.isBlank()) {
            com.google.android.material.snackbar.Snackbar.make(binding.root, "Configure WiGLE in Settings first", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            return
        }
        val devices = viewModel.campaignDevices(campaignId)
        if (devices.isEmpty()) {
            com.google.android.material.snackbar.Snackbar.make(binding.root, "No devices to upload", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }
        com.google.android.material.snackbar.Snackbar.make(binding.root, "Uploading to WiGLE...", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val csv = com.kismet.mobile.export.WigleExporter.toCsv(devices)
                val tempFile = java.io.File(requireContext().cacheDir, "wigle_export.csv")
                tempFile.writeText(csv)
                val mediaType = "text/csv".toMediaTypeOrNull()
                val reqFile = tempFile.asRequestBody(mediaType)
                val body = okhttp3.MultipartBody.Part.createFormData("file", tempFile.name, reqFile)
                val api = com.kismet.mobile.api.WigleClient.build(profile.apiName, profile.apiToken)
                val response = api.upload(body)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true)
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "WiGLE Upload Successful", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    else
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "WiGLE Upload Failed: ${response.body()?.message ?: response.code()}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.google.android.material.snackbar.Snackbar.make(binding.root, "Error: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                }
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
