package io.tinychat.markdown

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RenderCropTest {

    @Test
    fun `行内公式按墨迹框裁`() {
        val crop = cropRect(display = false, ink = intArrayOf(40, 12, 200, 60), viewW = 1080, viewH = 800)
        assertArrayEquals(intArrayOf(40, 12, 200, 60), crop)
    }

    @Test
    fun `块级公式横向保留整宽以保住居中`() {
        val crop = cropRect(display = true, ink = intArrayOf(300, 12, 400, 60), viewW = 1080, viewH = 800)
        assertArrayEquals(intArrayOf(0, 12, 1080, 60), crop)
    }

    @Test
    fun `没有墨迹时给 1x1 透明图而不是 0 尺寸`() {
        val crop = cropRect(display = false, ink = null, viewW = 1080, viewH = 800)
        assertArrayEquals(intArrayOf(0, 0, 1, 1), crop)
    }

    @Test
    fun `墨迹贴边时裁剪不会越界`() {
        val crop = cropRect(display = false, ink = intArrayOf(1000, 790, 200, 40), viewW = 1080, viewH = 800)
        assertArrayEquals(intArrayOf(1000, 790, 80, 10), crop)
    }

    @Test
    fun `块级公式纵向贴底也不越界`() {
        val crop = cropRect(display = true, ink = intArrayOf(0, 780, 1080, 40), viewW = 1080, viewH = 800)
        assertArrayEquals(intArrayOf(0, 780, 1080, 20), crop)
    }
}
