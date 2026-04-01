package com.vaultzip.ui.volume

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class VolumePickerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val names = requireArguments().getStringArrayList(ARG_NAMES).orEmpty()
        val uris = requireArguments().getStringArrayList(ARG_URIS).orEmpty()

        return AlertDialog.Builder(requireContext())
            .setTitle("选择入口卷")
            .setItems(names.toTypedArray()) { _, which ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putString(RESULT_NAME, names[which])
                        putString(RESULT_URI, uris[which])
                    }
                )
            }
            .setNegativeButton("取消", null)
            .create()
    }

    companion object {
        const val TAG = "VolumePickerDialog"
        const val REQUEST_KEY = "volume_picker_request"
        const val RESULT_NAME = "result_name"
        const val RESULT_URI = "result_uri"

        private const val ARG_NAMES = "arg_names"
        private const val ARG_URIS = "arg_uris"

        fun newInstance(candidates: List<VolumeCandidate>): VolumePickerDialogFragment {
            return VolumePickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_NAMES, ArrayList(candidates.map { it.displayName }))
                    putStringArrayList(ARG_URIS, ArrayList(candidates.map { it.fileUri.toString() }))
                }
            }
        }
    }
}
