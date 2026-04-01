package com.vaultzip.storage

import android.content.Context
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TempFileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createStagingFile(prefix: String, suffix: String = ".tmp"): File {
        val dir = File(context.cacheDir, "archive_staging").apply { mkdirs() }
        return File(dir, "${prefix}_${UUID.randomUUID()}$suffix")
    }

    fun createStagingSessionDir(sessionId: String = UUID.randomUUID().toString()): File {
        return File(File(context.cacheDir, "archive_staging_sessions"), sessionId).apply { mkdirs() }
    }

    fun createPreviewDir(): File {
        return File(context.cacheDir, "archive_preview").apply { mkdirs() }
    }

    fun createExtractTempDir(): File {
        return File(File(context.cacheDir, "archive_extract"), UUID.randomUUID().toString()).apply { mkdirs() }
    }
}
