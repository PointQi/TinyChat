package io.tinychat.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.tinychat.crash.CrashGuard

/**
 * 崩溃兜底的验收开关（M9-2，只在 debug 包里）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a io.tinychat.debug.CRASH --es kind main
 * adb shell am broadcast -a io.tinychat.debug.CRASH --es kind bg
 * adb shell am broadcast -a io.tinychat.debug.CRASH --es kind stream
 * ```
 * `main` 主线程、`bg` 后台线程、`stream` 假装正在生成时崩（用来验崩溃页的
 * 「当前正在生成的内容可能未保存」那半句）。
 */
class CrashLabReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("kind")) {
            "bg" -> Thread { error("调试用：后台线程崩溃") }.start()
            "stream" -> {
                CrashGuard.streaming = true
                Handler(Looper.getMainLooper()).post { error("调试用：生成中崩溃") }
            }

            else -> error("调试用：主线程崩溃")
        }
    }
}
