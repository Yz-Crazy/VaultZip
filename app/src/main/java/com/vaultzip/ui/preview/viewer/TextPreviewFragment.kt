package com.vaultzip.ui.preview.viewer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vaultzip.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextPreviewFragment : Fragment(R.layout.fragment_text_preview) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val path = requireArguments().getString(ARG_PATH).orEmpty()
        val textView = view.findViewById<TextView>(R.id.textView)

        viewLifecycleOwner.lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    File(path).bufferedReader().use { it.readText() }
                }.getOrElse { "无法读取文件内容" }
            }
            textView.text = content
        }
    }

    companion object {
        private const val ARG_PATH = "arg_path"

        fun newInstance(localPath: String): TextPreviewFragment {
            return TextPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, localPath)
                }
            }
        }
    }
}
