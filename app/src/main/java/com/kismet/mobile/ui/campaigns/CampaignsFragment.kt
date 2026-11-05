package com.kismet.mobile.ui.campaigns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import androidx.navigation.fragment.findNavController
import com.kismet.mobile.ui.navigateIfPresent
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentCampaignsBinding
import com.kismet.mobile.model.CampaignType
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel

class CampaignsFragment : Fragment() {

    private var _binding: FragmentCampaignsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private lateinit var adapter: CampaignAdapter
    private var systemBarTop = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCampaignsBinding.inflate(inflater, container, false)
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

        adapter = CampaignAdapter(
            onOpen = { campaign ->
                findNavController().navigateIfPresent(
                    R.id.action_campaigns_to_campaign_detail,
                    Bundle().apply {
                        putString("campaignId", campaign.id)
                        putString("campaignName", campaign.name)
                    }
                )
            },
            onDelete = { campaign -> viewModel.deleteCampaign(campaign.id) },
            onRename = { campaign -> showRenameDialog(campaign) }
        )
        binding.rvCampaigns.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCampaigns.adapter = adapter

        binding.fabNew.setOnClickListener { showTypePicker() }

        viewModel.campaigns.observe(viewLifecycleOwner) { campaigns ->
            binding.tvEmpty.visibility = if (campaigns.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(campaigns)

            try {
                binding.root.findViewById<android.widget.TextView>(com.kismet.mobile.R.id.tv_stats_campaigns)?.text = "${campaigns.size}"
                val activeCount = campaigns.count { it.status == com.kismet.mobile.model.CampaignStatus.ACTIVE }
                binding.root.findViewById<android.widget.TextView>(com.kismet.mobile.R.id.tv_stats_active)?.text = "$activeCount"
            } catch (_: Exception) {}
        }
    }

    private fun showRenameDialog(campaign: com.kismet.mobile.model.Campaign) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Campaign name"
            setPadding(48, 32, 48, 16)
            setText(campaign.name)
            setSelection(campaign.name.length)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("RENAME") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { campaign.name }
                viewModel.updateCampaign(campaign.copy(name = name))
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showTypePicker() {
        val types = listOf(
            Triple(CampaignType.SURVEY,     "Survey",     "Passively collect all devices seen while active"),
            Triple(CampaignType.HUNT,       "Hunt",       "Alert when a specific MAC or SSID appears"),
            Triple(CampaignType.WARDRIVING, "Wardriving", "Survey + GPS location tagging + WiGLE export"),
        )
        val labels = types.map { "${it.second} — ${it.third}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Campaign Type")
            .setItems(labels) { _, which ->
                val (type, name, desc) = types[which]
                showNameDialog(type, name, desc)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showNameDialog(type: CampaignType, prefill: String, description: String) {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Campaign name"
            setPadding(48, 32, 48, 16)
            setText(prefill)
            setSelection(prefill.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New ${type.name.lowercase().replaceFirstChar { it.uppercase() }}")
            .setMessage(description)
            .setView(input)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { prefill }
                viewModel.createCampaign(name, type, description)
            }
            .setNegativeButton("CANCEL", null)
            .show()
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
