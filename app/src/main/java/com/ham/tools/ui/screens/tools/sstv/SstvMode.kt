package com.ham.tools.ui.screens.tools.sstv

/**
 * SSTV 颜色通道枚举
 */
enum class ColorChannel {
    GREEN,
    BLUE,
    RED,
    Y,    // 亮度 (用于 YUV 模式)
    U,    // 蓝色色度 Cb (用于 YUV 模式)
    V     // 红色色度 Cr (用于 YUV 模式)
}

/**
 * 颜色扫描段定义
 * 
 * @param channel 颜色通道
 * @param durationMs 该通道的扫描时长（毫秒）
 */
data class ColorScanSegment(
    val channel: ColorChannel,
    val durationMs: Double
)

/**
 * SSTV 模式行结构类型
 * 
 * 不同 SSTV 模式的行结构有显著差异：
 * - Martin: 同步脉冲在行开头，后面是 G-B-R 颜色数据
 * - Scottie: 同步脉冲在行中间，颜色数据在同步前后分布
 */
enum class LineStructureType {
    /** Martin 类型: [Sync] [G] [Sep] [B] [Sep] [R] [Sep] */
    MARTIN,
    
    /** Scottie 类型: [G] [Sep] [B] [Sync] [R] [Sep] (同步在中间) */
    SCOTTIE
}

/**
 * SSTV 模式数据类
 * 
 * 定义一个 SSTV 模式解码所需的所有物理参数。
 * 
 * @param modeName 模式名称（如 "Martin 1"）
 * @param visCode VIS 码（用于自动模式识别）
 * @param width 图像宽度（像素）
 * @param height 图像高度（像素）
 * @param syncPulseMs 同步脉冲时长（毫秒）
 * @param syncPulseFreq 同步脉冲频率（Hz）
 * @param separatorMs 分隔脉冲时长（毫秒）
 * @param separatorFreq 分隔脉冲频率（Hz）
 * @param colorSequence 颜色发送顺序（包含每种颜色的精确扫描时长）
 * @param lineStructureType 行结构类型
 * @param scanLineTimeMs 单行总时长（毫秒，用于校验和时序同步）
 */
data class SstvMode(
    val modeName: String,
    val visCode: Int,
    val width: Int,
    val height: Int,
    val syncPulseMs: Double,
    val syncPulseFreq: Int = SstvConstants.SYNC_FREQ,
    val separatorMs: Double,
    val separatorFreq: Int = SstvConstants.SYNC_FREQ,
    val colorSequence: List<ColorScanSegment>,
    val lineStructureType: LineStructureType,
    val scanLineTimeMs: Double
) {
    // ==================== 计算属性 ====================
    
    /** 采样率 (固定 44100 Hz) */
    val sampleRate: Int = 44100
    
    /** 每毫秒采样数 */
    val samplesPerMs: Double = sampleRate / 1000.0
    
    /** 同步脉冲采样数 */
    val syncPulseSamples: Int = (syncPulseMs * samplesPerMs).toInt()
    
    /** 分隔脉冲采样数 */
    val separatorSamples: Int = (separatorMs * samplesPerMs).toInt()
    
    /** 单行总采样数 */
    val lineSamples: Int = (scanLineTimeMs * samplesPerMs).toInt()
    
    /** 每个颜色通道的扫描时长（毫秒） */
    val colorScanDurationMs: Double = colorSequence.firstOrNull()?.durationMs ?: 0.0
    
    /** 每个颜色通道的采样数 */
    val colorScanSamples: Int = (colorScanDurationMs * samplesPerMs).toInt()
    
    /** 每像素采样数 */
    val samplesPerPixel: Int = colorScanSamples / width
    
    /**
     * 获取指定颜色通道的扫描时长
     */
    fun getColorDurationMs(channel: ColorChannel): Double {
        return colorSequence.find { it.channel == channel }?.durationMs ?: colorScanDurationMs
    }
    
    /**
     * 获取指定颜色通道的采样数
     */
    fun getColorSamples(channel: ColorChannel): Int {
        return (getColorDurationMs(channel) * samplesPerMs).toInt()
    }
    
    /**
     * 计算从行开始到指定颜色通道起始的偏移时间（毫秒）
     * 
     * 注意：这个计算根据 lineStructureType 不同而有差异
     */
    fun getColorOffsetMs(channel: ColorChannel): Double {
        return when (lineStructureType) {
            LineStructureType.MARTIN -> calculateMartinColorOffset(channel)
            LineStructureType.SCOTTIE -> calculateScottieColorOffset(channel)
        }
    }
    
    /**
     * 计算 Martin 类型模式的颜色偏移
     * 结构: [Sync] [G] [Sep] [B] [Sep] [R] [Sep]
     */
    private fun calculateMartinColorOffset(channel: ColorChannel): Double {
        var offset = syncPulseMs
        
        for (segment in colorSequence) {
            if (segment.channel == channel) {
                return offset
            }
            offset += segment.durationMs + separatorMs
        }
        
        return offset
    }
    
    /**
     * 计算 Scottie 类型模式的颜色偏移
     * 结构: [G] [Sep] [B] [Sync] [R] [Sep]
     * 
     * Scottie 的同步脉冲在 Blue 之后，Red 之前
     */
    private fun calculateScottieColorOffset(channel: ColorChannel): Double {
        val greenDuration = getColorDurationMs(ColorChannel.GREEN)
        val blueDuration = getColorDurationMs(ColorChannel.BLUE)
        
        return when (channel) {
            ColorChannel.GREEN -> 0.0
            ColorChannel.BLUE -> greenDuration + separatorMs
            ColorChannel.RED -> greenDuration + separatorMs + blueDuration + syncPulseMs
            // YUV 通道不适用于 Scottie 模式
            ColorChannel.Y, ColorChannel.U, ColorChannel.V -> 0.0
        }
    }
    
    /**
     * 获取指定颜色通道的偏移采样数
     */
    fun getColorOffsetSamples(channel: ColorChannel): Int {
        return (getColorOffsetMs(channel) * samplesPerMs).toInt()
    }
}

