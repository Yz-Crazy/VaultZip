package com.vaultzip.ui.main

import com.vaultzip.preview.PreparedPreview
import com.vaultzip.ui.password.PasswordPromptRequest

sealed interface MainUiEvent {
    data class ShowPasswordPrompt(
        val request: PasswordPromptRequest
    ) : MainUiEvent

    data class ShowToast(
        val message: String
    ) : MainUiEvent

    data class OpenPreview(
        val preview: PreparedPreview
    ) : MainUiEvent

    data class ShowMissingParts(
        val missingParts: List<String>
    ) : MainUiEvent

    data class ShowMultiVolumeDetected(
        val formatName: String,
        val partCount: Int
    ) : MainUiEvent
}
