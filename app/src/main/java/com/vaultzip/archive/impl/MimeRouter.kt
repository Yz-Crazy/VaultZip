package com.vaultzip.archive.impl

import com.vaultzip.archive.model.PreviewableType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MimeRouter @Inject constructor() {

    fun resolve(fileName: String, mimeType: String? = null): PreviewableType {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") -> PreviewableType.IMAGE

            lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
                lower.endsWith(".mov") -> PreviewableType.VIDEO

            lower.endsWith(".mp3") || lower.endsWith(".aac") ||
                lower.endsWith(".wav") || lower.endsWith(".flac") -> PreviewableType.AUDIO

            lower.endsWith(".txt") || lower.endsWith(".md") ||
                lower.endsWith(".json") || lower.endsWith(".xml") ||
                lower.endsWith(".log") || lower.endsWith(".csv") -> PreviewableType.TEXT

            lower.endsWith(".pdf") -> PreviewableType.PDF
            else -> PreviewableType.UNSUPPORTED
        }
    }
}
