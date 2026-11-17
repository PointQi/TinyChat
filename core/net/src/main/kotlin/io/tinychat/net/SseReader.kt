package io.tinychat.net

import okio.BufferedSource

/**
 * SSE 单帧：`event:` 名（可能缺省）+ 按 `\n` 拼好的 `data:` 全文。
 *
 * 注意：扩展模式**没有 `[DONE]` 帧**，终止靠 `message_stop` 或 error 帧（02 §4.4）。
 */
data class SseEvent(val name: String?, val data: String)

/**
 * 手写 SSE 读取器。**不引第三方库**（02 §4.4）。
 *
 * 必须处理的四类边界：
 * 1. `: keep-alive` 注释行 → 忽略，仅重置读超时；
 * 2. 具名事件 → `event` 名与 JSON `type` 一致，以 JSON `type` 为准（由上层判定）；
 * 3. 无名 data 错误帧（只有 `data: {"error":{...}}`）→ 上层抛 `ApiError(kind=stream)`；
 * 4. 没有 `[DONE]`；EOF 前缺最后一个空行也要 flush。
 *
 * 为什么用 okio 而不是 `BufferedReader`：`readUtf8Line` 按字节扫描换行、
 * 不会把多字节字符切坏，也天然支持 `\r\n`。
 */
object SseReader {

    fun read(source: BufferedSource): Sequence<SseEvent> = sequence {
        val data = StringBuilder()
        var name: String? = null
        var started = false

        while (true) {
            val line = source.readUtf8Line() ?: break

            if (line.isEmpty()) {
                if (started) {
                    // 帧结束：去掉每个 data 行追加的那个换行
                    yield(SseEvent(name, data.toString().removeSuffix("\n")))
                    data.setLength(0)
                    name = null
                    started = false
                }
                continue
            }

            if (line.startsWith(":")) continue // 注释 / 心跳

            started = true
            val colon = line.indexOf(':')
            val field: String
            var value: String
            if (colon < 0) {
                field = line
                value = ""
            } else {
                field = line.substring(0, colon)
                value = line.substring(colon + 1)
                // 规范里冒号后可有一个空格，且只有一个
                if (value.startsWith(" ")) value = value.substring(1)
            }

            when (field) {
                "event" -> name = value
                "data" -> data.append(value).append('\n')
                else -> Unit // id / retry 忽略
            }
        }

        // EOF 时未以空行收尾的帧
        if (started) yield(SseEvent(name, data.toString().removeSuffix("\n")))
    }
}
