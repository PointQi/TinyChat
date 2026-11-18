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
    )

    enum class Kind(val wire: String) { MATH("math"), MERMAID("mermaid") }

    /** 结果。[bitmap] 为空表示失败（[error] 里有原因），调用方退回源码/占位。 */
    data class Result(
        val bitmap: ImageBitmap?,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
        val error: String? = null,
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

    private data class BridgeResult(val ok: Boolean, val widthPx: Int, val heightPx: Int, val error: String?)

    /** 缓存命中直接复用，不碰 WebView */
    fun cached(request: Request): Result? = cache.get(request.cacheKey())

    /**
     * 渲染一张图。必须在主线程调用（内部切到 Main）。
     *
     * @return 成功给出位图；失败给出 [Result.error]，调用方负责降级显示。
     */
    suspend fun render(context: Context, request: Request, useCache: Boolean = true): Result {
        if (useCache) cache.get(request.cacheKey())?.let { return it }
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
    }

    private fun Request.cacheKey(): String = listOf(
        kind.wire, display, fontPx, widthPx, colorHex, dark, source,
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
            if (request.kind == Kind.MERMAID) put("code", request.source) else put("latex", request.source)
        }.toString()
        val bridged = evaluateAndAwait(view, "window.tcRender(" + JSONObject.quote(payload) + ")")
        if (bridged == null) return Result(null, error = "渲染超时")
        if (!bridged.ok) return Result(null, bridged.widthPx, bridged.heightPx, bridged.error)

        // 回传的是 CSS px，折回物理像素；+2px 防亚像素把笔画切掉
        val width = (bridged.widthPx * scale).toInt().coerceIn(1, MAX_SIDE_PX)
        var height = ((bridged.heightPx + 2) * scale).toInt().coerceIn(1, MAX_SIDE_PX)

        // 量完再改尺寸会让页面重排一次，公式换行后可能比第一次更高（实测被裁掉半行）。
        // 所以改完尺寸**再量一次**，只增不减，最多补两轮。
        for (round in 0 until 2) {
            layout(view, width, height)
            delay(RESIZE_SETTLE_MS)
            val measured = evaluateAndAwait(view, "window.tcMeasure($id)") ?: break
            val needed = ((measured.heightPx + 2) * scale).toInt()
            if (needed <= height) break
            height = needed.coerceIn(1, MAX_SIDE_PX)
        }
        layout(view, width, height)
        delay(RESIZE_SETTLE_MS)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0)
        view.draw(Canvas(bitmap))
        return Result(bitmap.asImageBitmap(), width, height)
    }

    /** 执行一段 JS 并等它回调（渲染结果或补量结果），超时就当失败 */
    private suspend fun evaluateAndAwait(view: WebView, script: String): BridgeResult? {
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
            loadWithOverviewMode = false
            useWideViewPort = false
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
        fun onRender(id: Int, ok: Int, widthPx: Int, heightPx: Int, error: String) {
            onResult(BridgeResult(ok == 1, widthPx, heightPx, error.takeIf { it.isNotBlank() }))
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
    private const val PAGE_RESIZE_SETTLE_MS = 32L

    /** 渲染时页面的初始高度：比任何公式都高，量完再缩到实际高度 */
    private const val INITIAL_PAGE_HEIGHT_PX = 4_000

    /** 单边像素上限：防止极端公式把位图撑爆（超过就由调用方降级） */
    private const val MAX_SIDE_PX = 8_000
}
