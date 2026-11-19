package io.tinychat.audio

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 录音时的实时频谱（M5-2）：把 PCM 变成一排 0..1 的柱高。
 *
 * 参数与算法照 chat-demo 的 `Spectrum.tsx`（官方 MIT 实现）：
 *
 * - 100–4000Hz 人声频段，**按对数**分到各柱（低频柱子多、高频少，视觉上才均匀）；
 * - 每倍频程 +4dB 倾斜补偿：人声高频能量天然衰减，不补的话右侧柱子常年贴底；
 * - 底噪门槛 -80dB、封顶 -25dB；
 * - 先与相邻频段加权平均（填平谐波之间的空隙），再做起快落慢的非对称平滑。
 *
 * 柱子的**状态**放在这里（不是 UI 层）：一帧一次的平滑计算不需要重组，
 * 界面只读 [levels] 画高度即可。
 */
class SpectrumAnalyser(
    bands: Int,
    private val sampleRate: Int = 16_000,
    private val fftSize: Int = Fft.DEFAULT_SIZE,
    private val lowHz: Float = 100f,
    private val highHz: Float = 4_000f,
    private val floorDb: Float = -80f,
    private val ceilDb: Float = -25f,
    private val tiltDbPerOctave: Float = 4f,
) {
    private class Band(val from: Int, val to: Int, val center: Float)

    /** 竖条根数按可用宽度算（chat-demo 同款），所以要在录音前可以改。 */
    var bands: Int = bands
        private set

    private val window = Fft.blackman(fftSize)
    private val binWidth = sampleRate.toFloat() / fftSize
    private var bandRanges: List<Band> = buildBands()
    private var tilts = FloatArray(bands) { tiltOf(it) }
    private var targets = FloatArray(bands)
    private var smoothed = FloatArray(bands)

    /** 最近一帧的柱高（0..1），复用同一个数组，直接给 Canvas 读。 */
    val levels: FloatArray get() = smoothed

    /**
     * 改竖条根数（只在**录音开始前**调，录音中改会和录音线程抢数组）。
     *
     * @return 是否真的改了
     */
    fun resize(bands: Int): Boolean {
        val clamped = bands.coerceIn(4, MAX_BANDS)
        if (clamped == this.bands) return false
        this.bands = clamped
        bandRanges = buildBands()
        tilts = FloatArray(clamped) { tiltOf(it) }
        targets = FloatArray(clamped)
        smoothed = FloatArray(clamped)
        return true
    }

    fun reset() {
        targets.fill(0f)
        smoothed.fill(0f)
    }

    /** 喂一帧 PCM（长度随意，内部取末尾 [fftSize] 个样本），返回 [levels]。 */
    fun update(pcm: ShortArray): FloatArray {
        if (pcm.isEmpty()) return smoothed
        val magnitudes = Fft.magnitudes(pcm, fftSize, window)
        for (index in 0 until bands) {
            val band = bandRanges[index]
            val peak = peakOf(magnitudes, band)
            val db = if (peak <= 0f) -Float.MAX_VALUE else 20f * log10(peak) + tilts[index]
            targets[index] = ((db - floorDb) / (ceilDb - floorDb)).coerceIn(0f, 1f)
        }
        for (index in 0 until bands) {
            val previous = targets[max(0, index - 1)]
            val next = targets[min(bands - 1, index + 1)]
            val target = (previous + targets[index] * 2f + next) / 4f
            val current = smoothed[index]
            // 起几乎即时、落稍缓：既有反应速度，又不闪
            smoothed[index] += (target - current) * if (target > current) 0.8f else 0.35f
        }
        return smoothed
    }

    /** 频段窄于一个 bin 时在相邻 bin 之间插值，否则取区间内最大值。 */
    private fun peakOf(magnitudes: FloatArray, band: Band): Float {
        if (magnitudes.isEmpty()) return 0f
        if (band.to - band.from <= 1) {
            val base = min(magnitudes.size - 2, band.center.toInt()).coerceAtLeast(0)
            val weight = band.center - base
            if (base + 1 >= magnitudes.size) return magnitudes[magnitudes.size - 1]
            return magnitudes[base] * (1f - weight) + magnitudes[base + 1] * weight
        }
        var peak = 0f
        for (bin in band.from until band.to) {
            if (bin in magnitudes.indices) peak = max(peak, magnitudes[bin])
        }
        return peak
    }

    private fun buildBands(): List<Band> {
        val binCount = fftSize / 2
        val ratio = highHz / lowHz
        return (0 until bands).map { index ->
            val low = lowHz * ratio.pow(index.toFloat() / bands)
            val high = lowHz * ratio.pow((index + 1).toFloat() / bands)
            val from = min(binCount - 1, (low / binWidth).toInt())
            val to = min(binCount, max(from + 1, ceil((high / binWidth).toDouble()).toInt()))
            // 几何中心（对数轴上的中点），窄频段插值用
            Band(from = from, to = to, center = sqrt(low * high) / binWidth)
        }
    }

    /** 第 index 根柱子的高频补偿量：越靠右补得越多。 */
    private fun tiltOf(index: Int): Float =
        tiltDbPerOctave * log2(highHz / lowHz) * (index.toFloat() / bands)

    companion object {
        /** 与 chat-demo 一致：最多 60 根（2dp 宽 + 3dp 间距正好铺满手机宽度）。 */
        const val MAX_BANDS = 60
    }
}
