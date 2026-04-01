package com.vaultzip.compress

import android.content.Context
import com.vaultzip.compress.model.CompressRequest
import com.vaultzip.compress.model.CompressResult
import com.vaultzip.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class CompressRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CompressRepository {

    override suspend fun createZipArchive(
        request: CompressRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?, currentEntryName: String?) -> Unit)?
    ): CompressResult = withContext(ioDispatcher) {
        require(request.sources.isNotEmpty()) { "请先选择要压缩的文件" }

        val totalBytes = request.sources
            .map { it.sizeBytes }
            .takeIf { sizes -> sizes.all { it != null } }
            ?.sumOf { it ?: 0L }

        val usedNames = linkedSetOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var processedBytes = 0L

        val outputStream = context.contentResolver.openOutputStream(request.outputUri, WRITE_MODE)
            ?: error("无法创建输出文件")

        outputStream.use { rawOutput ->
            ZipOutputStream(BufferedOutputStream(rawOutput)).use { zipOutput ->
                request.sources.forEach { source ->
                    val entryName = buildUniqueEntryName(source.displayName, usedNames)
                    onProgress?.invoke(processedBytes, totalBytes, entryName)

                    val inputStream = context.contentResolver.openInputStream(source.uri)
                        ?: error("无法读取文件：${source.displayName}")

                    inputStream.use { rawInput ->
                        BufferedInputStream(rawInput).use { input ->
                            zipOutput.putNextEntry(ZipEntry(entryName))
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                zipOutput.write(buffer, 0, read)
                                processedBytes += read
                                onProgress?.invoke(processedBytes, totalBytes, entryName)
                            }
                            zipOutput.closeEntry()
                        }
                    }
                }
            }
        }

        CompressResult(
            outputUri = request.outputUri,
            archiveName = request.archiveName,
            entryCount = request.sources.size,
            totalBytes = processedBytes
        )
    }

    private fun buildUniqueEntryName(displayName: String, usedNames: MutableSet<String>): String {
        val sanitized = sanitizeEntryName(displayName)
        if (usedNames.add(sanitized)) return sanitized

        val dotIndex = sanitized.lastIndexOf('.')
        val base = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
        val extension = if (dotIndex > 0) sanitized.substring(dotIndex) else ""

        var index = 2
        while (true) {
            val candidate = "$base ($index)$extension"
            if (usedNames.add(candidate)) return candidate
            index++
        }
    }

    private fun sanitizeEntryName(name: String): String {
        val trimmed = name.trim().ifBlank { "file" }
        return trimmed.replace(PATH_SEPARATOR_REGEX, "_")
    }

    companion object {
        private const val WRITE_MODE = "w"
        private val PATH_SEPARATOR_REGEX = Regex("[\\\\/]+")
    }
}
