package io.tinychat.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.concurrent.atomic.AtomicBoolean

private val loaderReady = AtomicBoolean(false)

/**
 * 站点 favicon（M2-8）：圆形，加载失败/离线时退回首字母色块。
 *
 * DeepSeek 的图标 CDN（`https://cdn.deepseek.com/site-icons/<domain>`）**没有扩展名**、
 * `Content-Type: binary/octet-stream`，靠 Coil 的内容嗅探解码，所以必须走网络 fetcher。
 */
@Composable
fun TcFavicon(
    url: String?,
    fallbackLetter: String,
    modifier: Modifier = Modifier,
    size: Int = 16,
) {
    val palette = TcTheme.palette
    val context = LocalContext.current

    if (!url.isNullOrBlank()) ensureImageLoader()

    val letter = fallbackLetter.take(1).uppercase()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(palette.bgLayer2),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            FallbackLetter(letter, size, palette.labelSecondary)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { FallbackLetter(letter, size, palette.labelTertiary) },
                error = { FallbackLetter(letter, size, palette.labelSecondary) },
            )
        }
    }
}

@Composable
private fun FallbackLetter(letter: String, size: Int, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = letter,
        style = TextStyle(
            fontSize = (size * 0.5f).sp,
            // 行高与字号取同一个值 + 去掉字体内部留白，字才会正落在圆心；
            // 之前 14dp 的圆配默认行高，字形下沿被圆裁掉，看起来像字飞出了圆
            lineHeight = (size * 0.5f).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            color = color,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        softWrap = false,
    )
}

private fun ensureImageLoader() {
    if (!loaderReady.compareAndSet(false, true)) return
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
    }
}
