package com.vaultzip.archive.bridge

import com.vaultzip.archive.impl.MimeRouter
import com.vaultzip.archive.model.*
import com.vaultzip.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class NativeArchiveBridgeImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val mimeRouter: MimeRouter
) : NativeArchiveBridge {

    companion object {
        init {
            System.loadLibrary("archivekit")
        }
    }

    override suspend fun detectFormat(source: ResolvedArchiveSource): ArchiveFormat? {
        return withContext(ioDispatcher) {
            runNative {
                nativeDetectFormat(source.primaryPath, source.partPaths.toTypedArray())
                    ?.let { runCatching { ArchiveFormat.valueOf(it) }.getOrNull() }
            }
        }
    }

    override suspend fun listEntries(
        source: ResolvedArchiveSource,
        password: CharArray?
    ): List<ArchiveEntry> {
        return withContext(ioDispatcher) {
            runNative {
                nativeListEntries(source.primaryPath, source.partPaths.toTypedArray(), password)
                    .map(::toDomainEntry)
            }
        }
    }

    override suspend fun extract(
        source: ResolvedArchiveSource,
        request: ExtractRequest,
        localOutputDir: File,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractResult {
        return withContext(ioDispatcher) {
            runNative {
                nativeExtract(
                    source.primaryPath,
                    source.partPaths.toTypedArray(),
                    localOutputDir.absolutePath,
                    request.password
                ).toDomain()
            }
        }
    }

    override suspend fun extractEntry(
        source: ResolvedArchiveSource,
        request: ExtractSingleEntryRequest,
        localOutputDir: File,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractSingleEntryResult {
        return withContext(ioDispatcher) {
            runNative {
                nativeExtractEntry(
                    source.primaryPath,
                    source.partPaths.toTypedArray(),
                    request.entryPath,
                    localOutputDir.absolutePath,
                    request.password
                ).toDomain()
            }
        }
    }

    private fun toDomainEntry(dto: NativeArchiveEntryDto): ArchiveEntry {
        return ArchiveEntry(
            path = dto.path,
            name = dto.name,
            isDirectory = dto.isDirectory,
            compressedSize = dto.compressedSize,
            uncompressedSize = dto.uncompressedSize,
            modifiedAtMillis = dto.modifiedAtMillis,
            encrypted = dto.encrypted,
            previewableType = mimeRouter.resolve(dto.name)
        )
    }

    private fun NativeExtractResultDto.toDomain(): ExtractResult {
        return ExtractResult(
            extractedCount = extractedCount,
            failedEntries = failedEntries,
            outputPath = outputPath
        )
    }

    private fun NativeExtractSingleEntryResultDto.toDomain(): ExtractSingleEntryResult {
        return ExtractSingleEntryResult(
            extractedFilePath = extractedFilePath
        )
    }

    private inline fun <T> runNative(block: () -> T): T {
        return try {
            block()
        } catch (exception: NativeArchiveException) {
            throw exception.toArchiveError()
        }
    }

    private fun NativeArchiveException.toArchiveError(): Throwable {
        return when (code) {
            "ERR_UNSUPPORTED_FORMAT" -> ArchiveError.UnsupportedFormat
            "ERR_MISSING_VOLUME" -> ArchiveError.MissingVolume
            "ERR_WRONG_PASSWORD" -> ArchiveError.WrongPassword
            "ERR_ENCRYPTED" -> ArchiveError.EncryptedArchive
            "ERR_CORRUPTED" -> ArchiveError.CorruptedArchive
            "ERR_OUTPUT_DENIED" -> ArchiveError.OutputWriteDenied
            "ERR_ENTRY_TOO_LARGE" -> ArchiveError.EntryTooLargeForPreview
            else -> ArchiveError.Unknown(message)
        }
    }

    private external fun nativeDetectFormat(
        primaryPath: String,
        partPaths: Array<String>
    ): String?

    private external fun nativeListEntries(
        primaryPath: String,
        partPaths: Array<String>,
        passwordChars: CharArray?
    ): List<NativeArchiveEntryDto>

    private external fun nativeExtract(
        primaryPath: String,
        partPaths: Array<String>,
        outputDir: String,
        passwordChars: CharArray?
    ): NativeExtractResultDto

    private external fun nativeExtractEntry(
        primaryPath: String,
        partPaths: Array<String>,
        entryPath: String,
        outputDir: String,
        passwordChars: CharArray?
    ): NativeExtractSingleEntryResultDto
}
