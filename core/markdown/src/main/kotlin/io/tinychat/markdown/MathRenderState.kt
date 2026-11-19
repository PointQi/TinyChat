package io.tinychat.markdown

/**
 * 公式/Mermaid 的渲染进度（给"分享长图"用）。
 *
 * `TcRenderHost` 是 internal（它是实现细节，不该被业务层直接拿去渲染），
 * 但分享时确实需要知道「还有几张没画完」，否则会把「公式渲染中」的占位块拍进图里。
 * 所以只暴露一个只读计数。
 */
object MathRenderState {
    /** 正在渲染或排队的张数；0 = 当前没有待出图的内容 */
    fun inFlight(): Int = TcRenderHost.inFlightCount()
}
