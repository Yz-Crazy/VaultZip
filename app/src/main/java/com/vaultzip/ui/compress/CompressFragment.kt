package com.vaultzip.ui.compress

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vaultzip.R
import kotlinx.coroutines.launch

class CompressFragment : Fragment(R.layout.fragment_compress) {

    private val viewModel by activityViewModels<CompressViewModel>()

    private lateinit var btnPickFiles: Button
    private lateinit var btnPickOutput: Button
    private lateinit var btnStartCompress: Button
    private lateinit var btnClear: Button
    private lateinit var tvSelectedFiles: TextView
    private lateinit var tvOutputFile: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        btnPickFiles = view.findViewById(R.id.btnPickFiles)
        btnPickOutput = view.findViewById(R.id.btnPickOutput)
        btnStartCompress = view.findViewById(R.id.btnStartCompress)
        btnClear = view.findViewById(R.id.btnClearCompress)
        tvSelectedFiles = view.findViewById(R.id.tvSelectedFiles)
        tvOutputFile = view.findViewById(R.id.tvOutputFile)
        tvStatus = view.findViewById(R.id.tvCompressStatus)
        tvError = view.findViewById(R.id.tvCompressError)
        tvProgress = view.findViewById(R.id.tvCompressProgress)
        progressBar = view.findViewById(R.id.progressBarCompress)

        btnPickFiles.setOnClickListener { (activity as? Host)?.onPickCompressFilesRequested() }
        btnPickOutput.setOnClickListener { (activity as? Host)?.onCreateArchiveRequested(viewModel.suggestedArchiveName()) }
        btnStartCompress.setOnClickListener { viewModel.createArchive() }
        btnClear.setOnClickListener { viewModel.clearSelection() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    tvSelectedFiles.text = buildSelectedFilesSummary(state)
                    tvOutputFile.text = state.outputDisplayName.ifBlank { "还没有选择输出位置" }
                    tvStatus.text = state.statusMessage.ifBlank { "选择文件后即可创建 ZIP 压缩包" }
                    tvError.text = state.errorMessage.orEmpty()
                    tvError.visibility = if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE

                    val hasKnownTotal = state.totalBytes != null && state.totalBytes > 0L
                    val showProgress = state.loading && (hasKnownTotal || state.processedBytes > 0L || !state.currentEntryName.isNullOrBlank())
                    progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    progressBar.isIndeterminate = !hasKnownTotal
                    if (hasKnownTotal) {
                        progressBar.max = 1000
                        progressBar.progress = ((state.processedBytes * 1000L) / state.totalBytes!!).toInt().coerceIn(0, 1000)
                    }

                    tvProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
                    tvProgress.text = when {
                        !showProgress -> ""
                        hasKnownTotal -> {
                            val percent = ((state.processedBytes * 100L) / state.totalBytes!!).coerceIn(0L, 100L)
                            "进度：${percent}%${state.currentEntryName?.let { " · $it" }.orEmpty()}"
                        }
                        else -> "正在压缩${state.currentEntryName?.let { " · $it" }.orEmpty()}"
                    }

                    btnStartCompress.isEnabled = !state.loading && state.selectedSources.isNotEmpty() && state.outputUri != null
                    btnPickFiles.isEnabled = !state.loading
                    btnPickOutput.isEnabled = !state.loading
                    btnClear.isEnabled = !state.loading && (state.selectedSources.isNotEmpty() || state.outputUri != null)
                }
            }
        }
    }

    private fun buildSelectedFilesSummary(state: CompressUiState): String {
        if (state.selectedSources.isEmpty()) return "还没有选择文件"
        val preview = state.selectedSources.take(3).joinToString("、") { it.displayName }
        val suffix = if (state.selectedSources.size > 3) " 等 ${state.selectedSources.size} 项" else ""
        return "$preview$suffix"
    }

    interface Host {
        fun onPickCompressFilesRequested()
        fun onCreateArchiveRequested(defaultFileName: String)
    }
}
