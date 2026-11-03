package com.kismet.mobile.ui.connect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kismet.mobile.databinding.ItemProfileBinding
import com.kismet.mobile.model.ConnectionProfile

class ProfileAdapter(
    private val onSelect: (ConnectionProfile) -> Unit,
    private val onDelete: (ConnectionProfile) -> Unit,
) : ListAdapter<ConnectionProfile, ProfileAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemProfileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: ConnectionProfile) {
            b.tvName.text = p.name
            b.tvAddress.text = "${p.host}:${p.port}"
            b.root.setOnClickListener { onSelect(p) }
            b.btnDelete.setOnClickListener { onDelete(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ConnectionProfile>() {
            override fun areItemsTheSame(a: ConnectionProfile, b: ConnectionProfile) = a.id == b.id
            override fun areContentsTheSame(a: ConnectionProfile, b: ConnectionProfile) = a == b
        }
    }
}
