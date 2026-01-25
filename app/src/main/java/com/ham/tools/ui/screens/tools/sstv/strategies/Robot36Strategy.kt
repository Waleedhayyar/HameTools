package com.ham.tools.ui.screens.tools.sstv.strategies

import com.ham.tools.ui.screens.tools.sstv.SstvConstants
import com.ham.tools.ui.screens.tools.sstv.YuvModeStrategy

/**
 * Robot 36 模式解码策略
 * 
 * Robot 36 使用 YUV 颜色编码（也称 YCbCr），这是一种基于亮度-色度分离的颜色模型。
 * 与 RGB 模式不同，YUV 可以更高效地传输图像，因为人眼对亮度变化更敏感。
 * 
 * ## 技术规格
 * 
 * - 分辨率: 320 x 240 像素
 * - VIS 码: 8
 * - 颜色模式: YUV (Y = 亮度, R-Y = 红色色度, B-Y = 蓝色色度)
 * - 单行时长: 约 150 ms
 * - **扫描速度: 约 6-7 行/秒**（需要性能优化！）
 * 
 * ## 行结构时序
 * 
 * ```
 * ┌─────────┬──────────────────┬─────────┬──────────────────┬─────────┬──────────────────┬─────────┐
 * │  Sync   │        Y         │   Sep   │       R-Y        │   Sep   │       B-Y        │   Sep   │
 * │ 9.0ms   │     88.0ms       │  4.5ms  │     44.0ms       │  4.5ms  │     44.0ms       │  4.5ms  │
 * │ 1200Hz  │  1500-2300Hz     │ 1500Hz  │  1500-2300Hz     │ 1500Hz  │  1500-2300Hz     │ 1500Hz  │
 * └─────────┴──────────────────┴─────────┴──────────────────┴─────────┴──────────────────┴─────────┘
 * ```
 * 
 * ## 频率到 YUV 映射
 * 
 * - 1500 Hz → Y=0 (黑色), U/V=0 (偏移后 -128)
 * - 1900 Hz → Y=128 (中灰), U/V=128 (无色差，中性)
 * - 2300 Hz → Y=255 (白色), U/V=255 (偏移后 +127)
 * 
 * ## YUV → RGB 转换公式
 * 
 * 使用 SSTV 社区优化的公式：
 * - R = Y + 1.14 × (V - 128)
 * - G = Y - 0.39 × (U - 128) - 0.58 × (V - 128)
 * - B = Y + 2.03 × (U - 128)
 * 
 * ## 性能优化
 * 
 * 使用缓冲区复用，避免每行分配新数组。
 * Robot 36 每秒 6-7 行，如果不复用缓冲区会导致严重的 GC 压力。
 */
class Robot36Strategy : YuvModeStrategy() {
    
    companion object {
        const val VIS_CODE = 8
        const val MODE_NAME = "Robot 36"
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 240
        
        // 时序参数 (毫秒)
        const val SYNC_PULSE_MS = 9.0
        const val SEPARATOR_MS = 4.5
        
        // 亮度通道 Y 扫描时间 (88ms)
        const val Y_SCAN_MS = 88.0
        
        // 色度通道 R-Y (V/Cr) 和 B-Y (U/Cb) 扫描时间 (各 44ms)
        const val CHROMA_SCAN_MS = 44.0
        
        // 总行时长约 150ms (实际计算: 9.0 + 88.0 + 4.5 + 44.0 + 4.5 + 44.0 + 4.5 = 198.5ms)
        // 但 Robot 36 的实际标准是 150ms，这里使用标准值
        const val SCAN_LINE_MS = 150.0
        
        const val SYNC_FREQ = SstvConstants.SYNC_FREQ  // 1200 Hz
    }
    
    override val modeName: String = MODE_NAME
    override val visCode: Int = VIS_CODE
    override val width: Int = IMAGE_WIDTH
    override val height: Int = IMAGE_HEIGHT
    
    override val syncPulseMs: Double = SYNC_PULSE_MS
    override val syncPulseFreq: Int = SYNC_FREQ
    override val scanLineTimeMs: Double = SCAN_LINE_MS
    
