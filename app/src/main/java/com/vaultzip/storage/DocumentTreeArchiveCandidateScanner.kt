package com.vaultzip.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.vaultzip.ui.volume.VolumeCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentTreeArchiveCandidateScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : ArchiveCandidateScanner {

    override suspend fun scan(treeUri: Uri): List<VolumeCandidate> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null) {
            Log.w(TAG, "DocumentFile.fromTreeUri returned null: $treeUri")
            return emptyList()
        }

        return runCatching {
            val files = root.listFiles()
            Log.d(TAG, "Scanned ${files.size} files from tree uri: $treeUri")
            files
                .filter { it.isFile && !it.name.isNullOrBlank() }
                .mapNotNull { file ->
                    val name = file.name.orEmpty()
                    if (looksLikeArchiveOrVolume(name)) {
                        VolumeCandidate(
                            displayName = name,
                            fileUri = file.uri
                        )
                    } else {
                        null
                    }
                }
                .sortedBy { it.displayName.lowercase() }
                .also { candidates ->
                    Log.d(TAG, "Matched ${candidates.size} archive candidates for tree uri: $treeUri")
                }
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to scan tree uri: $treeUri", throwable)
            emptyList()
        }
    }

    private fun looksLikeArchiveOrVolume(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".zip") ||
            lower.endsWith(".zipx") ||
            lower.endsWith(".rar") ||
            lower.endsWith(".7z") ||
            lower.endsWith(".tar") ||
            lower.endsWith(".tgz") ||
            lower.endsWith(".tar.gz") ||
            lower.endsWith(".gz") ||
            lower.matches(Regex(""".*\.7z\.\d{3}$""")) ||
            lower.matches(Regex(""".*\.part\d+\.rar$""")) ||
            lower.matches(Regex(""".*\.r\d{2}$""")) ||
            lower.matches(Regex(""".*\.z\d{2}$""")) ||
            lower.matches(Regex(""".*\.zip\.\d{3}$"""))
    }

    companion object {
        private const val TAG = "ArchiveCandidateScan"
    }
}
