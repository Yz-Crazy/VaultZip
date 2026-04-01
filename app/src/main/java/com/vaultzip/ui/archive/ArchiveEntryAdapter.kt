package com.vaultzip.ui.archive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vaultzip.R
import com.vaultzip.archive.model.ArchiveEntry

class ArchiveEntryAdapter(
    private val onPreviewClick: (ArchiveEntry) -> Unit,
    private val onExtractClick: (ArchiveEntry) -> Unit
) : ListAdapter<ArchiveEntry, ArchiveEntryAdapter.EntryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archive_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
        private val btnPreview: Button = itemView.findViewById(R.id.btnPreview)
        private val btnExtract: Button = itemView.findViewById(R.id.btnExtract)

        fun bind(item: ArchiveEntry) {
            tvName.text = item.name
            tvMeta.text = buildString {
                append(if (item.isDirectory) "目录" else "文件")
                append(" · ")
                append("大小 ")
                append(item.uncompressedSize ?: 0)
                append(" · ")
                append(if (item.encrypted) "已加密" else "未加密")
            }

            btnPreview.isEnabled = !item.isDirectory
            btnExtract.isEnabled = !item.isDirectory
            btnPreview.setOnClickListener { onPreviewClick(item) }
            btnExtract.setOnClickListener { onExtractClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ArchiveEntry>() {
        override fun areItemsTheSame(oldItem: ArchiveEntry, newItem: ArchiveEntry): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ArchiveEntry, newItem: ArchiveEntry): Boolean {
            return oldItem == newItem
        }
    }
}
