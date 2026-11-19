package io.tinychat.markdown

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.max

/**
 * 公式与图表的离屏渲染宿主（M2-5 / M2-6）。
 *
 * **架构决定（02 §12 / 03 决策 19，别改成「列表里挂 WebView」）**：
 * 整个应用只有一个**不挂到窗口**的 WebView，串行地把 LaTeX / Mermaid 渲染成位图；
 * 消息列表里只放 `Image`。这样付出的是「每个公式一次性出图」的成本，
 * 换掉的是三笔随正文长度线性恶化的账：段落重排、WebView 存量、WebView 吞竖向手势。
 *
 * 位图按 **1:1 物理像素**出（`fontPx` 传的是 px，不是 dp）：显示端再按 density 折回 dp，
 * 所以数学字体在 2x 屏上依然是清晰的，不做缩放大图。
 */
internal object TcRenderHost {

    /** 渲染请求。[source] 对 math 是 LaTeX，对 mermaid 是图表源码。 */
    data class Request(
        val kind: Kind,
        val source: String,
        val display: Boolean = false,
        val fontPx: Int,
        val widthPx: Int,
        val colorHex: String,
        val dark: Boolean,
        /**
         * 块级公式按**内容自然宽度**排版（不限制在容器宽内），出图可能比屏幕宽 ——
         * 显示端再用横向滚动查看。宽公式（多列矩阵、长推导）只能这么处理。
         */
        val naturalWidth: Boolean = false,
    )

    enum class Kind(val wire: String) { MATH("math"), MERMAID("mermaid") }

    /** 结果。[bitmap] 为空表示失败（[error] 里有原因），调用方退回源码/占位。 */
    data class Result(
        val bitmap: ImageBitmap?,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
        /**
         * 基线在位图中的 y（物理 px）；0 = 没量到（块级公式、mermaid 都是 0）。
         *
         * 行内公式靠它对齐正文：占位框只占 [0, baselinePx) 这段（即基线以上），
         * 位图从占位框顶端按原尺寸往下画，伸进基线以下的部分自然落进行距里。
         */
        val baselinePx: Int = 0,
        val error: String? = null,
        /**
         * 内容的**自然宽度**（物理 px）：`scrollWidth` 比盒子宽就说明这条公式比容器还长，
         * 显示端据此改用「自然宽度出图 + 横向滚动」，而不是把公式切掉。
         */
        val naturalWidthPx: Int = 0,
        /**
         * 墨迹在右侧被视口切掉了（块级公式比容器宽）。
         *
         * 为什么不用 `scrollWidth` 判断：`overflow: visible` 的元素上它返回的是自身宽度，
         * **不会**算上溢出内容 —— 实测永远等于容器宽，判据恒为假。
         * 只有像素墨迹贴到右边缘才是真的被切。
         */
        val clippedRight: Boolean = false,
    )

