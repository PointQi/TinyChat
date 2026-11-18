package io.tinychat.markdown

import android.content.Context

/**
 * 渲染性能采样（debug 渲染实验室用，release 不调用）。
 *
 * 存在的原因：行内公式到底是「流式中就换成位图」还是「结束后批量换」，
 * 取决于单个公式出图的真实耗时 —— 这个数字只能实测，不能拍脑袋。
 */
object TcRenderLab {

    /** 一组有代表性的行内公式：分式、上下标、根号、希腊字母、矩阵 */
    val Samples: List<String> = listOf(
        "E=mc^2",
        "\\frac{a}{b}",
        "x_{i}^{2}+y_{i}^{2}=z^{2}",
        "\\sqrt[3]{x+1}",
        "\\sum_{k=1}^{n} k = \\frac{n(n+1)}{2}",
        "\\alpha\\beta\\gamma\\delta\\varepsilon",
        "\\int_{0}^{\\infty} e^{-x^2}\\,dx = \\frac{\\sqrt{\\pi}}{2}",
        "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
    )

    /**
     * 调试：取回一条公式渲染后的 DOM（见 [TcRenderHost.dumpHtml]）。
     * 只在渲染实验室里调用，正常路径不碰。
     */
    suspend fun dumpDom(
        context: Context,
        latex: String,
        fontPx: Int = 51,
        display: Boolean = false,
        widthPx: Int = 900,
        naturalWidth: Boolean = false,
    ): String =
        TcRenderHost.dumpHtml(
            context,
            TcRenderHost.Request(
                kind = TcRenderHost.Kind.MATH,
                source = latex,
                display = display,
                fontPx = fontPx,
                widthPx = widthPx,
                colorHex = "#1A1A1A",
                dark = false,
                naturalWidth = naturalWidth,
            ),
        )

    /**
     * 串行渲染一组公式，返回每条耗时（毫秒）。首次调用包含 WebView 冷启动，
     * 所以要跑两轮：第二轮才是「稳态单条成本」。
     */
    suspend fun benchMath(
        context: Context,
        samples: List<String> = Samples,
        fontPx: Int = 32,
        colorHex: String = "#1A1A1A",
        dark: Boolean = false,
    ): List<Long> = samples.map { latex ->
        val request = TcRenderHost.Request(
            kind = TcRenderHost.Kind.MATH,
            source = latex,
            display = false,
            fontPx = fontPx,
            widthPx = 900,
            colorHex = colorHex,
            dark = dark,
        )
        val started = System.nanoTime()
        TcRenderHost.render(context, request, useCache = false)
        (System.nanoTime() - started) / 1_000_000
    }
}
