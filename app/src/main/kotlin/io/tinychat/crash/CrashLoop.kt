package io.tinychat.crash

import java.io.File

/**
 * 崩溃循环判定（M9-2）。
 *
 * 同一种异常在 [WINDOW_MILLIS] 内累计出现 [LIMIT] 次，就不再弹崩溃页——那时候用户
 * 每打开一次 App 就撞一次，页面帮不上忙，只会挡路。计数写成一行一条的纯文本
 * （`毫秒 签名`），不走 DataStore：崩溃路径上不该依赖任何还需要初始化异步栈的组件。
 */
object CrashLoop {

    const val WINDOW_MILLIS = 10 * 60 * 1000L
    const val LIMIT = 3

    /** 窗口内已经记过几次同样的签名。 */
    fun recent(file: File, signature: String, now: Long): Int =
        read(file).count { it.first == signature && now - it.second <= WINDOW_MILLIS }

    /** 记一次；顺手把窗口外的老记录丢掉，文件不会无限长。 */
    fun record(file: File, signature: String, now: Long) {
        runCatching {
            val kept = read(file).filter { now - it.second <= WINDOW_MILLIS }
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    kept.forEach { append(it.second).append(' ').append(it.first).append('\n') }
                    append(now).append(' ').append(signature).append('\n')
                },
                Charsets.UTF_8,
            )
        }
    }

    fun clear(file: File) {
        runCatching { file.delete() }
    }

    private fun read(file: File): List<Pair<String, Long>> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8).mapNotNull { line ->
                val cut = line.indexOf(' ')
                if (cut <= 0) return@mapNotNull null
                val millis = line.substring(0, cut).toLongOrNull() ?: return@mapNotNull null
                val signature = line.substring(cut + 1).trim()
                if (signature.isEmpty()) null else signature to millis
            }
        }.getOrDefault(emptyList())
    }
}
