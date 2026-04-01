package com.vaultzip.preview

import com.vaultzip.archive.impl.MimeRouter
import com.vaultzip.archive.model.ArchiveEntry
import com.vaultzip.archive.model.ArchiveInput
import com.vaultzip.archive.model.ExtractSingleEntryRequest
import com.vaultzip.archive.model.OutputTarget
import com.vaultzip.archive.model.PreviewableType
import com.vaultzip.domain.ExtractSingleEntryUseCase
import com.vaultzip.storage.TempFileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewCoordinator @Inject constructor(
    private val extractSingleEntryUseCase: ExtractSingleEntryUseCase,
    private val mimeRouter: MimeRouter,
    private val tempFileProvider: TempFileProvider
) {

    suspend fun prepareFromLocalFile(
        localPath: String,
        mimeType: String? = null
    ): PreparedPreview {
        val file = File(localPath)
        if (!file.exists()) throw PreviewOpenException.MissingLocalFile

        val type = mimeRouter.resolve(file.name, mimeType)
        ensurePreviewable(type, file.length())

        return PreparedPreview(
            localPath = file.absolutePath,
            fileName = file.name,
            type = type,
            deleteOnClose = false
        )
    }

    suspend fun prepareFromArchiveEntry(
        archiveInput: ArchiveInput,
        entry: ArchiveEntry,
        password: CharArray?,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)? = null
    ): PreparedPreview {
        ensurePreviewable(entry.previewableType, entry.uncompressedSize)

        val previewDir = tempFileProvider.createPreviewDir()
        val result = extractSingleEntryUseCase(
            request = ExtractSingleEntryRequest(
                input = archiveInput,
                entryPath = entry.path,
                outputDir = OutputTarget.AppPrivateDir(previewDir.absolutePath),
                password = password
            ),
            onProgress = onProgress
        )

        val extracted = File(result.extractedFilePath)
        if (!extracted.exists()) throw PreviewOpenException.MissingLocalFile

        return PreparedPreview(
            localPath = extracted.absolutePath,
            fileName = extracted.name,
            type = entry.previewableType,
            deleteOnClose = true
        )
    }

    private fun ensurePreviewable(type: PreviewableType, sizeBytes: Long?) {
        if (type == PreviewableType.UNSUPPORTED) throw PreviewOpenException.UnsupportedType

        val tooLarge = when (type) {
            PreviewableType.TEXT -> (sizeBytes ?: 0L) > 50L * 1024 * 1024
            PreviewableType.PDF -> (sizeBytes ?: 0L) > 100L * 1024 * 1024
            PreviewableType.VIDEO, PreviewableType.AUDIO -> (sizeBytes ?: 0L) > 200L * 1024 * 1024
            else -> false
        }

        if (tooLarge) throw PreviewOpenException.FileTooLarge
    }
}
