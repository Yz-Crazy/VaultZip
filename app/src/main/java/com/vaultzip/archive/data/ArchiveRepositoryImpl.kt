package com.vaultzip.archive.data

import com.vaultzip.archive.model.*
import com.vaultzip.archive.bridge.NativeArchiveBridge
import com.vaultzip.archive.bridge.ResolvedArchiveSource
import com.vaultzip.storage.OutputDirectoryManager
import com.vaultzip.storage.TempFileProvider
import com.vaultzip.storage.UriResolver
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveRepositoryImpl @Inject constructor(
    private val nativeBridge: NativeArchiveBridge,
    private val uriResolver: UriResolver,
    private val outputDirectoryManager: OutputDirectoryManager,
    private val tempFileProvider: TempFileProvider
) : ArchiveRepository {

    override suspend fun detectFormat(input: ArchiveInput): ArchiveFormat {
        val resolved = resolveArchiveInput(input)
        return try {
            nativeBridge.detectFormat(resolved.source) ?: ArchiveFormat.UNKNOWN
        } finally {
            resolved.cleanup()
        }
    }

    override suspend fun listEntries(
        input: ArchiveInput,
        password: CharArray?
    ): List<ArchiveEntry> {
        val resolved = resolveArchiveInput(input)
        return try {
            nativeBridge.listEntries(resolved.source, password)
        } finally {
            resolved.cleanup()
        }
    }

    override suspend fun extract(
        request: ExtractRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractResult {
        val resolved = resolveArchiveInput(request.input)
        val outputDir = outputDirectoryManager.prepareExtractOutputDir(request.outputDir)

        return try {
            nativeBridge.extract(
                source = resolved.source,
                request = request,
                localOutputDir = outputDir,
                onProgress = onProgress
            ).copy(outputPath = outputDirectoryManager.exportDirectoryIfNeeded(request.outputDir, outputDir))
        } finally {
            resolved.cleanup()
        }
    }

    override suspend fun extractEntry(
        request: ExtractSingleEntryRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractSingleEntryResult {
        val resolved = resolveArchiveInput(request.input)
        val outputDir = outputDirectoryManager.prepareExtractOutputDir(request.outputDir)
        return try {
            val result = nativeBridge.extractEntry(
                source = resolved.source,
                request = request,
                localOutputDir = outputDir,
                onProgress = onProgress
            )
            result.copy(
                extractedFilePath = outputDirectoryManager.exportExtractedFileIfNeeded(
                    target = request.outputDir,
                    localExtractedFilePath = result.extractedFilePath,
                    localOutputDir = outputDir
                )
            )
        } finally {
            resolved.cleanup()
        }
    }

    private suspend fun resolveArchiveInput(input: ArchiveInput): ResolvedArchiveHandle {
        return when (input) {
            is ArchiveInput.LocalFile -> {
                ResolvedArchiveHandle(
                    source = ResolvedArchiveSource(primaryPath = input.absolutePath)
                )
            }

            is ArchiveInput.ContentUri -> {
                val copied = uriResolver.copyToReadableLocalFile(input.uri)
                ResolvedArchiveHandle(
                    source = ResolvedArchiveSource(primaryPath = copied.absolutePath),
                    cleanupTargets = listOf(copied)
                )
            }

            is ArchiveInput.VolumeGroup -> {
                val staged = uriResolver.copyVolumeGroupToSessionDir(input.parts)
                val splitArchive = maybeAssembledSplitArchive(staged.files)
                if (splitArchive != null) {
                    ResolvedArchiveHandle(
                        source = ResolvedArchiveSource(primaryPath = splitArchive.absolutePath),
                        cleanupTargets = listOfNotNull(staged.sessionDir, splitArchive)
                    )
                } else {
                    ResolvedArchiveHandle(
                        source = ResolvedArchiveSource(
                            primaryPath = selectPrimaryPath(staged.files).absolutePath,
                            partPaths = staged.files.map { it.absolutePath }
                        ),
                        cleanupTargets = listOfNotNull(staged.sessionDir)
                    )
                }
            }
        }
    }

    private fun maybeAssembledSplitArchive(files: List<File>): File? {
        val lead = files.firstOrNull { isSevenZipLead(it.name) }
            ?: files.firstOrNull { isSplitZipLead(it.name) }
            ?: return null
        val ordered = files.sortedBy { parseSplitSequence(it.name) ?: Int.MAX_VALUE }
        val suffix = when {
            isSevenZipLead(lead.name) -> ".7z"
            isSplitZipLead(lead.name) -> ".zip"
            else -> ".tmp"
        }
        val merged = tempFileProvider.createStagingFile(
            prefix = lead.name.substringBeforeLast('.'),
            suffix = suffix
        )
        merged.outputStream().use { output ->
            ordered.forEach { part ->
                part.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        return merged
    }

    private fun selectPrimaryPath(files: List<File>): File {
        return files.firstOrNull { parseRarPartNumber(it.name) == 1 }
            ?: files.firstOrNull { isLegacyRarLead(it.name) }
            ?: files.firstOrNull { isSevenZipLead(it.name) }
            ?: files.firstOrNull { isSplitZipLead(it.name) }
            ?: files.firstOrNull { isLegacyZipLead(it.name) }
            ?: files.first()
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

    private fun parseSplitSequence(fileName: String): Int? {
        val lower = fileName.lowercase()
        Regex(""".*\.(?:7z|zip)\.(\d+)$""").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }
}

private data class ResolvedArchiveHandle(
    val source: ResolvedArchiveSource,
    val cleanupTargets: List<File> = emptyList()
) {
    fun cleanup() {
        cleanupTargets.forEach(::deleteRecursivelyQuietly)
    }

    private fun deleteRecursivelyQuietly(file: File) {
        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach(::deleteRecursivelyQuietly)
        }
        file.delete()
    }
}
