package com.joat917.qrdecoderlite.util

import android.util.Log

/**
 * 应用日志统一入口。
 *
 * TAG 一律为 `QRLite/<组件>`，与系统日志和第三方库日志分开：
 * ```
 * adb logcat | grep "QRLite/"
 * adb logcat QRLite/WeChat:D '*:S'
 * ```
 * 组件名取固定几个短词，不要临时起名：`MlKit`、`WeChat`、`Combined`、`VM`、`UI`。
 *
 * 消息格式约定为 `<阶段> <key=value>…`，必须能回答「在哪一步、输入是什么、观测到什么」。
 * 例：
 * ```
 * detect texts=1 vertices=0 decoded=0
 * load uri=content://… bytes=2048 bounds=-1x-1
 * process image=1200x900 barcodes=2 dropped=0
 * ```
 */
internal object AppLog {

    private const val PREFIX = "QRLite/"

    fun d(component: String, message: String) {
        Log.d(PREFIX + component, message)
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(PREFIX + component, message, throwable)
    }
}
