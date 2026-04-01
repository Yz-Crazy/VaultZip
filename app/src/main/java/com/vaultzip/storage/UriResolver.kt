package com.vaultzip.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class UriResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileProvider: TempFileProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun copyToReadableLocalFile(
        uri: Uri,
        fallbackName: String = "archive_input"
    ): File = withContext(ioDispatcher) {
        val displayName = queryDisplayName(uri) ?: fallbackName
        val suffix = displayName.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".${it}" }
            ?: ".tmp"

        val target = tempFileProvider.createStagingFile(
            prefix = displayName.substringBeforeLast('.', displayName),
            suffix = suffix
        )

        copyUriToFile(uri, target)
        target
    }

    suspend fun copyToReadableLocalFilePreserveName(
        uri: Uri,
        targetDir: File,
        fallbackName: String = "archive_input"
    ): File = withContext(ioDispatcher) {
        targetDir.mkdirs()
        val displayName = queryDisplayName(uri) ?: fallbackName
        val target = File(targetDir, displayName)
        copyUriToFile(uri, target)
        target
    }

    suspend fun copyVolumeGroupToSessionDir(parts: List<ArchivePartSource>): StagedVolumeGroup {
        return withContext(ioDispatcher) {
            val needsSessionDir = parts.any { it is ArchivePartSource.UriPart }
            val sessionDir = needsSessionDir.takeIf { it }
                ?.let { tempFileProvider.createStagingSessionDir() }

            val files = parts.map { part ->
                when (part) {
                    is ArchivePartSource.LocalPart -> {
                        if (sessionDir == null) {
                            File(part.absolutePath)
                        } else {
                            File(part.absolutePath).copyTo(
                                target = File(sessionDir, part.name),
                                overwrite = true
                            )
                        }
                    }
                    is ArchivePartSource.UriPart -> {
                        val targetDir = requireNotNull(sessionDir)
                        val target = File(targetDir, part.name)
                        copyUriToFile(part.uri, target)
                        target
                    }
                }
            }

            StagedVolumeGroup(
                files = files,
                sessionDir = sessionDir
            )
        }
    }

    fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
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

    private fun copyUriToFile(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open input stream for uri=$uri" }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

data class StagedVolumeGroup(
    val files: List<File>,
    val sessionDir: File?
)
