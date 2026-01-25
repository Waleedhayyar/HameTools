package com.ham.tools.data.model

/**
 * Enum representing radio propagation modes
 */
enum class PropagationMode(val displayName: String) {
    UNKNOWN("未知/默认"),
    GROUND_WAVE("地波"),
    SKYWAVE("天波"),
    F2("F2层反射"),
    SPORADIC_E("Es突发E层"),
    AURORA("极光"),
    AURORA_E("极光E"),
    TROPO("对流层"),
    METEOR_SCATTER("流星余迹"),
    EME("月面反射"),
    SATELLITE("卫星"),
    TEP("跨赤道传播"),
    NVIS("近垂直入射"),
    INTERNET("网络链路"),
    REPEATER("中继");

    companion object {
        fun asList(): List<PropagationMode> = entries
        
        /**
         * Common propagation modes for quick selection
         */
        fun commonModes(): List<PropagationMode> = listOf(
            UNKNOWN, SKYWAVE, F2, SPORADIC_E, TROPO, SATELLITE, REPEATER
        )
    }
}
