package com.vaultzip.ui.extract

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vaultzip.R
import com.vaultzip.ui.archive.ArchiveBrowserFragment
import com.vaultzip.ui.main.MainViewModel
import kotlinx.coroutines.launch

class ExtractFragment : Fragment(R.layout.fragment_extract) {

    private val viewModel by activityViewModels<MainViewModel>()

    private lateinit var btnPick: Button
    private lateinit var btnPickDirectory: Button
    private lateinit var btnDetect: Button
    private lateinit var btnList: Button
    private lateinit var btnExtract: Button
    private lateinit var btnClear: Button
    private lateinit var tvSelected: TextView
    private lateinit var tvFormat: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvError: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvContentHint: TextView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateContainer: View
    private lateinit var extractContentContainer: FrameLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        btnPick = view.findViewById(R.id.btnPickArchive)
        btnPickDirectory = view.findViewById(R.id.btnPickDirectory)
        btnDetect = view.findViewById(R.id.btnDetectFormat)
        btnList = view.findViewById(R.id.btnListEntries)
        btnExtract = view.findViewById(R.id.btnExtractAll)
        btnClear = view.findViewById(R.id.btnClear)
        tvSelected = view.findViewById(R.id.tvSelected)
        tvFormat = view.findViewById(R.id.tvFormat)
        tvLog = view.findViewById(R.id.tvLog)
        tvError = view.findViewById(R.id.tvError)
        tvProgress = view.findViewById(R.id.tvProgress)
        tvContentHint = view.findViewById(R.id.tvContentHint)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        extractContentContainer = view.findViewById(R.id.extractContentContainer)

        btnPick.setOnClickListener { (activity as? Host)?.onPickArchiveRequested() }
        btnPickDirectory.setOnClickListener { (activity as? Host)?.onPickDirectoryRequested() }
        btnDetect.setOnClickListener { viewModel.detectFormat() }
        btnList.setOnClickListener { viewModel.listEntries() }
        btnExtract.setOnClickListener { viewModel.extractAll() }
        btnClear.setOnClickListener { clearCurrentSelection() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    tvSelected.text = state.selectedName.ifBlank { "还没有选择压缩包" }
                    tvFormat.text = buildString {
                        append("格式：${state.detectedFormat?.name ?: "-"}")
                        state.volumeSet?.let {
                            append("  ·  分卷 ${it.parts.size} 卷")
                        }
                    }
                    tvLog.text = state.logMessage.ifBlank { "支持单个压缩包、分卷文件和分卷目录" }
                    tvError.text = state.errorMessage.orEmpty()
                    tvError.visibility = if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE

                    val showProgress = state.loading && (state.totalBytes != null || state.processedBytes > 0L || !state.currentEntryName.isNullOrBlank())
                    progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    progressBar.isIndeterminate = state.totalBytes == null || state.totalBytes <= 0L
                    if (!progressBar.isIndeterminate) {
                        progressBar.max = 1000
                        progressBar.progress = ((state.processedBytes * 1000L) / state.totalBytes!!).toInt().coerceIn(0, 1000)
                    }

                    tvProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
                    tvProgress.text = when {
                        !showProgress -> ""
                        state.totalBytes != null && state.totalBytes > 0L -> {
                            val percent = ((state.processedBytes * 100L) / state.totalBytes).coerceIn(0L, 100L)
                            "进度：${percent}%${state.currentEntryName?.let { " · $it" }.orEmpty()}"
                        }
                        else -> "处理中${state.currentEntryName?.let { " · $it" }.orEmpty()}"
                    }

                    updateContentSection(state.selectedName.isNotBlank(), state.entries.isNotEmpty())

                    val childFragment = childFragmentManager.findFragmentByTag(ArchiveBrowserFragment.TAG)
                    if (state.entries.isNotEmpty() && childFragment == null) {
                        childFragmentManager.commit {
                            replace(R.id.extractContentContainer, ArchiveBrowserFragment(), ArchiveBrowserFragment.TAG)
                        }
                    } else if (state.entries.isEmpty() && childFragment != null) {
                        childFragmentManager.commit { remove(childFragment) }
                    }
                }
            }
        }
    }

    private fun updateContentSection(hasSelection: Boolean, hasEntries: Boolean) {
        if (hasEntries) {
            emptyStateContainer.visibility = View.GONE
            extractContentContainer.visibility = View.VISIBLE
            tvContentHint.text = "已列出归档内容，可预览文件或单独解出"
            return
        }

        extractContentContainer.visibility = View.GONE
        emptyStateContainer.visibility = View.VISIBLE
        if (hasSelection) {
            tvEmptyTitle.text = "已选择归档文件"
            tvEmptyMessage.text = "可以先查看内容，也可以直接整包解压"
            tvContentHint.text = "还没有展示归档内容"
        } else {
            tvEmptyTitle.text = "还没有选择压缩包"
            tvEmptyMessage.text = "支持单个压缩包、分卷文件和分卷目录"
            tvContentHint.text = "列目录后可预览文件或单独解出"
        }
    }

    private fun clearCurrentSelection() {
        viewModel.clearSelection()
        childFragmentManager.findFragmentByTag(ArchiveBrowserFragment.TAG)?.let { fragment ->
            childFragmentManager.commit { remove(fragment) }
        }
    }

    interface Host {
        fun onPickArchiveRequested()
        fun onPickDirectoryRequested()
    }
}
