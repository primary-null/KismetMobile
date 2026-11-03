package com.kismet.mobile.ui.campaigns

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kismet.mobile.databinding.ItemCampaignBinding
import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.CampaignType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CampaignAdapter(
    private val onOpen: (Campaign) -> Unit,
    private val onDelete: (Campaign) -> Unit,
    private val onRename: (Campaign) -> Unit = {},
) : ListAdapter<Campaign, CampaignAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemCampaignBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Campaign) {
            b.tvName.text = c.name
            b.tvDeviceCount.text = "${c.deviceKeys.size} devices"
            b.tvAlertsCount.text = if (c.firedAlerts.isEmpty()) "" else "⚡ ${c.firedAlerts.size}"
            b.tvCreated.text = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(c.createdAt))

            // Type badge
            val (typeLabel, typeColor) = when (c.type) {
                CampaignType.SURVEY    -> "SURVEY"    to Color.parseColor("#546E7A")
                CampaignType.HUNT      -> "HUNT"      to Color.parseColor("#EF5350")
                CampaignType.ALERT     -> "ALERT"     to Color.parseColor("#FFA726")
                CampaignType.WARDRIVING -> "WARDRIVE" to Color.parseColor("#7E57C2")
            }
            b.tvTypeBadge.text = typeLabel
            b.tvTypeBadge.setTextColor(typeColor)

            // Status chip
            val (statusLabel, statusColor) = when (c.status) {
                CampaignStatus.ACTIVE   -> "● ACTIVE"   to Color.parseColor("#39FF14")
                CampaignStatus.PAUSED   -> "⏸ PAUSED"  to Color.parseColor("#FFA726")
                CampaignStatus.COMPLETE -> "✓ DONE"     to Color.parseColor("#42A5F5")
                CampaignStatus.DRAFT    -> "○ DRAFT"    to Color.parseColor("#546E7A")
            }
            b.tvStatusChip.text = statusLabel
            b.tvStatusChip.setTextColor(statusColor)

            b.root.setOnClickListener { onOpen(c) }
            b.root.setOnLongClickListener { onRename(c); true }
            b.btnDelete.setOnClickListener { onDelete(c) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCampaignBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Campaign>() {
            override fun areItemsTheSame(a: Campaign, b: Campaign) = a.id == b.id
            override fun areContentsTheSame(a: Campaign, b: Campaign) = a == b
        }
    }
}
