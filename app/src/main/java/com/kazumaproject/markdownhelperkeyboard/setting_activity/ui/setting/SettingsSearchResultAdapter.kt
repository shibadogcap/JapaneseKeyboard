package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R

class SettingsSearchResultAdapter(
    private val onEntrySelected: (SettingsPreferenceIndex.Entry) -> Unit,
) : ListAdapter<SettingsPreferenceIndex.Entry, SettingsSearchResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_settings_search_result, parent, false)
        return ViewHolder(view, onEntrySelected)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onEntrySelected: (SettingsPreferenceIndex.Entry) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.settings_search_result_title)
        private val subtitleView: TextView = itemView.findViewById(R.id.settings_search_result_subtitle)

        fun bind(entry: SettingsPreferenceIndex.Entry) {
            titleView.text = entry.title
            val subtitle = buildString {
                append(entry.tabTitle)
                entry.categoryTitle?.let { category ->
                    append(" › ")
                    append(category)
                }
                entry.summary?.let { summary ->
                    append("\n")
                    append(summary)
                }
            }
            subtitleView.text = subtitle
            subtitleView.isVisible = subtitle.isNotBlank()
            itemView.setOnClickListener { onEntrySelected(entry) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SettingsPreferenceIndex.Entry>() {
            override fun areItemsTheSame(
                oldItem: SettingsPreferenceIndex.Entry,
                newItem: SettingsPreferenceIndex.Entry,
            ): Boolean {
                return oldItem.tabIndex == newItem.tabIndex &&
                    oldItem.preferenceKey == newItem.preferenceKey &&
                    oldItem.title == newItem.title
            }

            override fun areContentsTheSame(
                oldItem: SettingsPreferenceIndex.Entry,
                newItem: SettingsPreferenceIndex.Entry,
            ): Boolean = oldItem == newItem
        }
    }
}
