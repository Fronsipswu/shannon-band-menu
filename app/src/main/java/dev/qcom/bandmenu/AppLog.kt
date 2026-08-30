package dev.qcom.bandmenu

import android.util.Log

object AppLog {
    @Volatile
    var debugEnabled = true

    private const val MAX_LOG_LEN = 3800

    fun d(tag: String, msg: String) {
        if (!debugEnabled) return
        if (msg.length <= MAX_LOG_LEN) {
            Log.d(tag, msg)
        } else {
            var idx = 0
            while (idx < msg.length) {
                val end = minOf(idx + MAX_LOG_LEN, msg.length)
                Log.d(tag, msg.substring(idx, end))
                idx = end
            }
        }
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
    }

    fun i(tag: String, msg: String) {
        if (msg.length <= MAX_LOG_LEN) {
            Log.i(tag, msg)
        } else {
            var idx = 0
            while (idx < msg.length) {
                val end = minOf(idx + MAX_LOG_LEN, msg.length)
                Log.i(tag, msg.substring(idx, end))
                idx = end
            }
        }
    }
}
