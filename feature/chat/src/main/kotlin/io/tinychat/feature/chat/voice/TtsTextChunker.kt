package io.tinychat.feature.chat.voice

/**
 * 长文按句切分（M5-5）。
 *
 * 为什么要切：TTS 单次请求 `text` 上限 **10000 计费字符**，而**汉字算 2 个**，
 * 也就是 5000 个汉字就顶格了。一篇长回答经常超过，直接发会 400。
 *
 * 切法：先按句末标点切开，再贪心拼到接近上限；单句本身就超限时硬切。
 * 段与段之间是**串行**合成的（上一段播完再发下一段请求），不做预取：
 * 实测一次请求约 6 倍速产出，2800 字的段大约 1 分钟内合成完，
 * 音频则要播 9 分钟，预取的收益远小于"多一个并发请求"的复杂度。
 */
object TtsTextChunker {

    /** 每段上限（计费字符）。留足余量：请求体 64KiB 限制也不是瓶颈。 */
    const val MAX_BILLING_CHARS = 3_000

    private val SENTENCE_ENDS = charArrayOf('。', '！', '？', '；', '!', '?', ';', '\n')

    /** 按句切段，顺序不变，不做去重与润色。 */
    fun split(text: String, limit: Int = MAX_BILLING_CHARS): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var currentCost = 0

        fun flush() {
            val piece = current.toString().trim()
            if (piece.isNotEmpty()) segments += piece
            current.setLength(0)
            currentCost = 0
        }

        for (sentence in sentences(trimmed)) {
            val cost = billingChars(sentence)
            if (cost > limit) {
                // 单句就超限：先把手上这段收尾，再把长句按字数硬切
                flush()
                segments += hardSplit(sentence, limit)
                continue
            }
            if (currentCost + cost > limit) flush()
            current.append(sentence)
            currentCost += cost
        }
        flush()
        return segments
    }

    /** 计费字符：汉字 2，其余 1（Alibaba/DeepSeek 口径，见 03 §6.6）。 */
    fun billingChars(text: String): Int = text.sumOf { if (it.isHan()) 2 else 1 }

    /** 按句末标点切，标点跟着上一句走。 */
    private fun sentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        for (char in text) {
            builder.append(char)
            if (char in SENTENCE_ENDS) {
                val piece = builder.toString()
                if (piece.isNotBlank()) result += piece
                builder.setLength(0)
            }
        }
        if (builder.isNotBlank()) result += builder.toString()
        return result
    }

    /** 长句硬切：保证每段都是合法 UTF-16（不在代理对中间断开）。 */
    private fun hardSplit(sentence: String, limit: Int): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var cost = 0
        var index = 0
        while (index < sentence.length) {
            val char = sentence[index]
            val width = if (Character.isHighSurrogate(char) && index + 1 < sentence.length &&
                Character.isLowSurrogate(sentence[index + 1])
            ) {
                2
            } else {
                1
            }
            val piece = sentence.substring(index, index + width)
            val pieceCost = billingChars(piece)
            if (cost + pieceCost > limit && builder.isNotEmpty()) {
                result += builder.toString()
                builder.setLength(0)
                cost = 0
            }
            builder.append(piece)
            cost += pieceCost
            index += width
        }
        if (builder.isNotEmpty()) result += builder.toString()
        return result
    }

    private fun Char.isHan(): Boolean =
        this in '\u4E00'..'\u9FFF' || this in '\u3400'..'\u4DBF' || this in '\uF900'..'\uFAFF'
}
