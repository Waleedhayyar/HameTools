package com.ham.tools.data.model

/**
 * Enum representing QSL confirmation status
 * Covers physical cards, LoTW, eQSL, and ClubLog
 */
enum class QslStatus(val displayName: String, val shortName: String) {
    // Physical QSL Card
    NOT_SENT("未发卡", "未发"),
    SENT("已发卡", "已发"),
    RECEIVED("已收到", "已收"),
    
    // LoTW (Logbook of The World)
    LOTW_UPLOADED("LoTW已上传", "LoTW↑"),
    LOTW_CONFIRMED("LoTW已确认", "LoTW✓"),
    
    // eQSL
    EQSL_SENT("eQSL已发送", "eQSL↑"),
    EQSL_CONFIRMED("eQSL已确认", "eQSL✓"),
    
    // ClubLog
    CLUBLOG_UPLOADED("ClubLog已上传", "CL↑"),
    CLUBLOG_CONFIRMED("ClubLog已确认", "CL✓");

    companion object {
        fun asList(): List<QslStatus> = entries
        
        /**
         * Get statuses grouped by category for UI display
         */
        fun groupedByCategory(): Map<String, List<QslStatus>> = mapOf(
            "实体卡片" to listOf(NOT_SENT, SENT, RECEIVED),
            "LoTW" to listOf(LOTW_UPLOADED, LOTW_CONFIRMED),
            "eQSL" to listOf(EQSL_SENT, EQSL_CONFIRMED),
            "ClubLog" to listOf(CLUBLOG_UPLOADED, CLUBLOG_CONFIRMED)
        )
    }
}
