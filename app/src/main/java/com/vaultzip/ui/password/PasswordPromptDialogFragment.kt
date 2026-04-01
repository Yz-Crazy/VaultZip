package com.vaultzip.ui.password

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class PasswordPromptDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val request = PasswordPromptRequest(
            title = requireArguments().getString(ARG_TITLE).orEmpty(),
            message = requireArguments().getString(ARG_MESSAGE).orEmpty(),
            confirmText = requireArguments().getString(ARG_CONFIRM).orEmpty(),
            cancelText = requireArguments().getString(ARG_CANCEL).orEmpty(),
            showWrongPasswordHint = requireArguments().getBoolean(ARG_WRONG_HINT, false)
        )

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val messageView = TextView(requireContext()).apply {
            text = buildString {
                append(request.message)
                if (request.showWrongPasswordHint) append("\n\n密码错误，请重试。")
            }
        }

        val editText = EditText(requireContext()).apply {
            hint = "压缩包密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        container.addView(messageView)
        container.addView(editText)

        return AlertDialog.Builder(requireContext())
            .setTitle(request.title)
            .setView(container)
            .setPositiveButton(request.confirmText) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putString(RESULT_PASSWORD, editText.text?.toString().orEmpty())
                    }
                )
            }
            .setNegativeButton(request.cancelText) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putBoolean(RESULT_CANCELLED, true)
                    }
                )
            }
            .create()
    }

    companion object {
        const val TAG = "PasswordPromptDialog"
        const val REQUEST_KEY = "password_prompt_request_key"
        const val RESULT_PASSWORD = "result_password"
        const val RESULT_CANCELLED = "result_cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_CONFIRM = "arg_confirm"
        private const val ARG_CANCEL = "arg_cancel"
        private const val ARG_WRONG_HINT = "arg_wrong_hint"

        fun newInstance(request: PasswordPromptRequest): PasswordPromptDialogFragment {
            return PasswordPromptDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, request.title)
                    putString(ARG_MESSAGE, request.message)
                    putString(ARG_CONFIRM, request.confirmText)
                    putString(ARG_CANCEL, request.cancelText)
                    putBoolean(ARG_WRONG_HINT, request.showWrongPasswordHint)
                }
            }
        }
    }
}
