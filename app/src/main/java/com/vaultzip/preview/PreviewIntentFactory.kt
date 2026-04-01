package com.vaultzip.preview

import android.content.Context
import android.content.Intent
import com.vaultzip.archive.model.PreviewableType
import com.vaultzip.ui.preview.PreviewActivity

object PreviewIntentFactory {
    private const val EXTRA_LOCAL_PATH = "extra_local_path"
    private const val EXTRA_FILE_NAME = "extra_file_name"
    private const val EXTRA_TYPE = "extra_type"
    private const val EXTRA_DELETE_ON_CLOSE = "extra_delete_on_close"

    fun create(context: Context, preview: PreparedPreview): Intent {
        return Intent(context, PreviewActivity::class.java).apply {
            putExtra(EXTRA_LOCAL_PATH, preview.localPath)
            putExtra(EXTRA_FILE_NAME, preview.fileName)
            putExtra(EXTRA_TYPE, preview.type.name)
            putExtra(EXTRA_DELETE_ON_CLOSE, preview.deleteOnClose)
        }
    }

    fun readLocalPath(intent: Intent): String? = intent.getStringExtra(EXTRA_LOCAL_PATH)

    fun readFileName(intent: Intent): String = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()

    fun readType(intent: Intent): PreviewableType? {
        val raw = intent.getStringExtra(EXTRA_TYPE) ?: return null
        return runCatching { PreviewableType.valueOf(raw) }.getOrNull()
    }

    fun readDeleteOnClose(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_DELETE_ON_CLOSE, false)
    }
}
