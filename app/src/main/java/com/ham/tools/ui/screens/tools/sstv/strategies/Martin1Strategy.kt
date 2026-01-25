package com.ham.tools.ui.screens.tools.sstv.strategies

import com.ham.tools.ui.screens.tools.sstv.ColorChannel
import com.ham.tools.ui.screens.tools.sstv.RgbModeStrategy
import com.ham.tools.ui.screens.tools.sstv.SstvConstants

/**
 * Martin 1 模式解码策略
 * 
 * Martin 1 是最常用的 SSTV 模式之一，使用 RGB 颜色编码。
 * 
 * ## 技术规格
 * 
 * - 分辨率: 320 x 256 像素
 * - VIS 码: 44
 * - 颜色顺序: G-B-R (Green → Blue → Red)
 * - 单行时长: 约 446.446 ms
 * - 扫描速度: 约 2.2 行/秒
 * 
 * ## 行结构时序
 * 
 * ```
 * ┌─────────┬────────────┬─────────┬────────────┬─────────┬────────────┬─────────┐
 * │  Sync   │   Green    │   Sep   │    Blue    │   Sep   │    Red     │   Sep   │
 * │ 4.862ms │ 146.432ms  │ 0.572ms │ 146.432ms  │ 0.572ms │ 146.432ms  │ 0.572ms │
 * │ 1200Hz  │ 1500-2300Hz│ 1500Hz  │ 1500-2300Hz│ 1500Hz  │ 1500-2300Hz│ 1500Hz  │
 * └─────────┴────────────┴─────────┴────────────┴─────────┴────────────┴─────────┘
 * ```
 * 
 * ## RGB 解码
 * 
 * Martin 1 直接使用 RGB 模式，无需 YUV 转换：
 * - 频率 1500 Hz → 亮度 0 (黑色)
 * - 频率 2300 Hz → 亮度 255 (白色)
 * 
 * 颜色顺序是 G-B-R，而不是常见的 R-G-B，这是 Martin 模式的特点。
 */
class Martin1Strategy : RgbModeStrategy() {
    
    companion object {
        const val VIS_CODE = 44
        const val MODE_NAME = "Martin 1"
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 256
        
        // 时序参数 (毫秒)
        const val SYNC_PULSE_MS = 4.862
        const val SEPARATOR_MS = 0.572
        const val COLOR_SCAN_MS = 146.432
        
        // 频率 (Hz)
        const val SYNC_FREQ = SstvConstants.SYNC_FREQ  // 1200 Hz
    }
    
    override val modeName: String = MODE_NAME
    override val visCode: Int = VIS_CODE
    override val width: Int = IMAGE_WIDTH
    override val height: Int = IMAGE_HEIGHT
    
    override val syncPulseMs: Double = SYNC_PULSE_MS
    override val syncPulseFreq: Int = SYNC_FREQ
    
    override val scanLineTimeMs: Double = 
        SYNC_PULSE_MS + 
        COLOR_SCAN_MS + SEPARATOR_MS +  // Green + Sep
        COLOR_SCAN_MS + SEPARATOR_MS +  // Blue + Sep
        COLOR_SCAN_MS + SEPARATOR_MS    // Red + Sep
    
    /**
     * Martin 1 颜色通道配置
     * 
     * 行结构: [Sync] [Green] [Sep] [Blue] [Sep] [Red] [Sep]
     * 
     * 时间偏移计算：
     * - Green: 开始于 Sync 之后 = 4.862ms
     * - Blue:  开始于 Sync + Green + Sep = 4.862 + 146.432 + 0.572 = 151.866ms
     * - Red:   开始于 Sync + Green + Sep + Blue + Sep = 298.870ms
     */
    override val channelConfigs: List<ChannelConfig> = listOf(
        ChannelConfig(
            channel = ColorChannel.GREEN,
            startMs = SYNC_PULSE_MS,
            durationMs = COLOR_SCAN_MS
        ),
        ChannelConfig(
            channel = ColorChannel.BLUE,
            startMs = SYNC_PULSE_MS + COLOR_SCAN_MS + SEPARATOR_MS,
            durationMs = COLOR_SCAN_MS
        ),
        ChannelConfig(
            channel = ColorChannel.RED,
            startMs = SYNC_PULSE_MS + COLOR_SCAN_MS + SEPARATOR_MS + COLOR_SCAN_MS + SEPARATOR_MS,
            durationMs = COLOR_SCAN_MS
        )
    )
}

/**
 * Martin 2 模式解码策略
 * 
 * Martin 2 是 Martin 1 的快速变体，扫描时间减半。
 * 
 * ## 技术规格
 * 
 * - 分辨率: 320 x 256 像素
 * - VIS 码: 40
 * - 颜色顺序: G-B-R
 * - 单行时长: 约 226.798 ms（是 Martin 1 的一半）
 * - 扫描速度: 约 4.4 行/秒
 * 
 * ## 与 Martin 1 的区别
 * 
 * Martin 2 每个颜色通道的扫描时间是 Martin 1 的一半：
 * - Martin 1: 146.432ms per channel
 * - Martin 2: 73.216ms per channel
 * 
 * 传输时间减半，但信噪比略低。
 */
class Martin2Strategy : RgbModeStrategy() {
    
    companion object {
        const val VIS_CODE = 40
        const val MODE_NAME = "Martin 2"
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 256
        
        // 时序参数 (毫秒)
        const val SYNC_PULSE_MS = 4.862
        const val SEPARATOR_MS = 0.572
        const val COLOR_SCAN_MS = 73.216  // Martin 2 的颜色扫描时间是 Martin 1 的一半
        
        const val SYNC_FREQ = SstvConstants.SYNC_FREQ
    }
    
    override val modeName: String = MODE_NAME
    override val visCode: Int = VIS_CODE
    override val width: Int = IMAGE_WIDTH
    override val height: Int = IMAGE_HEIGHT
    
    override val syncPulseMs: Double = SYNC_PULSE_MS
    override val syncPulseFreq: Int = SYNC_FREQ
    
    override val scanLineTimeMs: Double = 
        SYNC_PULSE_MS + 
        COLOR_SCAN_MS + SEPARATOR_MS +
        COLOR_SCAN_MS + SEPARATOR_MS +
        COLOR_SCAN_MS + SEPARATOR_MS
    
    override val channelConfigs: List<ChannelConfig> = listOf(
        ChannelConfig(
            channel = ColorChannel.GREEN,
            startMs = SYNC_PULSE_MS,
            durationMs = COLOR_SCAN_MS
        ),
        ChannelConfig(
            channel = ColorChannel.BLUE,
            startMs = SYNC_PULSE_MS + COLOR_SCAN_MS + SEPARATOR_MS,
            durationMs = COLOR_SCAN_MS
        ),
        ChannelConfig(
            channel = ColorChannel.RED,
            startMs = SYNC_PULSE_MS + COLOR_SCAN_MS + SEPARATOR_MS + COLOR_SCAN_MS + SEPARATOR_MS,
            durationMs = COLOR_SCAN_MS
        )
    )
}
