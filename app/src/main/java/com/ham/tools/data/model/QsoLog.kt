package com.ham.tools.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a QSO (radio contact) log entry
 * 
 * Contains comprehensive fields for amateur radio logging,
 * following common standards like ADIF.
 */
@Entity(tableName = "qso_logs")
data class QsoLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // ===== 基本信息 (Required) =====
    
    /** 对方呼号 */
    val callsign: String,
    
    /** 频率 (e.g., "14.200 MHz", "145.500") */
    val frequency: String,
    
    /** 通信模式 */
    val mode: Mode,
    
    /** 发送的信号报告 */
    val rstSent: String = "59",
    
    /** 接收的信号报告 */
    val rstRcvd: String = "59",
    
    /** UTC 时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    
    // ===== 对方信息 (Optional) =====
    
    /** 对方姓名 / OP Name */
    val opName: String? = null,
    
    /** 对方 QTH / 地点 (e.g., "北京", "California") */
    val qth: String? = null,
    
    /** 网格定位 / Maidenhead Locator (e.g., "OM89cd") */
    val gridLocator: String? = null,
    
    /** QSL 信息 / 备注 (e.g., "QSL via bureau", "via TA1HZ") */
    val qslInfo: String? = null,
    
    // ===== 我方信息 (Optional) =====
    
    /** 发射功率 (e.g., "100W", "5W") */
    val txPower: String? = null,
    
    /** 设备/天线 (e.g., "IC-7300 + GP-3") */
    val rig: String? = null,
    
    /** 我方网格定位 */
    val myGridLocator: String? = null,
    
    // ===== 传播与确认 =====
    
    /** 传播模式 */
    val propagation: PropagationMode = PropagationMode.UNKNOWN,
    
    /** QSL 确认状态 */
    val qslStatus: QslStatus = QslStatus.NOT_SENT,
    
    // ===== 备注 =====
    
    /** 
     * 通用备注字段
     * 可记录：天气、太阳黑子数、K指数、特殊事件、聊天内容等
     */
    val remarks: String? = null
) {
    /**
     * Check if any optional fields are filled
     */
    fun hasExtendedInfo(): Boolean {
        return !opName.isNullOrBlank() ||
               !qth.isNullOrBlank() ||
               !gridLocator.isNullOrBlank() ||
               !qslInfo.isNullOrBlank() ||
               !txPower.isNullOrBlank() ||
               !rig.isNullOrBlank() ||
               propagation != PropagationMode.UNKNOWN ||
               !remarks.isNullOrBlank()
    }
}
