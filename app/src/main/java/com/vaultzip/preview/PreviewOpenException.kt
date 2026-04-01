package com.vaultzip.preview

sealed class PreviewOpenException(message: String) : RuntimeException(message) {
    data object UnsupportedType : PreviewOpenException("Unsupported preview type")
    data object MissingLocalFile : PreviewOpenException("Preview file does not exist")
    data object FileTooLarge : PreviewOpenException("File too large for direct preview")
}
