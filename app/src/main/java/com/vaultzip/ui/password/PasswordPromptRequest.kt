package com.vaultzip.ui.password

data class PasswordPromptRequest(
    val title: String = "请输入密码",
    val message: String = "该压缩包已加密，请输入密码继续",
    val confirmText: String = "确定",
    val cancelText: String = "取消",
    val showWrongPasswordHint: Boolean = false
)
