package io.tinychat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcTheme
import kotlin.math.roundToInt

/**
 * 侧栏抽屉的外壳（P-030）：遮罩 + 位移 + 圆角 + 拖拽。
 *
 * 会话列表与用量与费用共用它，所以两个侧栏的遮罩浓度、圆角、位移规则、
 * 「拖一半就是一半」的手感一定一致 —— 谁也不会因为各写一份而慢慢漂走。
 *
 * [progress] 是唯一的事实来源：0 收在屏幕外，1 完全展开，中间值就是拖到一半。
 * 它由调用方写（拖拽时手指直接改，松手后补一段固定时长的动画），本组件只管画。
 */
@Composable
fun TcSideSheet(
    progress: Float,
    /** true = 从左侧滑入（会话列表）；false = 从右侧滑入（用量与费用） */
    fromStart: Boolean,
    width: Dp,
    /** 读屏念出来的面板名，如「会话列表」 */
    title: String,
    onDismiss: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (dxPx: Float) -> Unit,
    onDragEnd: (velocityDp: Float) -> Unit,
    /** 内容是否自带纵向滚动（用量与费用需要，会话列表自己用 LazyColumn，不能再包一层） */
    scrollable: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    // 收起时也整块留在树上、只是推到屏幕外：首次组合 + 测量的开销必须先付掉。
    // 之前是 progress 一过 0 才开始组合，结果是手指一划、主线程先去组装整个列表，
    // 200ms 之后抽屉才动 —— 看起来就像「划完要等一下它才出来」。
    var warmed by remember { mutableStateOf(progress > 0f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        warmed = true
    }
    if (progress <= 0f && !warmed) return

    val palette = TcTheme.palette
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val density = LocalDensity.current.density
    // draggable 的状态是 remember 出来的：直接闭包会一直用首次那个回调
    val dragDelta = rememberUpdatedState(onDragDelta)
    val dragState = rememberDraggableState { delta -> dragDelta.value(delta) }
    val offsetX = (if (fromStart) -1f else 1f) * (1f - progress) * widthPx
    val shape = if (fromStart) {
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    }

    Box(Modifier.fillMaxSize()) {
        // 收起时不要这张遮罩：它全屏可点，会把聊天页的点击全吃掉
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * progress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "关闭",
                        onClick = onDismiss,
                    ),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            // 位移单独放这一层：offset 的 lambda 每帧读进度，不触发重组
            Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
                Column(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(palette.bg)
                        .semantics {
                            paneTitle = title
                            // 收在屏幕外时对读屏也要藏起来，否则焦点会跑进看不见的抽屉
                            if (progress <= 0f) invisibleToUser()
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = dragState,
                            onDragStarted = { onDragStart() },
                            onDragStopped = { velocity -> onDragEnd(velocity / density) },
                        )
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(contentPadding),
                    content = content,
                )
            }
        }
    }
}
