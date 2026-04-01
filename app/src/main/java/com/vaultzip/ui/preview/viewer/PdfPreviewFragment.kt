package com.vaultzip.ui.preview.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vaultzip.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class PdfPreviewFragment : Fragment(R.layout.fragment_pdf_preview) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var renderJob: Job? = null
    private var currentBitmap: Bitmap? = null
    private var currentPageIndex: Int = 0
    private var previewPageCount: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        currentPageIndex = savedInstanceState?.getInt(STATE_PAGE_INDEX) ?: 0

        val path = requireArguments().getString(ARG_PATH).orEmpty()
        val imageView = view.findViewById<ImageView>(R.id.pdfPageImageView)
        val btnPrev = view.findViewById<Button>(R.id.btnPrevPage)
        val btnNext = view.findViewById<Button>(R.id.btnNextPage)
        val tvPageIndicator = view.findViewById<TextView>(R.id.tvPageIndicator)

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "PDF 文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
        previewPageCount = min(pdfRenderer?.pageCount ?: 0, 10)
        if (previewPageCount <= 0) {
            Toast.makeText(requireContext(), "PDF 没有可预览页面", Toast.LENGTH_SHORT).show()
            return
        }
        currentPageIndex = currentPageIndex.coerceIn(0, previewPageCount - 1)

        btnPrev.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex -= 1
                renderCurrentPage(imageView, tvPageIndicator, btnPrev, btnNext)
            }
        }
        btnNext.setOnClickListener {
            if (currentPageIndex < previewPageCount - 1) {
                currentPageIndex += 1
                renderCurrentPage(imageView, tvPageIndicator, btnPrev, btnNext)
            }
        }

        imageView.post {
            renderCurrentPage(imageView, tvPageIndicator, btnPrev, btnNext)
        }
    }

    private fun renderCurrentPage(
        imageView: ImageView,
        tvPageIndicator: TextView,
        btnPrev: Button,
        btnNext: Button
    ) {
        val renderer = pdfRenderer ?: return
        val pageIndex = currentPageIndex.coerceIn(0, max(previewPageCount - 1, 0))
        tvPageIndicator.text = "${pageIndex + 1} / $previewPageCount"
        btnPrev.isEnabled = pageIndex > 0
        btnNext.isEnabled = pageIndex < previewPageCount - 1

        val targetWidth = imageView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val targetHeight = imageView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val page = renderer.openPage(pageIndex)
                try {
                    val pageWidth = max(page.width, 1)
                    val pageHeight = max(page.height, 1)
                    val scale = min(
                        targetWidth.toFloat() / pageWidth.toFloat(),
                        targetHeight.toFloat() / pageHeight.toFloat()
                    ).coerceAtLeast(0.1f)
                    val bitmapWidth = max((pageWidth * scale).toInt(), 1)
                    val bitmapHeight = max((pageHeight * scale).toInt(), 1)
                    Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            }
            currentBitmap?.recycle()
            currentBitmap = bitmap
            imageView.setImageBitmap(bitmap)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE_INDEX, currentPageIndex)
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        currentBitmap?.recycle()
        currentBitmap = null
        pdfRenderer?.close()
        pdfRenderer = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PATH = "arg_path"
        private const val STATE_PAGE_INDEX = "state_page_index"

        fun newInstance(localPath: String): PdfPreviewFragment {
            return PdfPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, localPath)
                }
            }
        }
    }
}
