package io.tinychat

import android.animation.ValueAnimator
import android.os.Build
import android.view.animation.PathInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreenViewProvider

/** Hands the system icon off to the measured greeting logo, in window coordinates. */
internal class SplashLanding {
    private var logoCoordinates: LayoutCoordinates? = null
    private var splashView: SplashScreenViewProvider? = null
    private var animation: ValueAnimator? = null
    private var greetingAlpha by mutableFloatStateOf(1f)
    private var finished = false

    val logoModifier: Modifier = Modifier
        .graphicsLayer { alpha = greetingAlpha }
        .onGloballyPositioned { coordinates ->
            if (finished) return@onGloballyPositioned
            logoCoordinates = coordinates
            moveToGreeting()
        }

    fun start(splash: SplashScreenViewProvider) {
        if (finished) {
            splash.remove()
            return
        }
        splashView = splash
        moveToGreeting()
        // A restored route or an unusually small window may have no greeting to land on.
        if (animation == null) splash.view.postOnAnimation {
            if (animation == null) finish()
        }
    }

    private fun moveToGreeting() {
        if (animation != null) return
        val splash = splashView ?: return
        if (logoCoordinates?.windowRect() == null) return
        // On recent Android versions an OEM may send a splash with only a background.
        val icon = runCatching { splash.iconView }.getOrNull() ?: return
        if (icon.width == 0 || icon.height == 0) return

        val location = IntArray(2).also(icon::getLocationInWindow)
        val from = Offset(location[0] + icon.width / 2f, location[1] + icon.height / 2f)
        // AndroidX clips the legacy splash to the center two thirds of the icon view.
        val visibleFraction = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 2f / 3f else 1f
        val visibleWidth = icon.width * visibleFraction
        val visibleHeight = icon.height * visibleFraction
        icon.pivotX = icon.width / 2f
        icon.pivotY = icon.height / 2f
        val background = splash.view.background?.mutate()
        val originalBackgroundAlpha = background?.alpha ?: 255
        val originalIconAlpha = icon.alpha
        greetingAlpha = 0f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460
            interpolator = PathInterpolator(0.35f, 0f, 0.25f, 1f)
            addUpdateListener { frame ->
                val progress = frame.animatedFraction
                // The final position may still change while the saved settings load.
                val destination = logoCoordinates?.windowRect()
                if (destination == null) {
                    frame.cancel()
                    return@addUpdateListener
                }
                icon.translationX = (destination.center.x - from.x) * progress
                icon.translationY = (destination.center.y - from.y) * progress
                icon.scaleX = 1f + (destination.width / visibleWidth - 1f) * progress
                icon.scaleY = 1f + (destination.height / visibleHeight - 1f) * progress

                background?.alpha = (originalBackgroundAlpha * (1f - progress)).toInt()
                val reveal = ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f)
                icon.alpha = originalIconAlpha * (1f - reveal)
                greetingAlpha = reveal
            }
            doOnEnd { finish() }
        }
        animation = animator
        animator.start()
    }

    fun dispose() {
        animation?.cancel()
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        animation = null
        greetingAlpha = 1f
        logoCoordinates = null
        splashView?.remove()
        splashView = null
    }
}

private fun LayoutCoordinates.windowRect(): Rect? {
    if (!isAttached || size.width == 0 || size.height == 0) return null
    val topLeft = positionInWindow()
    if (!topLeft.x.isFinite() || !topLeft.y.isFinite()) return null
    return Rect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height)
}
