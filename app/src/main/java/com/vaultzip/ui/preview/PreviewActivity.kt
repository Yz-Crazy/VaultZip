package com.vaultzip.ui.preview

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.vaultzip.R
import com.vaultzip.archive.model.PreviewableType
import com.vaultzip.preview.PreviewIntentFactory
import com.vaultzip.ui.preview.viewer.ImagePreviewFragment
import com.vaultzip.ui.preview.viewer.PdfPreviewFragment
import com.vaultzip.ui.preview.viewer.TextPreviewFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PreviewActivity : AppCompatActivity() {

    private val viewModel by viewModels<PreviewViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val localPath = PreviewIntentFactory.readLocalPath(intent)
        val fileName = PreviewIntentFactory.readFileName(intent)
        val type = PreviewIntentFactory.readType(intent)
        val deleteOnClose = PreviewIntentFactory.readDeleteOnClose(intent)

        if (localPath.isNullOrBlank() || type == null) {
            viewModel.bindError("Invalid preview arguments")
            Toast.makeText(this, "预览参数错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.bind(localPath, fileName, type, deleteOnClose)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.previewContainer, createFragment(type, localPath))
            }
        }
    }

    override fun onDestroy() {
        viewModel.cleanupIfNeeded()
        super.onDestroy()
    }

    private fun createFragment(type: PreviewableType, localPath: String) = when (type) {
        PreviewableType.IMAGE -> ImagePreviewFragment.newInstance(localPath)
        PreviewableType.TEXT -> TextPreviewFragment.newInstance(localPath)
        PreviewableType.PDF -> PdfPreviewFragment.newInstance(localPath)
        else -> throw IllegalArgumentException("Unsupported preview type for PoC")
    }
}
