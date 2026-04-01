package com.vaultzip.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultzip.archive.model.ArchiveError
import com.vaultzip.archive.model.OutputTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutputDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileProvider: TempFileProvider
) {

    fun prepareExtractOutputDir(target: OutputTarget): File {
        return when (target) {
            is OutputTarget.LocalDir -> File(target.absolutePath).apply { mkdirs() }
            is OutputTarget.AppPrivateDir -> File(target.absolutePath).apply { mkdirs() }
            is OutputTarget.SafTree -> tempFileProvider.createExtractTempDir()
        }
    }

    fun exportDirectoryIfNeeded(target: OutputTarget, localOutputDir: File): String {
        return try {
            when (target) {
                is OutputTarget.LocalDir,
                is OutputTarget.AppPrivateDir -> localOutputDir.absolutePath
                is OutputTarget.SafTree -> {
                    exportTree(localOutputDir, Uri.parse(target.treeUri))
                    target.treeUri
                }
            }
        } finally {
            cleanupTempDirIfNeeded(target, localOutputDir)
        }
    }

    fun exportExtractedFileIfNeeded(
        target: OutputTarget,
        localExtractedFilePath: String,
        localOutputDir: File
    ): String {
        return try {
            when (target) {
                is OutputTarget.LocalDir,
                is OutputTarget.AppPrivateDir -> localExtractedFilePath
                is OutputTarget.SafTree -> {
                    exportTree(localOutputDir, Uri.parse(target.treeUri))
                    target.treeUri
                }
            }
        } finally {
            cleanupTempDirIfNeeded(target, localOutputDir)
        }
    }

    private fun exportTree(localOutputDir: File, treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw ArchiveError.OutputWriteDenied
        if (!root.exists() || !root.canWrite()) {
            throw ArchiveError.OutputWriteDenied
        }
        localOutputDir.listFiles().orEmpty().forEach { child ->
            copyRecursivelyToTree(child, root)
        }
    }

    private fun copyRecursivelyToTree(source: File, targetDir: DocumentFile) {
        if (source.isDirectory) {
            val dir = targetDir.findFile(source.name)
                ?.takeIf { it.isDirectory }
                ?: targetDir.createDirectory(source.name)
                ?: throw ArchiveError.OutputWriteDenied
            source.listFiles().orEmpty().forEach { child ->
                copyRecursivelyToTree(child, dir)
            }
            return
        }

        targetDir.findFile(source.name)?.delete()
        val target = targetDir.createFile(resolveMimeType(source.name), source.name)
            ?: throw ArchiveError.OutputWriteDenied
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
            source.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw ArchiveError.OutputWriteDenied
    }

    private fun cleanupTempDirIfNeeded(target: OutputTarget, localOutputDir: File) {
        if (target is OutputTarget.SafTree) {
            deleteRecursivelyQuietly(localOutputDir)
        }
    }

    private fun deleteRecursivelyQuietly(file: File) {
        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach(::deleteRecursivelyQuietly)
        }
        file.delete()
    }

    private fun resolveMimeType(fileName: String): String {
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
    }
}