/**
 * SSTV 模式仓库 - 单例对象
 * 
 * 预定义了常用的 SSTV 模式参数
 */
object SstvModeRepository {
    
    /**
     * Martin 1 模式
     * 
     * - 最常用的 SSTV 模式之一
     * - 分辨率: 320 x 256
     * - 颜色顺序: G-B-R
     * - 特点: 同步脉冲在行开头
     * 
     * 行结构 (时序):
     * [Sync 4.862ms] [Green 146.432ms] [Sep 0.572ms] [Blue 146.432ms] [Sep 0.572ms] [Red 146.432ms] [Sep 0.572ms]
     */
    val MARTIN_1 = SstvMode(
        modeName = "Martin 1",
        visCode = 44,
        width = 320,
        height = 256,
        syncPulseMs = 4.862,
        syncPulseFreq = 1200,
        separatorMs = 0.572,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.GREEN, 146.432),
            ColorScanSegment(ColorChannel.BLUE, 146.432),
            ColorScanSegment(ColorChannel.RED, 146.432)
        ),
        lineStructureType = LineStructureType.MARTIN,
        scanLineTimeMs = 446.446  // 实际测量约 446-457ms
    )
    
    /**
     * Martin 2 模式
     * 
     * - Martin 1 的低分辨率变体
     * - 分辨率: 320 x 256（但扫描时间更短）
     * - 颜色顺序: G-B-R
     */
    val MARTIN_2 = SstvMode(
        modeName = "Martin 2",
        visCode = 40,
        width = 320,
        height = 256,
        syncPulseMs = 4.862,
        syncPulseFreq = 1200,
        separatorMs = 0.572,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.GREEN, 73.216),
            ColorScanSegment(ColorChannel.BLUE, 73.216),
            ColorScanSegment(ColorChannel.RED, 73.216)
        ),
        lineStructureType = LineStructureType.MARTIN,
        scanLineTimeMs = 226.798
    )
    
    /**
     * Scottie 1 模式
     * 
     * - 另一种常用的 SSTV 模式
     * - 分辨率: 320 x 256
     * - 颜色顺序: G-B-R（但同步脉冲位置不同）
     * - 特点: 同步脉冲在 Blue 之后、Red 之前
     * 
     * 行结构 (时序):
     * [Green 138.240ms] [Sep 1.5ms] [Blue 138.240ms] [Sync 9.0ms] [Red 138.240ms] [Sep 1.5ms]
     * 
     * 注意: Scottie 的第一行略有特殊，起始同步脉冲较长
     */
    val SCOTTIE_1 = SstvMode(
        modeName = "Scottie 1",
        visCode = 60,
        width = 320,
        height = 256,
        syncPulseMs = 9.0,
        syncPulseFreq = 1200,
        separatorMs = 1.5,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.GREEN, 138.240),
            ColorScanSegment(ColorChannel.BLUE, 138.240),
            ColorScanSegment(ColorChannel.RED, 138.240)
        ),
        lineStructureType = LineStructureType.SCOTTIE,
        scanLineTimeMs = 428.22  // 138.24*3 + 1.5*2 + 9.0
    )
    
    /**
     * Scottie 2 模式
     * 
     * - Scottie 1 的快速变体
     * - 分辨率: 320 x 256
     */
    val SCOTTIE_2 = SstvMode(
        modeName = "Scottie 2",
        visCode = 56,
        width = 320,
        height = 256,
        syncPulseMs = 9.0,
        syncPulseFreq = 1200,
        separatorMs = 1.5,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.GREEN, 88.064),
            ColorScanSegment(ColorChannel.BLUE, 88.064),
            ColorScanSegment(ColorChannel.RED, 88.064)
        ),
        lineStructureType = LineStructureType.SCOTTIE,
        scanLineTimeMs = 277.692  // 88.064*3 + 1.5*2 + 9.0
    )
    
    /**
     * Scottie DX 模式
     * 
     * - Scottie 系列的高质量变体
     * - 更长的扫描时间意味着更好的信噪比
     */
    val SCOTTIE_DX = SstvMode(
        modeName = "Scottie DX",
        visCode = 76,
        width = 320,
        height = 256,
        syncPulseMs = 9.0,
        syncPulseFreq = 1200,
        separatorMs = 1.5,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.GREEN, 345.6),
            ColorScanSegment(ColorChannel.BLUE, 345.6),
            ColorScanSegment(ColorChannel.RED, 345.6)
        ),
        lineStructureType = LineStructureType.SCOTTIE,
        scanLineTimeMs = 1049.8  // 345.6*3 + 1.5*2 + 9.0
    )
    
    /**
     * Robot 36 模式 (YUV)
     * 
     * - 使用 YUV 颜色编码，传输更高效
     * - 分辨率: 320 x 240
     * - VIS 码: 8
     * - 单行时长: 约 150ms
     * 
     * 行结构 (时序):
     * [Sync 9.0ms] [Y 88.0ms] [Sep 4.5ms] [R-Y 44.0ms] [Sep 4.5ms] [B-Y 44.0ms] [Sep 4.5ms]
     * 
     * YUV → RGB 转换使用 ITU-R BT.601 标准
     */
    val ROBOT_36 = SstvMode(
        modeName = "Robot 36",
        visCode = 8,
        width = 320,
        height = 240,
        syncPulseMs = 9.0,
        syncPulseFreq = 1200,
        separatorMs = 4.5,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.Y, 88.0),      // 亮度
            ColorScanSegment(ColorChannel.V, 44.0),      // R-Y (Cr)
            ColorScanSegment(ColorChannel.U, 44.0)       // B-Y (Cb)
        ),
        lineStructureType = LineStructureType.MARTIN,  // Robot 类似 Martin 的行结构
        scanLineTimeMs = 150.0  // 9.0 + 88.0 + 4.5 + 44.0 + 4.5 + 44.0 + 4.5 ≈ 198.5ms (实际约150ms)
    )
    
    /**
     * Robot 72 模式 (YUV)
     * 
     * - Robot 36 的高质量变体，时长加倍
     * - 分辨率: 320 x 240
     * - VIS 码: 12
     * - 单行时长: 约 300ms
     */
    val ROBOT_72 = SstvMode(
        modeName = "Robot 72",
        visCode = 12,
        width = 320,
        height = 240,
        syncPulseMs = 9.0,
        syncPulseFreq = 1200,
        separatorMs = 4.5,
        separatorFreq = 1500,
        colorSequence = listOf(
            ColorScanSegment(ColorChannel.Y, 138.0),     // 亮度
            ColorScanSegment(ColorChannel.V, 69.0),      // R-Y (Cr)
            ColorScanSegment(ColorChannel.U, 69.0)       // B-Y (Cb)
        ),
        lineStructureType = LineStructureType.MARTIN,
        scanLineTimeMs = 300.0
    )
    
    /**
     * 所有支持的模式列表
     */
    val allModes: List<SstvMode> = listOf(
        MARTIN_1,
        MARTIN_2,
        SCOTTIE_1,
        SCOTTIE_2,
        SCOTTIE_DX,
        ROBOT_36,
        ROBOT_72
    )
    
    /**
     * VIS 码到模式的映射
     */
    private val visModeMap: Map<Int, SstvMode> = allModes.associateBy { it.visCode }
    
    /**
     * 根据 VIS 码获取对应的模式
     * 
     * @param visCode VIS 码
     * @return 对应的 SstvMode，如果未找到则返回 null
     */
    fun getModeByVisCode(visCode: Int): SstvMode? {
        return visModeMap[visCode]
    }
    
    /**
     * 根据模式名称获取模式
     * 
     * @param name 模式名称（不区分大小写）
     * @return 对应的 SstvMode，如果未找到则返回 null
     */
    fun getModeByName(name: String): SstvMode? {
        return allModes.find { it.modeName.equals(name, ignoreCase = true) }
    }
    
    /**
     * 获取默认模式（Martin 1）
     */
    fun getDefaultMode(): SstvMode = MARTIN_1
    
    /**
     * 检查 VIS 码是否受支持
     */
    fun isSupportedVisCode(visCode: Int): Boolean {
        return visModeMap.containsKey(visCode)
    }
}
