package com.ham.tools.ui.screens.tools.sstv

/**
 * SSTV 协议通用常量定义
 * 
 * SSTV (Slow Scan Television) 通过音频频率变化传输图像：
 * - 低频 (1500 Hz) = 黑色
 * - 高频 (2300 Hz) = 白色
 * - 同步信号 (1200 Hz) = 行同步脉冲
 * 
 * 频率到亮度的映射是线性的：
 * luminance = (frequency - 1500) / (2300 - 1500) = (freq - 1500) / 800
 * 
 * 注意: 具体模式的参数现在定义在 SstvMode 和 SstvModeRepository 中
 */
object SstvConstants {
    
    // ==================== 通用频率定义 (Hz) ====================
    
    /** 同步脉冲频率 */
    const val SYNC_FREQ = 1200
    
    /** 黑色频率 (最低亮度) */
    const val BLACK_FREQ = 1500
    
    /** 白色频率 (最高亮度) */
    const val WHITE_FREQ = 2300
    
    /** VIS 起始音频率 */
    const val VIS_START_FREQ = 1900
    
    /** VIS 逻辑 1 频率 */
    const val VIS_BIT_1_FREQ = 1100
    
    /** VIS 逻辑 0 频率 */
    const val VIS_BIT_0_FREQ = 1300
    
    /** 频率范围 (用于亮度计算) */
    const val FREQ_RANGE = WHITE_FREQ - BLACK_FREQ  // 800 Hz
    
    // ==================== 频率检测容差 ====================
    
    /** 频率检测容差 (Hz) */
    const val FREQ_TOLERANCE = 50
    
    /** 同步脉冲频率范围 */
    val SYNC_FREQ_RANGE = (SYNC_FREQ - FREQ_TOLERANCE)..(SYNC_FREQ + FREQ_TOLERANCE)
    
    /** 黑色频率范围 */
    val BLACK_FREQ_RANGE = (BLACK_FREQ - FREQ_TOLERANCE)..(BLACK_FREQ + FREQ_TOLERANCE)
    
    /** 白色频率范围 */
    val WHITE_FREQ_RANGE = (WHITE_FREQ - FREQ_TOLERANCE)..(WHITE_FREQ + FREQ_TOLERANCE)
    
    // ==================== 亮度转换工具函数 ====================
    
    /**
     * 将频率转换为亮度值 (0.0 - 1.0)
     * @param frequency 检测到的频率 (Hz)
     * @return 亮度值，0.0 = 黑色，1.0 = 白色
     */
    fun frequencyToLuminance(frequency: Float): Float {
        return ((frequency - BLACK_FREQ) / FREQ_RANGE).coerceIn(0f, 1f)
    }
    
    /**
     * 将亮度值转换为频率
     * @param luminance 亮度值 (0.0 - 1.0)
     * @return 对应的频率 (Hz)
     */
    fun luminanceToFrequency(luminance: Float): Float {
        return BLACK_FREQ + (luminance.coerceIn(0f, 1f) * FREQ_RANGE)
    }
    
    // ==================== 采样率相关 ====================
    
    /** 标准采样率 */
    const val SAMPLE_RATE = 44100
    
    /** 每毫秒采样数 */
    const val SAMPLES_PER_MS = 44.1  // 44100 / 1000
}

/**
 * VIS (Vertical Interval Signaling) 码定义
 * 
 * VIS 码用于标识 SSTV 模式，在图像传输前发送
 * 格式：起始位 + 7 位数据 + 奇偶校验位
 * 
 * 注意: 完整的模式定义请参考 SstvModeRepository
 */
object VisCodes {
    // Martin 系列
    const val MARTIN_1 = 44
    const val MARTIN_2 = 40
    
    // Scottie 系列
    const val SCOTTIE_1 = 60
    const val SCOTTIE_2 = 56
    const val SCOTTIE_DX = 76
    
    // Robot 系列
    const val ROBOT_36 = 8
    const val ROBOT_72 = 12
    
    // PD 系列
    const val PD_90 = 99
    const val PD_120 = 95
    const val PD_180 = 96
    const val PD_240 = 97
    
    /**
     * 根据 VIS 码获取模式名称
     * 
     * 推荐使用 SstvModeRepository.getModeByVisCode() 获取完整的模式信息
     */
    fun getModeName(visCode: Int): String {
        // 首先尝试从 Repository 获取
        SstvModeRepository.getModeByVisCode(visCode)?.let {
            return it.modeName
        }
        
        // 回退到手动映射
        return when (visCode) {
            MARTIN_1 -> "Martin 1"
            MARTIN_2 -> "Martin 2"
            SCOTTIE_1 -> "Scottie 1"
            SCOTTIE_2 -> "Scottie 2"
            SCOTTIE_DX -> "Scottie DX"
            ROBOT_36 -> "Robot 36"
            ROBOT_72 -> "Robot 72"
            PD_90 -> "PD 90"
            PD_120 -> "PD 120"
            PD_180 -> "PD 180"
            PD_240 -> "PD 240"
            else -> "Unknown ($visCode)"
        }
    }
    
    /**
     * 检查 VIS 码是否为支持的模式
     */
    fun isSupported(visCode: Int): Boolean {
        return SstvModeRepository.isSupportedVisCode(visCode)
    }
}
