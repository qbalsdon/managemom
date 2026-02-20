package com.balsdon.managemom

import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.balsdon.managemom.databinding.ItemAppBinding
import com.balsdon.managemom.databinding.ItemSectionHeaderBinding

class AppListAdapter(
    private val onDeleteClick: (AppInfo) -> Unit,
    private val onBugClick: (AppInfo) -> Unit
) : ListAdapter<AppListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AppListItem.SectionHeader -> VIEW_TYPE_HEADER
            is AppListItem.App -> VIEW_TYPE_APP
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SectionHeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AppViewHolder(binding, onDeleteClick, onBugClick)
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AppListItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item.title)
            is AppListItem.App -> (holder as AppViewHolder).bind(item.appInfo)
        }
    }

    class SectionHeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionTitle.text = title
        }
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        private val onDeleteClick: (AppInfo) -> Unit,
        private val onBugClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppInfo) {
            try {
                binding.appIcon.setImageDrawable(
                    binding.root.context.packageManager.getApplicationIcon(item.packageName)
                )
            } catch (_: Exception) {
                binding.appIcon.setImageDrawable(null)
            }
            binding.appName.text = item.appName
            binding.packageName.text = "${item.packageName} / ${PackageHelper.getInstallSourceLabel(item.installerPackageName)}"
            binding.bugIcon.visibility = if (item.isMarkedForDeletion) View.VISIBLE else View.GONE
            val bugColor = ContextCompat.getColor(
                binding.root.context,
                if (item.isBug) R.color.bug_marked else R.color.bug_unmarked
            )
            binding.bugIcon.setColorFilter(bugColor)
            binding.bugIcon.setOnClickListener { onBugClick(item) }
            val syncColor = ContextCompat.getColor(
                binding.root.context,
                if (item.isSynced) R.color.sync_synced else R.color.sync_not_synced
            )
            binding.syncIcon.setColorFilter(syncColor)
            binding.deleteIcon.visibility = if (item.canUninstall) View.VISIBLE else View.GONE
            binding.deleteIcon.setOnClickListener { if (item.canUninstall) onDeleteClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppListItem>() {
        override fun areItemsTheSame(old: AppListItem, new: AppListItem): Boolean =
            when {
                old is AppListItem.SectionHeader && new is AppListItem.SectionHeader -> old.title == new.title
                old is AppListItem.App && new is AppListItem.App -> old.appInfo.packageName == new.appInfo.packageName
                else -> false
            }

        override fun areContentsTheSame(old: AppListItem, new: AppListItem): Boolean =
            old == new
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1
    }
}
