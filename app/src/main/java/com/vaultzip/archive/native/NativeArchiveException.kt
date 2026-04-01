package com.vaultzip.archive.bridge

class NativeArchiveException(
    val code: String,
    override val message: String
) : RuntimeException(message)
