package com.ham.tools.data.model

import kotlinx.serialization.Serializable

/**
 * 波段传播条件
 * 
 * @param name 波段名称，如 "80m-40m", "30m-20m", "17m-15m", "12m-10m"
 * @param time 时间段 "day" 或 "night"
 * @param condition 传播条件 "Good", "Fair", "Poor"
 */
@Serializable
data class BandCondition(
    val name: String,
    val time: String,
    val condition: String
) {
    /**
     * 获取条件等级用于排序和颜色显示
     * Good = 3, Fair = 2, Poor = 1, Unknown = 0
     */
    val conditionLevel: Int
        get() = when (condition.lowercase()) {
            "good" -> 3
            "fair" -> 2
            "poor" -> 1
            else -> 0
        }
    
    val isDay: Boolean get() = time.lowercase() == "day"
    val isNight: Boolean get() = time.lowercase() == "night"
}

/**
 * VHF 传播条件（可选）
 */
@Serializable
data class VhfCondition(
    val phenomenon: String,  // e.g. "aurora", "E-Skip", etc.
    val location: String? = null
)

/**
 * 太阳/地磁数据模型
 * 
 * 从 hamqsl.com/solarxml.php 获取的实时数据
 */
@Serializable
data class SolarData(
    // 更新时间 (GMT格式，如 "22 Jan 2026 0352 GMT")
    val updated: String,
    
    // 太阳通量指数 (Solar Flux Index)
    // 衡量太阳活动水平，高值通常意味着更好的高频传播
    val solarFlux: Int,
    
    // A 指数 - 地磁活动的日平均值
    // 越低越好，A > 30 通常会降低传播质量
    val aIndex: Int,
    
    // K 指数 - 地磁活动的3小时平均值 (0-9)
    // K <= 2: 稳定，K >= 5: 风暴，影响传播
    val kIndex: Int,
    
    // 地磁场状态，如 "QUIET", "UNSETTLED", "ACTIVE", "STORM"
    val geomagField: String,
    
    // 信号噪声水平，如 "S0", "S1-S2", "S3-S4"
    val signalNoise: String,
    
    // 太阳风速度 (km/s)
    val solarWind: Int? = null,
    
    // 磁场 Bz 分量 (nT)
    val magneticBz: Double? = null,
    
    // 太阳黑子数
    val sunspots: Int? = null,
    
    // X射线耀斑等级
    val xRay: String? = null,
    
    // 质子通量
    val protonFlux: String? = null,
    
    // 电子通量
    val electronFlux: String? = null,
    
    // HF 波段计算条件
    val bandConditions: List<BandCondition>,
    
    // VHF 传播条件（可选）
    val vhfConditions: List<VhfCondition> = emptyList(),
    
    // 数据获取时间戳（本地时间，用于缓存判断）
    val fetchedAt: Long = System.currentTimeMillis()
) {
    /**
     * 检查缓存是否过期
     * @param maxAgeMinutes 最大缓存时间（分钟）
     */
    fun isCacheExpired(maxAgeMinutes: Int = 15): Boolean {
        val ageMs = System.currentTimeMillis() - fetchedAt
        return ageMs > maxAgeMinutes * 60 * 1000
    }
    
    /**
     * 获取白天波段条件
     */
    val dayConditions: List<BandCondition>
        get() = bandConditions.filter { it.isDay }
    
    /**
     * 获取夜间波段条件
     */
    val nightConditions: List<BandCondition>
        get() = bandConditions.filter { it.isNight }
    
    /**
     * 判断当前传播是否有利
     * SFI > 100 且 K < 4 通常条件较好
     */
    val isFavorable: Boolean
        get() = solarFlux > 100 && kIndex < 4
    
    /**
     * 判断是否处于地磁风暴
     */
    val isStorm: Boolean
        get() = kIndex >= 5 || geomagField.uppercase().contains("STORM")
    
    /**
     * 生成简要传播总结
     */
    fun generateSummary(): String {
        return buildString {
            // SFI 评估
            when {
                solarFlux >= 150 -> append("SFI偏高($solarFlux)，高频段可能有机会。")
                solarFlux >= 100 -> append("SFI适中($solarFlux)。")
                else -> append("SFI偏低($solarFlux)，高频段传播受限。")
            }
            
            append(" ")
            
            // 地磁评估
            when {
                kIndex >= 5 -> append("地磁活跃(K=$kIndex)，可能影响传播。")
                kIndex >= 3 -> append("地磁略有扰动(K=$kIndex)。")
                else -> append("地磁平静(K=$kIndex)，条件稳定。")
            }
            
            // 整体建议
            append(" ")
            val goodBands = bandConditions.filter { it.conditionLevel >= 3 }
            val fairBands = bandConditions.filter { it.conditionLevel == 2 }
            
            when {
                goodBands.isNotEmpty() -> {
                    val bands = goodBands.map { it.name }.distinct().take(2).joinToString("、")
                    append("建议尝试 $bands 波段。")
                }
                fairBands.isNotEmpty() -> append("整体条件一般，可尝试低频段。")
                else -> append("传播条件较差，建议选择更可靠的模式。")
            }
        }
    }
}

/**
 * 传播预测 UI 状态
 */
sealed class PropagationUiState {
    /** 加载中 */
    data object Loading : PropagationUiState()
    
    /** 加载成功 */
    data class Success(
        val data: SolarData,
        val isFromCache: Boolean = false
    ) : PropagationUiState()
    
    /** 加载失败 */
    data class Error(
        val message: String,
        val cachedData: SolarData? = null
    ) : PropagationUiState()
}
