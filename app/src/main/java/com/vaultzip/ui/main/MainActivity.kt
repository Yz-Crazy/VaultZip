package com.vaultzip.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vaultzip.R
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.compress.model.CompressSource
import com.vaultzip.preview.PreviewIntentFactory
import com.vaultzip.ui.compress.CompressFragment
import com.vaultzip.ui.compress.CompressViewModel
import com.vaultzip.ui.extract.ExtractFragment
import com.vaultzip.ui.password.PasswordPromptDialogFragment
import com.vaultzip.ui.volume.VolumePickerDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ExtractFragment.Host, CompressFragment.Host {

    private val viewModel by viewModels<MainViewModel>()
    private val compressViewModel by viewModels<CompressViewModel>()

    private lateinit var btnTabExtract: Button
    private lateinit var btnTabCompress: Button

    private val pickArchivePartsLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val parts = buildSelectedUriParts(data)
        if (parts.isEmpty()) return@registerForActivityResult
        val orderedParts = orderSelectedParts(parts)
        val displayName = orderedParts.first().name
        if (orderedParts.size == 1) {
            viewModel.onArchiveSelected(orderedParts.first().uri, displayName)
        } else {
            viewModel.onArchivePartsSelected(orderedParts, displayName)
        }
    }

    private val pickCompressFilesLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val sources = buildCompressSources(data)
        if (sources.isEmpty()) return@registerForActivityResult
        compressViewModel.onSourcesSelected(sources)
    }

    private val createArchiveLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uri = data.data ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
        compressViewModel.onOutputSelected(uri, displayName)
    }

    private val pickTreeLauncher = registerForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onDirectorySelected(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTabExtract = findViewById(R.id.btnTabExtract)
        btnTabCompress = findViewById(R.id.btnTabCompress)

        btnTabExtract.setOnClickListener { showTab(Tab.EXTRACT) }
        btnTabCompress.setOnClickListener { showTab(Tab.COMPRESS) }

        supportFragmentManager.setFragmentResultListener(PasswordPromptDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val cancelled = bundle.getBoolean(PasswordPromptDialogFragment.RESULT_CANCELLED, false)
            if (cancelled) {
                viewModel.onPasswordCancelled()
                return@setFragmentResultListener
            }
            viewModel.onPasswordConfirmed(bundle.getString(PasswordPromptDialogFragment.RESULT_PASSWORD).orEmpty())
        }

        supportFragmentManager.setFragmentResultListener(VolumePickerDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val selectedName = bundle.getString(VolumePickerDialogFragment.RESULT_NAME).orEmpty()
            val selectedUri = bundle.getString(VolumePickerDialogFragment.RESULT_URI)?.let(Uri::parse)
                ?: return@setFragmentResultListener
            val treeUri = viewModel.uiState.value.selectedTreeUri ?: return@setFragmentResultListener
            viewModel.onVolumeCandidateSelected(treeUri, selectedUri, selectedName)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val pendingVolumePicker = state.pendingVolumePicker ?: return@collect
                        if (supportFragmentManager.findFragmentByTag(VolumePickerDialogFragment.TAG) == null) {
                            VolumePickerDialogFragment.newInstance(pendingVolumePicker.candidates)
                                .show(supportFragmentManager, VolumePickerDialogFragment.TAG)
                        }
                        viewModel.onVolumePickerPresented()
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MainUiEvent.ShowPasswordPrompt -> {
                                if (supportFragmentManager.findFragmentByTag(PasswordPromptDialogFragment.TAG) == null) {
                                    PasswordPromptDialogFragment.newInstance(event.request)
                                        .show(supportFragmentManager, PasswordPromptDialogFragment.TAG)
                                }
                            }

                            is MainUiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }

                            is MainUiEvent.OpenPreview -> {
                                startActivity(PreviewIntentFactory.create(this@MainActivity, event.preview))
                            }

                            is MainUiEvent.ShowMissingParts -> {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("分卷不完整")
                                    .setMessage(buildString {
                                        append("缺少以下分卷：\n\n")
                                        event.missingParts.forEach { append("• $it\n") }
                                    })
                                    .setPositiveButton("知道了", null)
                                    .show()
                            }

                            is MainUiEvent.ShowMultiVolumeDetected -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "已识别 ${event.formatName} 分卷，共 ${event.partCount} 卷",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        val initialTab = savedInstanceState?.getString(STATE_TAB)?.let(Tab::valueOf) ?: Tab.EXTRACT
        showTab(initialTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, currentTab.name)
    }

    override fun onPickArchiveRequested() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickArchivePartsLauncher.launch(intent)
    }

    override fun onPickDirectoryRequested() {
        pickTreeLauncher.launch(null)
    }

    override fun onPickCompressFilesRequested() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickCompressFilesLauncher.launch(intent)
    }

    override fun onCreateArchiveRequested(defaultFileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, ensureZipFileName(defaultFileName))
        }
        createArchiveLauncher.launch(intent)
    }

    private var currentTab: Tab = Tab.EXTRACT

    private fun showTab(tab: Tab) {
        currentTab = tab
        updateTabState()
        val tag = when (tab) {
            Tab.EXTRACT -> TAG_EXTRACT
            Tab.COMPRESS -> TAG_COMPRESS
        }
        val fragment = when (tab) {
            Tab.EXTRACT -> ExtractFragment()
            Tab.COMPRESS -> CompressFragment()
        }
        supportFragmentManager.commit {
            replace(R.id.mainContentContainer, fragment, tag)
        }
    }

    private fun updateTabState() {
        updateTabButton(btnTabExtract, currentTab == Tab.EXTRACT)
        updateTabButton(btnTabCompress, currentTab == Tab.COMPRESS)
    }

    private fun updateTabButton(button: Button, selected: Boolean) {
        val background = if (selected) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected
        val textColor = if (selected) android.R.color.white else R.color.vz_text_secondary
        button.background = ContextCompat.getDrawable(this, background)
        button.setTextColor(ContextCompat.getColor(this, textColor))
        button.isEnabled = !selected
    }

    private fun buildSelectedUriParts(data: Intent): List<ArchivePartSource.UriPart> {
        val uris = buildList {
            data.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
            data.data?.let(::add)
        }.distinct()

        return uris.map { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ArchivePartSource.UriPart(
                name = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty(),
                uri = uri
            )
        }
    }

    private fun buildCompressSources(data: Intent): List<CompressSource> {
        val uris = buildList {
            data.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
            data.data?.let(::add)
        }.distinct()

        return uris.map { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            CompressSource(
                uri = uri,
                displayName = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty(),
                sizeBytes = querySize(uri)
            )
        }
    }

    private fun orderSelectedParts(parts: List<ArchivePartSource.UriPart>): List<ArchivePartSource.UriPart> {
        val lead = parts.firstOrNull { parseRarPartNumber(it.name) == 1 }
            ?: parts.firstOrNull { isLegacyRarLead(it.name) }
            ?: parts.firstOrNull { isSevenZipLead(it.name) }
            ?: parts.firstOrNull { isSplitZipLead(it.name) }
            ?: parts.firstOrNull { isLegacyZipLead(it.name) }
            ?: parts.sortedBy { it.name.lowercase() }.first()
        return listOf(lead) + parts.filterNot { it.uri == lead.uri }.sortedBy { it.name.lowercase() }
    }

    private fun parseRarPartNumber(fileName: String): Int? {
        return Regex(""".*\.part(\d+)\.rar$""")
            .find(fileName.lowercase())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun isLegacyRarLead(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".rar") && parseRarPartNumber(fileName) == null
    }

    private fun isSevenZipLead(fileName: String): Boolean {
        return fileName.lowercase().matches(Regex(""".*\.7z\.001$"""))
    }

    private fun isSplitZipLead(fileName: String): Boolean {
        return fileName.lowercase().matches(Regex(""".*\.zip\.001$"""))
    }

    private fun isLegacyZipLead(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".zip")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun querySize(uri: Uri): Long? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }

    private fun ensureZipFileName(fileName: String): String {
        return if (fileName.lowercase().endsWith(".zip")) fileName else "$fileName.zip"
    }

    private enum class Tab {
        EXTRACT,
        COMPRESS
    }

    companion object {
        private const val STATE_TAB = "state_tab"
        private const val TAG_EXTRACT = "tab_extract"
        private const val TAG_COMPRESS = "tab_compress"
    }
}
