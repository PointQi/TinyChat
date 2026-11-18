package io.tinychat.markdown

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RenderCropTest {

    @Test
    fun `行内公式按墨迹框裁`() {
        val crop = cropRect(false, false, intArrayOf(40, 12, 200, 60), 1080, 800)
        assertArrayEquals(intArrayOf(40, 12, 200, 60), crop)
    }

    @Test
    fun `块级公式横向保留整宽以保住居中`() {
        val crop = cropRect(true, false, intArrayOf(300, 12, 400, 60), 1080, 800)
        assertArrayEquals(intArrayOf(0, 12, 1080, 60), crop)
    }

    @Test
    fun `自然宽度的块级公式裁到墨迹（要横向滚动）`() {
        val crop = cropRect(true, true, intArrayOf(300, 12, 400, 60), 1080, 800)
        assertArrayEquals(intArrayOf(300, 12, 400, 60), crop)
    }

    @Test
    fun `没有墨迹时给 1x1 透明图而不是 0 尺寸`() {
        assertArrayEquals(intArrayOf(0, 0, 1, 1), cropRect(false, false, null, 1080, 800))
    }

    @Test
    fun `墨迹贴边时裁剪不会越界`() {
        val crop = cropRect(false, false, intArrayOf(1000, 790, 200, 40), 1080, 800)
        assertArrayEquals(intArrayOf(1000, 790, 80, 10), crop)
    }

    @Test
    fun `块级公式纵向贴底也不越界`() {
        val crop = cropRect(true, false, intArrayOf(0, 780, 1080, 40), 1080, 800)
        assertArrayEquals(intArrayOf(0, 780, 1080, 20), crop)
    }
}