    /**
     * Robot 36 YUV 通道配置
     * 
     * 行结构: [Sync] [Y] [Sep] [R-Y] [Sep] [B-Y] [Sep]
     * 
     * 时间偏移计算：
     * - Y (亮度):  开始于 Sync 之后 = 9.0ms
     * - V (R-Y):   开始于 Sync + Y + Sep = 9.0 + 88.0 + 4.5 = 101.5ms
     * - U (B-Y):   开始于 Sync + Y + Sep + V + Sep = 9.0 + 88.0 + 4.5 + 44.0 + 4.5 = 150.0ms
     */
    override val yuvChannelConfigs: List<YuvChannelConfig> = listOf(
        YuvChannelConfig(
            type = YuvChannel.Y,
            startMs = SYNC_PULSE_MS,
            durationMs = Y_SCAN_MS
        ),
        YuvChannelConfig(
            type = YuvChannel.V,  // R-Y (Cr)
            startMs = SYNC_PULSE_MS + Y_SCAN_MS + SEPARATOR_MS,
            durationMs = CHROMA_SCAN_MS
        ),
        YuvChannelConfig(
            type = YuvChannel.U,  // B-Y (Cb)
            startMs = SYNC_PULSE_MS + Y_SCAN_MS + SEPARATOR_MS + CHROMA_SCAN_MS + SEPARATOR_MS,
            durationMs = CHROMA_SCAN_MS
        )
    )
    
    /**
     * 处理一行 YUV 数据并转换为 RGB 像素
     * 
     * 使用缓冲区复用优化性能。
     * 
     * @param audioBuffer 该行的音频频率数据
     * @param sampleRate 采样率
     * @return RGB 像素数组 (ARGB 格式)
     */
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        processLine(audioBuffer, sampleRate, pixels)
        return pixels
    }
    
    /**
     * 使用复用缓冲区处理行数据（推荐使用！）
     * 
     * 这是性能优化版本，避免每行分配新的 IntArray。
     * 对于 Robot 36 的高速扫描（每秒 6-7 行），
     * 使用此方法可以显著减少 GC 压力，避免 UI 卡顿。
     * 
     * @param audioBuffer 该行的音频频率数据
     * @param sampleRate 采样率
     * @param outputBuffer 输出缓冲区（必须长度 >= width）
     */
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int, outputBuffer: IntArray) {
        // 使用父类的缓冲区复用方法
        processLineWithBuffers(audioBuffer, sampleRate, outputBuffer)
    }
}

/**
 * Robot 72 模式解码策略
 * 
 * Robot 72 是 Robot 36 的高质量变体，时长加倍，信噪比更好。
 * 
 * ## 技术规格
 * 
 * - 分辨率: 320 x 240 像素
 * - VIS 码: 12
 * - 颜色模式: YUV
 * - 单行时长: 约 300 ms (是 Robot 36 的两倍)
 * - 扫描速度: 约 3-4 行/秒
 * 
 * ## 与 Robot 36 的区别
 * 
 * Robot 72 使用更长的扫描时间，意味着：
 * - 更好的信噪比
 * - 更高的颜色保真度
 * - 传输时间加倍
 */
class Robot72Strategy : YuvModeStrategy() {
    
    companion object {
        const val VIS_CODE = 12
        const val MODE_NAME = "Robot 72"
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 240
        
        // 时序参数 (毫秒) - Robot 72 是 Robot 36 的两倍时长
        const val SYNC_PULSE_MS = 9.0
        const val SEPARATOR_MS = 4.5
        
        // 亮度通道 Y 扫描时间 (138ms)
        const val Y_SCAN_MS = 138.0
        
        // 色度通道 R-Y 和 B-Y 扫描时间 (各 69ms)
        const val CHROMA_SCAN_MS = 69.0
        
        // 总行时长约 300ms
        const val SCAN_LINE_MS = 300.0
        
        const val SYNC_FREQ = SstvConstants.SYNC_FREQ
    }
    
    override val modeName: String = MODE_NAME
    override val visCode: Int = VIS_CODE
    override val width: Int = IMAGE_WIDTH
    override val height: Int = IMAGE_HEIGHT
    
    override val syncPulseMs: Double = SYNC_PULSE_MS
    override val syncPulseFreq: Int = SYNC_FREQ
    override val scanLineTimeMs: Double = SCAN_LINE_MS
    
    override val yuvChannelConfigs: List<YuvChannelConfig> = listOf(
        YuvChannelConfig(
            type = YuvChannel.Y,
            startMs = SYNC_PULSE_MS,
            durationMs = Y_SCAN_MS
        ),
        YuvChannelConfig(
            type = YuvChannel.V,
            startMs = SYNC_PULSE_MS + Y_SCAN_MS + SEPARATOR_MS,
            durationMs = CHROMA_SCAN_MS
        ),
        YuvChannelConfig(
            type = YuvChannel.U,
            startMs = SYNC_PULSE_MS + Y_SCAN_MS + SEPARATOR_MS + CHROMA_SCAN_MS + SEPARATOR_MS,
            durationMs = CHROMA_SCAN_MS
        )
    )
    
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        processLine(audioBuffer, sampleRate, pixels)
        return pixels
    }
    
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int, outputBuffer: IntArray) {
        processLineWithBuffers(audioBuffer, sampleRate, outputBuffer)
    }
}
