package com.balsdon.managemom

import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.balsdon.managemom.databinding.ItemAppBinding

class AppListAdapter(
    private val onDeleteClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAppBinding,
        private val onDeleteClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppInfo) {
            binding.appName.text = item.appName
            binding.packageName.text = item.packageName
            val syncColor = ContextCompat.getColor(
                binding.root.context,
                if (item.isSynced) R.color.sync_synced else R.color.sync_not_synced
            )
            binding.syncIcon.setColorFilter(syncColor)
            binding.deleteIcon.visibility = if (item.canUninstall) View.VISIBLE else View.GONE
            binding.deleteIcon.setOnClickListener { if (item.canUninstall) onDeleteClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(old: AppInfo, new: AppInfo) = old.packageName == new.packageName
        override fun areContentsTheSame(old: AppInfo, new: AppInfo) = old == new
    }
}
