package com.vaultzip.ui.preview.viewer

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.vaultzip.R
import java.io.File

class ImagePreviewFragment : Fragment(R.layout.fragment_image_preview) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val path = requireArguments().getString(ARG_PATH).orEmpty()
        val imageView = view.findViewById<SubsamplingScaleImageView>(R.id.imageView)
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            imageView.setImage(ImageSource.uri(Uri.fromFile(file)))
        }.onFailure {
            Toast.makeText(requireContext(), "图片预览失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        view?.findViewById<SubsamplingScaleImageView>(R.id.imageView)?.recycle()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PATH = "arg_path"

        fun newInstance(localPath: String): ImagePreviewFragment {
            return ImagePreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, localPath)
                }
            }
        }
    }
}
