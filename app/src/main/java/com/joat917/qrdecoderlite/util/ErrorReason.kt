package com.joat917.qrdecoderlite.util

/**
 * 把异常转成用户可见的错误原因，格式为 `【类名】 异常自带 message`。
 *
 * 只有 message 为空或全空白时才用调用方给的兜底文字。兜底文字来自
 * `strings.xml`，属于「什么都拿不到时」的最后手段，不能反过来盖掉真实信息。
 */
internal fun Throwable.toErrorReason(fallback: String? = null): String {
    val name = this::class.simpleName ?: "Throwable"
    val text = message?.takeIf { it.isNotBlank() } ?: fallback
    return if (text.isNullOrBlank()) "[$name]" else "[$name] $text"
}
