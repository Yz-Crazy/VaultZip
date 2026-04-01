package com.vaultzip.ui.archive

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vaultzip.R
import com.vaultzip.archive.model.ArchiveEntry
import com.vaultzip.ui.main.MainViewModel
import kotlinx.coroutines.launch

class ArchiveBrowserFragment : Fragment(R.layout.fragment_archive_browser) {

    private val mainViewModel by activityViewModels<MainViewModel>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvArchiveSummary: TextView

    private val adapter = ArchiveEntryAdapter(
        onPreviewClick = { entry -> onPreviewClicked(entry) },
        onExtractClick = { entry -> onExtractClicked(entry) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewEntries)
        tvArchiveSummary = view.findViewById(R.id.tvArchiveSummary)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collect { state ->
                    adapter.submitList(state.entries)
                    tvArchiveSummary.text = "共 ${state.entries.size} 项，可预览文件或单独解出"
                }
            }
        }
    }

    private fun onPreviewClicked(entry: ArchiveEntry) {
        if (entry.isDirectory) {
            Toast.makeText(requireContext(), "目录暂不支持直接预览", Toast.LENGTH_SHORT).show()
            return
        }
        mainViewModel.previewEntry(entry)
    }

    private fun onExtractClicked(entry: ArchiveEntry) {
        if (entry.isDirectory) {
            Toast.makeText(requireContext(), "目录暂不支持单独解出", Toast.LENGTH_SHORT).show()
            return
        }
        mainViewModel.extractEntry(entry)
    }

    companion object {
        const val TAG = "ArchiveBrowserFragment"
    }
}
