package io.tinychat

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventHistory
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.tinychat.designsystem.TcMotion

private val PageSlide = tween<IntOffset>(TcMotion.Page, easing = TcMotion.Standard)
private val PredictivePageSlide = tween<IntOffset>(TcMotion.Page, easing = LinearEasing)

@Composable
internal fun PageNavHost(
    navController: NavHostController,
    startDestination: String,
    builder: NavGraphBuilder.() -> Unit,
) {
    val parentDispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current)
        .navigationEventDispatcher
    val input = remember(parentDispatcher) { PageBackInput() }
    DisposableEffect(parentDispatcher, input) {
        input.navigationEventDispatcher.addInput(input)
        parentDispatcher.addHandler(input.systemHandler)
        onDispose {
            input.systemHandler.remove()
            input.navigationEventDispatcher.dispose()
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides input) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().onSizeChanged { input.pageWidthPx = it.width },
            enterTransition = { slideInHorizontally(PageSlide) { it } },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideOutHorizontally(PageSlide) { it } },
            predictivePopEnterTransition = { _ -> EnterTransition.None },
            predictivePopExitTransition = { _ -> slideOutHorizontally(PredictivePageSlide) { it } },
            builder = builder,
        )
    }
}

// Keep NavHost's handler and descendant BackHandlers together. The system still decides
// when to complete or cancel the gesture; only the progress sent to the page changes.
private class PageBackInput : NavigationEventInput(), NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    var pageWidthPx = 0

    val systemHandler = object : NavigationEventHandler<NavigationEventInfo>(
        initialInfo = NavigationEventInfo.None,
        isBackEnabled = false,
        isForwardEnabled = false,
    ) {
        private var startTouchX = 0f

        override fun onBackStarted(event: NavigationEvent) {
            startTouchX = event.touchX
            dispatchOnBackStarted(event.withPageProgress(startTouchX, pageWidthPx))
        }

        override fun onBackProgressed(event: NavigationEvent) {
            dispatchOnBackProgressed(event.withPageProgress(startTouchX, pageWidthPx))
        }

        override fun onBackCancelled() = dispatchOnBackCancelled()

        override fun onBackCompleted() = dispatchOnBackCompleted()
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        systemHandler.isBackEnabled = hasEnabledHandlers
    }

    override fun onHistoryChanged(history: NavigationEventHistory) {
        val entries = history.mergedHistory
        val currentIndex = history.currentIndex
        systemHandler.setInfo(
            currentInfo = entries.getOrNull(currentIndex) ?: NavigationEventInfo.None,
            backInfo = entries.take(currentIndex.coerceAtLeast(0)),
            forwardInfo = entries.drop(currentIndex + 1),
        )
    }
}

private fun NavigationEvent.withPageProgress(startTouchX: Float, pageWidthPx: Int): NavigationEvent =
    NavigationEvent(
        swipeEdge = swipeEdge,
        progress = pageBackProgress(this, startTouchX, pageWidthPx),
        touchX = touchX,
        touchY = touchY,
        frameTimeMillis = frameTimeMillis,
    )

internal fun pageBackProgress(event: NavigationEvent, startTouchX: Float, pageWidthPx: Int): Float {
    val distance = when (event.swipeEdge) {
        NavigationEvent.EDGE_LEFT -> event.touchX - startTouchX
        NavigationEvent.EDGE_RIGHT -> startTouchX - event.touchX
        else -> Float.NaN
    }
    if (pageWidthPx <= 0 || !distance.isFinite()) {
        return if (event.progress.isFinite()) event.progress.coerceIn(0f, 1f) else 0f
    }
    return (distance / pageWidthPx).coerceIn(0f, 1f)
}