    /** 位图缓存上限：公式都很小，24MB 能放几千个；按字节计，超了先淘汰旧的 */
    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, Result>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Result): Int =
            ((value.bitmap?.width ?: 1) * (value.bitmap?.height ?: 1) * 4).coerceAtLeast(1)
    }

    /** WebView 只能串行用：前一个渲染没画完就去改内容，会量到半张图 */
    private val mutex = Mutex()

    private var webView: WebView? = null
    private var pageLoaded = false
    private var loading: CompletableDeferred<Boolean>? = null
    private var pending: CompletableDeferred<BridgeResult>? = null
    private var nextId = 1

    /** 正在渲染/排队的张数（分享长图用；跨线程读，所以是原子量） */
    private val inFlight = java.util.concurrent.atomic.AtomicInteger(0)

    private data class BridgeResult(
        val ok: Boolean,
        /** 公式基线在**视口坐标**里的 y（CSS px），行内公式靠它对齐正文；0 = 没量到 */
        val baselinePx: Int,
        /** stage 盒子的自然尺寸（CSS px）：块级自然宽度模式靠它决定视口要多宽 */
        val widthCss: Int,
        val heightCss: Int,
        val error: String?,
    )

    /** 缓存命中直接复用，不碰 WebView */
    fun cached(request: Request): Result? = cache.get(request.cacheKey())

    /**
     * 调试用：把渲染后的 stage HTML 取回来（只有渲染实验室调用）。
     *
     * 出问题时「图里少了根号/多出个小点」这类现象光看位图查不出原因，
     * 必须看 KaTeX 到底生成了什么 DOM。
     */
    suspend fun dumpHtml(context: Context, request: Request): String = withContext(Dispatchers.Main) {
        mutex.withLock {
            val view = ensureWebView(context)
            val result = drawOne(view, request)
            val header = "ok=" + (result.bitmap != null) +
                " w=" + result.widthPx + " h=" + result.heightPx +
                " baseline=" + result.baselinePx + " nat=" + result.naturalWidthPx +
                " clip=" + result.clippedRight + " err=" + result.error
            val done = CompletableDeferred<String>()
            view.evaluateJavascript(
                "(function(){var s=document.getElementById('stage');" +
                    "return s?s.outerHTML:'(no stage)';})()",
            ) { value -> done.complete(value ?: "") }
            header + "\n" + svgSummary(view) + "\n" + viewportSummary(view) + "\n" +
                (withTimeoutOrNull(3_000) { done.await() } ?: "")
        }
    }

    /** 调试：视口与字号真值（排查「整页被缩放」这类问题） */
    private suspend fun viewportSummary(view: WebView): String {
        val deferred = CompletableDeferred<String>()
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('stage');var k=document.querySelector('.katex');" +
                "var cs=getComputedStyle(s);var kcs=k?getComputedStyle(k):null;" +
                "return 'VP innerW='+window.innerWidth+' dpr='+window.devicePixelRatio" +
                "+' stageFS='+cs.fontSize+' stageW='+s.getBoundingClientRect().width.toFixed(1)" +
                "+' katexFS='+(kcs?kcs.fontSize:'-')+' katexW='+(k?k.getBoundingClientRect().width.toFixed(1):'0');})()",
        ) { value -> deferred.complete(value ?: "") }
        return withTimeoutOrNull(2_000) { deferred.await() } ?: "VP ?"
    }

    /** 调试：列出超出 #stage 盒子的元素与所有 svg（排查「图里多出个大图案」「根号消失」） */
    private suspend fun svgSummary(view: WebView): String {
        val deferred = CompletableDeferred<String>()
        view.evaluateJavascript(
            "(function(){" +
                "var s=document.getElementById('stage').getBoundingClientRect();" +
                "var out=[];var list=document.querySelectorAll('body *');" +
                "for(var i=0;i<list.length;i++){var e=list[i];var r=e.getBoundingClientRect();" +
                "if(r.bottom>s.bottom+8||r.height>s.height+8){" +
                "out.push(e.tagName.toLowerCase()+'.'+(e.className||'-')+' '+Math.round(r.width)+'x'" +
                "+Math.round(r.height)+'@'+Math.round(r.top)+','+Math.round(r.left));}}" +
                "return 'GEO stage '+Math.round(s.width)+'x'+Math.round(s.height)+' | '+out.slice(0,10).join(' || ');})()",
        ) { value -> deferred.complete(value ?: "") }
        return withTimeoutOrNull(2_000) { deferred.await() } ?: "GEO ?"
    }

    /**
     * 渲染一张图。必须在主线程调用（内部切到 Main）。
     *
     * @return 成功给出位图；失败给出 [Result.error]，调用方负责降级显示。
     */
    suspend fun render(context: Context, request: Request, useCache: Boolean = true): Result {
        if (useCache) cache.get(request.cacheKey())?.let { return it }
        inFlight.incrementAndGet()
        try {
            return withContext(Dispatchers.Main) {
                mutex.withLock {
                    // 双检：等锁期间别人可能已经渲染好了
                    if (useCache) cache.get(request.cacheKey())?.let { return@withLock it }
                    val view = ensureWebView(context)
                    val result = drawOne(view, request)
                    if (result.bitmap == null || !useCache) {
                        result
                    } else {
                        cache.put(request.cacheKey(), result)
                        result
                    }
                }
            }
        } finally {
            inFlight.decrementAndGet()
        }
    }

    /** 还有多少张公式/Mermaid 在排队或渲染中（分享长图靠它判断"真的画完了"） */
    fun inFlightCount(): Int = inFlight.get()

    private fun Request.cacheKey(): String = listOf(
        kind.wire, display, fontPx, widthPx, colorHex, dark, naturalWidth, source,
    ).joinToString("\u0001")

    private suspend fun drawOne(view: WebView, request: Request): Result {
        val id = nextId++
        // WebView 的 CSS 像素 = 物理像素 / density（devicePixelRatio），所以下发前要折成 CSS px，
        // 拿回来的尺寸再乘回 density。漏了这一步就会出现「公式被放大 3 倍、只画出前两个符号」。
        val scale = view.resources.displayMetrics.density
        val cssWidth = (request.widthPx / scale).toInt().coerceIn(200, MAX_SIDE_PX)
        val cssFont = (request.fontPx / scale).coerceAtLeast(6f)
        layout(view, request.widthPx.coerceIn(1, MAX_SIDE_PX), INITIAL_PAGE_HEIGHT_PX)
        delay(PAGE_RESIZE_SETTLE_MS)
        val payload = JSONObject().apply {
            put("id", id)
            put("kind", request.kind.wire)
            put("fontPx", cssFont)
            put("widthPx", cssWidth)
            put("color", request.colorHex)
            put("theme", if (request.dark) "dark" else "light")
            put("display", request.display)
            put("natural", request.naturalWidth)
            if (request.kind == Kind.MERMAID) put("code", request.source) else put("latex", request.source)
        }.toString()
        val bridged = evaluateAndAwait(view, "window.tcRender(" + JSONObject.quote(payload) + ")")
            ?: return Result(null, error = "渲染超时")
        if (!bridged.ok) return Result(bitmap = null, error = bridged.error)

        // 出图 + **按像素裁到真正的墨迹框**。
        //
        // 为什么不按 DOM 尺寸裁：KaTeX 的盒子比墨迹小（根号尾巴、上下标、大括号都画在盒外，
        // 实测块级公式的盒子只有真实高度的一半，矩阵右边少 20px）；而「取所有后代的并集」
        // 又会被 `.pstrut` 这类 3em 高的隐形占位撑大。只有扫像素能拿到「真正有内容」的矩形。
        // 视口如果装不下，墨迹会贴边 —— 那就放大重画，最多三轮。
        var viewW = request.widthPx.coerceIn(1, MAX_SIDE_PX)
        var viewH = INITIAL_PAGE_HEIGHT_PX
        // 自然宽度模式：直接按量到的内容宽出图，宽公式才不会被切；一轮到位，不用试错放大
        if (request.naturalWidth) {
            // +12 是页面留白（render.html 的 body padding），按 CSS px 折成物理像素
            val natural = ((bridged.widthCss + 12) * scale).toInt().coerceIn(1, MAX_SIDE_PX)
            if (natural > viewW) viewW = natural
        }
        var laidW = request.widthPx.coerceIn(1, MAX_SIDE_PX)
        var laidH = INITIAL_PAGE_HEIGHT_PX
        var full: Bitmap? = null
        var bounds: IntArray? = null
        // 块级、非自然宽度模式下墨迹贴右边缘 = 被容器切掉了，调用方据此切自然宽度重画
        var clippedRight = false
        for (round in 0 until 3) {
            if (viewW != laidW || viewH != laidH) {
                layout(view, viewW, viewH)
                laidW = viewW
                laidH = viewH
                // 改视口会让 WebView 重新排版并重新光栅，必须等一次 JS 往返再画，
                // 否则 draw() 拿到的是旧尺寸的半张图（实测 `\frac{1}{2}` 只剩一个分母）
                evaluateAndAwait(view, "window.tcMeasure($id)")
                delay(DRAW_SETTLE_MS)
            }
            awaitVisualState(view)
            val bitmap = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0)
            view.draw(Canvas(bitmap))
            val found = inkBounds(bitmap)
            full?.recycle()
            full = bitmap
            bounds = found
            if (found == null) break
            val hitBottom = found[1] + found[3] >= viewH - 2
            val inkRight = found[0] + found[2]
            if (round == 0 && request.display && !request.naturalWidth) {
                clippedRight = inkRight >= viewW - 2
            }
            // 非自然宽度的块级公式靠「容器宽 + text-align:center」居中，宽度不能动；
            // 行内公式和自然宽度块级公式可以把视口撑宽去够完整的墨迹
            val canGrowW = !request.display || request.naturalWidth
            val hitRight = canGrowW && inkRight >= viewW - 2
            if (!hitBottom && !hitRight) break
            if (hitBottom) viewH = (viewH * 2).coerceAtMost(MAX_SIDE_PX)
            if (hitRight) viewW = (viewW + 400).coerceAtMost(MAX_SIDE_PX)
        }
        val source = full ?: return Result(null, error = "渲染失败")
        val crop = cropRect(request.display, request.naturalWidth, bounds, source.width, source.height)
        val (cropX, cropY, cropW, cropH) = crop.toList()
        val bitmap = if (cropX == 0 && cropY == 0 && cropW == source.width && cropH == source.height) {
            source
        } else {
            // createBitmap 可能与原图共享像素，拷一份再把整屏释放掉
            val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
            val copy = cropped.copy(Bitmap.Config.ARGB_8888, false) ?: cropped
            if (copy !== cropped) cropped.recycle()
            source.recycle()
            copy
        }
        val baselinePx = (bridged.baselinePx * scale).toInt() - cropY
        return Result(
            bitmap = bitmap.asImageBitmap(),
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            baselinePx = baselinePx.coerceIn(0, bitmap.height),
            naturalWidthPx = (bridged.widthCss * scale).toInt().coerceAtLeast(bitmap.width),
            clippedRight = clippedRight,
        )
    }

    /** 找到位图里「真正有内容」的矩形（全透明 = 空）。找不到返回 null。 */
    private fun inkBounds(bitmap: Bitmap): IntArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val row = IntArray(w)
        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var rowMin = -1
            var rowMax = -1
            for (x in 0 until w) {
                // 只看 alpha：页面背景透明，墨迹以外全是 0
                if ((row[x] ushr 24) != 0) {
                    if (rowMin < 0) rowMin = x
                    rowMax = x
                }
            }
            if (rowMin >= 0) {
                if (rowMin < minX) minX = rowMin
                if (rowMax > maxX) maxX = rowMax
                if (y < minY) minY = y
                maxY = y
            }
        }
        if (maxX < 0) return null
        return intArrayOf(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /**
     * 等 WebView 真正画完这一帧。
     *
     * **踩坑**：`katex.render()` 之后 DOM 已经有了，但 WebView 的光栅是分块的，
     * `draw()` 可能拿到上一帧的旧内容 —— 实测多行 `aligned` 块只画出一条 8px 的横带。
     * `postVisualStateCallback` 是官方给的「视觉状态已就绪」信号，比 sleep 靠谱。
     */
    private suspend fun awaitVisualState(view: WebView) {
        val done = CompletableDeferred<Boolean>()
        view.postVisualStateCallback(
            nextId++.toLong(),
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    done.complete(true)
                }
            },
        )
        withTimeoutOrNull(VISUAL_STATE_TIMEOUT_MS) { done.await() }
    }

    /** 执行一段 JS 并等它回调（渲染结果或补量结果），超时就当失败 */    private suspend fun evaluateAndAwait(view: WebView, script: String): BridgeResult? {
        val deferred = CompletableDeferred<BridgeResult>()
        pending = deferred
        view.evaluateJavascript(script, null)
        val result = withTimeoutOrNull(RENDER_TIMEOUT_MS) { deferred.await() }
        pending = null
        return result
    }

    private fun layout(view: WebView, width: Int, height: Int) {
        view.layoutParams?.let { lp ->
            if (lp.width != width || lp.height != height) {
                lp.width = width
                lp.height = height
                view.layoutParams = lp   // 已 attach：让父容器下一帧按新尺寸摆放
            }
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    /** Compose 的 LocalContext 常常是 ContextThemeWrapper，往上找 Activity 才能挂窗口 */
    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    @Suppress("SetJavaScriptEnabled")
    private suspend fun ensureWebView(context: Context): WebView {
        webView?.let { view ->
            if (pageLoaded) return view
            loadPage(view)
            return view
        }
        val host = context.findActivity() ?: context.applicationContext
        val view = WebView(host)
        // 软件渲染：draw() 到 Bitmap 的唯一可靠路径（硬件层需要真实 Surface，离屏会画出半张图）
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // **必须为 true**：false 时 WebView 会忽略渲染页里的 `width=device-width`，
            // 按 980 CSS px 的布局宽度排版再整体缩放 —— 宽公式会被压成 1/3 大小糊成一团，
            // 而且和我们「CSS px = 物理 px / density」的换算对不上。
            useWideViewPort = true
            loadWithOverviewMode = false
            textZoom = 100
        }
        view.addJavascriptInterface(Bridge { result -> pending?.complete(result) }, "TcBridge")
        view.webViewClient = AssetClient(context.applicationContext)
        layout(view, 360, 200)   // 先给个尺寸，页面要用视口宽排版
        webView = view
        attachOffscreen(host, view)
        loadPage(view)
        return view
    }

    /**
     * 把不可见的 WebView 挂进窗口、挪到屏幕外。
     *
     * **踩坑记录**：完全不 attach 的 WebView 只会在默认视口（约 980 CSS px）里光栅化一次，
     * 之后无论怎么改尺寸，`draw()` 拿到的都是那块旧内容 —— 实测块级公式只画出 `∫ ∞` 两个残片。
     * 只有挂进窗口（仍然离屏、用户看不见）才能得到完整的软件光栅。
     */
    private fun attachOffscreen(context: Context, view: WebView) {
        val activity = context as? Activity ?: return
        val root = activity.window?.decorView as? ViewGroup ?: return
        val width = view.layoutParams?.width ?: 360
        val height = view.layoutParams?.height ?: 200
        root.addView(view, ViewGroup.LayoutParams(width, height))
        view.translationX = -100_000f   // 挪出可视区域，绝不闪屏
        view.translationY = -100_000f
    }

    private suspend fun loadPage(view: WebView) {
        val done = CompletableDeferred<Boolean>()
        loading = done
        view.loadUrl(PAGE_URL)
        withTimeoutOrNull(LOAD_TIMEOUT_MS) { done.await() }
        pageLoaded = true
        loading = null
    }

    private class Bridge(private val onResult: (BridgeResult) -> Unit) {
        @JavascriptInterface
        fun onRender(id: Int, ok: Int, baselinePx: Int, widthCss: Int, heightCss: Int, error: String) {
            onResult(
                BridgeResult(
                    ok = ok == 1,
                    baselinePx = baselinePx,
                    widthCss = widthCss,
                    heightCss = heightCss,
                    error = error.takeIf { it.isNotBlank() },
                ),
            )
        }

        @JavascriptInterface
        fun onPageReady() {
            TcRenderHost.onPageReady()
        }
    }

    private class AssetClient(private val context: Context) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != HOST) return null
            val path = url.path.orEmpty().removePrefix("/assets/")
            return try {
                WebResourceResponse(
                    mimeOf(path),
                    if (path.endsWith(".html")) "utf-8" else null,
                    200,
                    "OK",
                    mapOf("Cache-Control" to "no-store"),
                    context.assets.open(path),
                )
            } catch (e: Exception) {
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    404,
                    "Not Found",
                    emptyMap(),
                    ByteArrayInputStream("missing asset: $path".toByteArray()),
                )
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript("(function(){ if(window.TcBridge&&TcBridge.onPageReady) TcBridge.onPageReady(); })()", null)
        }

        private fun mimeOf(path: String): String = when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    private fun onPageReady() {
        loading?.complete(true)
    }

    private const val HOST = "tc.local"
    private const val PAGE_URL = "https://$HOST/assets/tc/render.html"
    private const val RENDER_TIMEOUT_MS = 6_000L
    private const val LOAD_TIMEOUT_MS = 8_000L
    private const val RESIZE_SETTLE_MS = 48L

    /** 收好视口到 draw 之间还要等的时长：重排 + 重光栅不是一帧能完成的 */
    private const val DRAW_SETTLE_MS = 64L

    /** 等 WebView 画出当前帧的上限 */
    private const val VISUAL_STATE_TIMEOUT_MS = 2_000L
    private const val PAGE_RESIZE_SETTLE_MS = 32L

    /**
     * 渲染时页面的初始高度（物理 px，约 290dp）。**不要开太大**：
     * 软件光栅要把整块视口重画，4000px 高时每次渲染慢一倍以上。
     * 真装不下也没关系 —— 量完盒子后会把视口收到实际大小，再补量。
     */
    private const val INITIAL_PAGE_HEIGHT_PX = 800

    /** 页面四周留白见 render.html 的 `body { padding }`（12 CSS px），墨迹探出盒子时靠它兜住 */

    /** 单边像素上限：防止极端公式把位图撑爆（超过就由调用方降级） */
    private const val MAX_SIDE_PX = 8_000
}
