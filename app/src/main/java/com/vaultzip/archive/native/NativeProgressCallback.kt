package com.vaultzip.archive.bridge

fun interface NativeProgressCallback {
    fun onProgress(
        processedBytes: Long,
        totalBytes: Long,
        currentEntryName: String?
    )
}
