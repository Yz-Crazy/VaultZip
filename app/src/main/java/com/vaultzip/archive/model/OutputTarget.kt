package com.vaultzip.archive.model

enum class OverwritePolicy {
    SKIP,
    REPLACE,
    RENAME
}

sealed interface OutputTarget {
    data class LocalDir(val absolutePath: String) : OutputTarget
    data class AppPrivateDir(val absolutePath: String) : OutputTarget
    data class SafTree(val treeUri: String) : OutputTarget
}
