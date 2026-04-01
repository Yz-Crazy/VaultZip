package com.vaultzip.storage

import android.content.Context
import android.net.Uri
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
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
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
}
