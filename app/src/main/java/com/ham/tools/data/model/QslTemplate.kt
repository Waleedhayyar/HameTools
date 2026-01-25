package com.ham.tools.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Text element on QSL card template
 * 
 * @property id Unique identifier for the element
 * @property placeholder The placeholder type (e.g., CALLSIGN, DATE, FREQ)
 * @property x X position as percentage of canvas width (0.0 - 1.0)
 * @property y Y position as percentage of canvas height (0.0 - 1.0)
 * @property fontSize Font size in sp
 * @property color Color as ARGB int
 * @property fontWeight Font weight (normal = 400, bold = 700)
 */
data class TextElement(
    val id: String,
    val placeholder: QslPlaceholder,
    val x: Float,
    val y: Float,
    val fontSize: Float = 24f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val fontWeight: Int = 400
)

/**
 * Placeholders available for QSL cards
 */
enum class QslPlaceholder(val displayName: String, val defaultText: String) {
    MY_CALLSIGN("我的呼号", "BV2XXX"),
    THEIR_CALLSIGN("对方呼号", "JA1XXX"),
    DATE("日期", "2026-01-22"),
    TIME("时间 UTC", "08:30"),
    FREQUENCY("频率", "14.200 MHz"),
    MODE("模式", "SSB"),
    RST_SENT("RST发送", "59"),
    RST_RCVD("RST接收", "59"),
    QTH("QTH", "Beijing"),
    GRID("网格", "OM89"),
    POWER("功率", "100W"),
    MESSAGE("自定义文字", "73!");

    companion object {
        fun asList(): List<QslPlaceholder> = entries
    }
}

/**
 * Entity representing a QSL card template
 */
@Entity(tableName = "qsl_templates")
data class QslTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Template name */
    val name: String,
    
    /** Background image URI (content:// or file://) */
    val backgroundUri: String? = null,
    
    /** Background color if no image (ARGB int) */
    val backgroundColor: Int = 0xFF1A1A2E.toInt(),
    
    /** Canvas width in pixels */
    val canvasWidth: Int = 1200,
    
    /** Canvas height in pixels */
    val canvasHeight: Int = 800,
    
    /** JSON serialized list of TextElement */
    val textElementsJson: String = "[]",
    
    /** Whether this is the default template */
    val isDefault: Boolean = false,
    
    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Last modified timestamp */
    val updatedAt: Long = System.currentTimeMillis()
)
