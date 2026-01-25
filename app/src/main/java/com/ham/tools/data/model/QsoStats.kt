package com.ham.tools.data.model

import androidx.room.ColumnInfo

/**
 * 频率/波段统计数据
 */
data class FrequencyStat(
    @ColumnInfo(name = "frequency")
    val frequency: String,
    
    @ColumnInfo(name = "count")
    val count: Int
) {
    /**
     * 从频率字符串提取波段名称
     * 例如: "14.200 MHz" -> "20m"
     */
    val bandName: String
        get() {
            val freqMhz = frequency.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return frequency
            return when {
                freqMhz in 1.8..2.0 -> "160m"
                freqMhz in 3.5..4.0 -> "80m"
                freqMhz in 7.0..7.3 -> "40m"
                freqMhz in 10.1..10.15 -> "30m"
                freqMhz in 14.0..14.35 -> "20m"
                freqMhz in 18.068..18.168 -> "17m"
                freqMhz in 21.0..21.45 -> "15m"
                freqMhz in 24.89..24.99 -> "12m"
                freqMhz in 28.0..29.7 -> "10m"
                freqMhz in 50.0..54.0 -> "6m"
                freqMhz in 144.0..148.0 -> "2m"
                freqMhz in 430.0..450.0 -> "70cm"
                else -> frequency
            }
        }
}

/**
 * 模式统计数据
 * 
 * 注意：mode 使用 String 类型，因为 Room GROUP BY 查询返回的是存储的原始字符串值
 */
data class ModeStat(
    @ColumnInfo(name = "mode")
    val mode: String,  // Room 存储的是枚举名称字符串
    
    @ColumnInfo(name = "count")
    val count: Int
) {
    /**
     * 获取 Mode 枚举值
     */
    val modeEnum: Mode?
        get() = try { Mode.valueOf(mode) } catch (e: Exception) { null }
}

/**
 * 综合统计数据
 */
data class QsoStatistics(
    val totalCount: Int = 0,
    val thisMonthCount: Int = 0,
    val todayCount: Int = 0,
    val thisYearCount: Int = 0,
    val frequencyStats: List<FrequencyStat> = emptyList(),
    val modeStats: List<ModeStat> = emptyList()
) {
    /**
     * 获取最常用的波段
     */
    val topBand: String?
        get() = frequencyStats.firstOrNull()?.bandName
    
    /**
     * 获取最常用的模式
     */
    val topMode: Mode?
        get() = modeStats.firstOrNull()?.modeEnum
}
