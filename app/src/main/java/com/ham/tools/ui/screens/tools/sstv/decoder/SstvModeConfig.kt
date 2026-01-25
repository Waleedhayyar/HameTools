package com.ham.tools.ui.screens.tools.sstv.decoder

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * SSTV 模式配置
 * 
 * 定义各种SSTV模式的参数和解码策略。
 * 分辨率完全由配置决定，解码器不限制固定分辨率。
 */
data class SstvModeConfig(
    val modeName: String,
    val visCode: Int,
    val width: Int,
    val height: Int,
    val lineTimeMs: Double,
    val syncPulseMs: Double,
    val colorType: ColorType,
    val lineStructure: LineStructure
) {
    /** 创建对应的行解码器 */
    fun createDecoder(): LineDecoder {
        return when (lineStructure) {
            is LineStructure.Robot -> RobotLineDecoder(lineStructure, width)
            is LineStructure.Martin -> MartinLineDecoder(lineStructure, width)
            is LineStructure.Scottie -> ScottieLineDecoder(lineStructure, width)
            is LineStructure.PD -> PdLineDecoder(lineStructure, width)
            is LineStructure.Wraase -> WraaseLineDecoder(lineStructure, width)
        }
    }
    
    companion object {
        // ==================== Robot 系列 ====================
        
        val ROBOT_36 = SstvModeConfig(
            modeName = "Robot 36",
            visCode = 8,
            width = 320,
            height = 240,
            lineTimeMs = 150.0,
            syncPulseMs = 9.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.Robot(
                syncMs = 9.0,
                yScanMs = 88.0,
                chromaScanMs = 44.0,
                separatorMs = 4.5
            )
        )
        
        val ROBOT_72 = SstvModeConfig(
            modeName = "Robot 72",
            visCode = 12,
            width = 320,
            height = 240,
            lineTimeMs = 300.0,
            syncPulseMs = 9.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.Robot(
                syncMs = 9.0,
                yScanMs = 138.0,
                chromaScanMs = 69.0,
                separatorMs = 4.5
            )
        )
        
        // ==================== Martin 系列 ====================
        
        val MARTIN_1 = SstvModeConfig(
            modeName = "Martin 1",
            visCode = 44,
            width = 320,
            height = 256,
            lineTimeMs = 446.446,
            syncPulseMs = 4.862,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Martin(
                syncMs = 4.862,
                colorScanMs = 146.432,
                separatorMs = 0.572
            )
        )
        
        val MARTIN_2 = SstvModeConfig(
            modeName = "Martin 2",
            visCode = 40,
            width = 320,
            height = 256,
            lineTimeMs = 226.798,
            syncPulseMs = 4.862,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Martin(
                syncMs = 4.862,
                colorScanMs = 73.216,
                separatorMs = 0.572
            )
        )
        
        // ==================== Scottie 系列 ====================
        
        val SCOTTIE_1 = SstvModeConfig(
            modeName = "Scottie 1",
            visCode = 60,
            width = 320,
            height = 256,
            lineTimeMs = 428.22,
            syncPulseMs = 9.0,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Scottie(
                syncMs = 9.0,
                colorScanMs = 138.240,
                separatorMs = 1.5
            )
        )
        
        val SCOTTIE_2 = SstvModeConfig(
            modeName = "Scottie 2",
            visCode = 56,
            width = 320,
            height = 256,
            lineTimeMs = 277.692,
            syncPulseMs = 9.0,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Scottie(
                syncMs = 9.0,
                colorScanMs = 88.064,
                separatorMs = 1.5
            )
        )
        
        val SCOTTIE_DX = SstvModeConfig(
            modeName = "Scottie DX",
            visCode = 76,
            width = 320,
            height = 256,
            lineTimeMs = 1049.8,
            syncPulseMs = 9.0,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Scottie(
                syncMs = 9.0,
                colorScanMs = 345.6,
                separatorMs = 1.5
            )
        )
        
        // ==================== PD 系列 ====================
        
        val PD_50 = SstvModeConfig(
            modeName = "PD 50",
            visCode = 93,
            width = 320,
            height = 256,
            lineTimeMs = 91.52,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 91.52 - 20.0,
                chromaScanMs = 0.0  // PD 50 是灰度模式
            )
        )
        
        val PD_90 = SstvModeConfig(
            modeName = "PD 90",
            visCode = 99,
            width = 320,
            height = 256,
            lineTimeMs = 170.24,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 88.0,
                chromaScanMs = 44.0
            )
        )
        
        val PD_120 = SstvModeConfig(
            modeName = "PD 120",
            visCode = 95,
            width = 640,
            height = 496,
            lineTimeMs = 121.6,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 60.8,
                chromaScanMs = 30.4
            )
        )
        
        val PD_160 = SstvModeConfig(
            modeName = "PD 160",
            visCode = 98,
            width = 512,
            height = 400,
            lineTimeMs = 195.84,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 97.92,
                chromaScanMs = 48.96
            )
        )
        
        val PD_180 = SstvModeConfig(
            modeName = "PD 180",
            visCode = 96,
            width = 640,
            height = 496,
            lineTimeMs = 183.04,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 91.52,
                chromaScanMs = 45.76
            )
        )
        
        val PD_240 = SstvModeConfig(
            modeName = "PD 240",
            visCode = 97,
            width = 640,
            height = 496,
            lineTimeMs = 244.48,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 122.24,
                chromaScanMs = 61.12
            )
        )
        
        val PD_290 = SstvModeConfig(
            modeName = "PD 290",
            visCode = 94,
            width = 800,
            height = 616,
            lineTimeMs = 228.8,
            syncPulseMs = 20.0,
            colorType = ColorType.YUV,
            lineStructure = LineStructure.PD(
                syncMs = 20.0,
                yScanMs = 114.4,
                chromaScanMs = 57.2
            )
        )
        
        // ==================== Wraase SC2 系列 ====================
        
        val WRAASE_SC2_180 = SstvModeConfig(
            modeName = "Wraase SC2-180",
            visCode = 55,
            width = 320,
            height = 256,
            lineTimeMs = 235.0,
            syncPulseMs = 5.5225,
            colorType = ColorType.RGB,
            lineStructure = LineStructure.Wraase(
                syncMs = 5.5225,
                colorScanMs = 235.0 / 3.0
            )
        )
        
        /**
         * 所有支持的模式
         */
        val ALL_MODES = listOf(
            ROBOT_36, ROBOT_72,
            MARTIN_1, MARTIN_2,
            SCOTTIE_1, SCOTTIE_2, SCOTTIE_DX,
            PD_50, PD_90, PD_120, PD_160, PD_180, PD_240, PD_290,
            WRAASE_SC2_180
        )
        
        /**
         * 根据VIS码获取模式
         */
        fun getByVisCode(visCode: Int): SstvModeConfig? {
            return ALL_MODES.find { it.visCode == visCode }
        }
        
        /**
         * 获取默认模式
         */
        fun getDefault(): SstvModeConfig = ROBOT_36
    }
}

/**
 * 颜色类型
 */
enum class ColorType {
    RGB,  // Martin, Scottie
    YUV   // Robot, PD
}

/**
 * 行结构定义
 * 
 * 不同SSTV模式有不同的行时序结构
 */
sealed class LineStructure {
    
    /**
     * Robot 模式行结构
     * [Sync] [Y] [Sep] [R-Y] [Sep] [B-Y] [Sep]
     */
    data class Robot(
        val syncMs: Double,
        val yScanMs: Double,
        val chromaScanMs: Double,
        val separatorMs: Double
    ) : LineStructure()
    
    /**
     * Martin 模式行结构
     * [Sync] [G] [Sep] [B] [Sep] [R] [Sep]
     */
    data class Martin(
        val syncMs: Double,
        val colorScanMs: Double,
        val separatorMs: Double
    ) : LineStructure()
    
    /**
     * Scottie 模式行结构
     * [G] [Sep] [B] [Sync] [R] [Sep]
     */
    data class Scottie(
        val syncMs: Double,
        val colorScanMs: Double,
        val separatorMs: Double
    ) : LineStructure()
    
    /**
     * PD 模式行结构
     * [Sync] [Y] [R-Y] [B-Y] [Y]
     */
    data class PD(
        val syncMs: Double,
        val yScanMs: Double,
        val chromaScanMs: Double
    ) : LineStructure()
    
    /**
     * Wraase SC2 模式行结构
     * [Sync] [R] [G] [B]
     */
    data class Wraase(
        val syncMs: Double,
        val colorScanMs: Double
    ) : LineStructure()
}

/**
 * 行解码器接口
 */
interface LineDecoder {
    /** 图像宽度 */
    val width: Int
    
    /**
     * 解码一行频率数据为像素数组
     */
    fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray
}

/**
 * 频率工具类
 */
object FrequencyUtils {
    const val BLACK_FREQ = 1500f
    const val WHITE_FREQ = 2300f
    const val FREQ_RANGE = WHITE_FREQ - BLACK_FREQ
    
    /**
     * 频率转亮度 (0-255)
     */
    fun frequencyToLuminance(freq: Float): Int {
        val normalized = ((freq - BLACK_FREQ) / FREQ_RANGE).coerceIn(0f, 1f)
        return (normalized * 255).roundToInt()
    }
    
    /**
     * YUV 到 RGB 转换 (SSTV优化版本)
     */
    fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        val yf = y.toFloat()
        val uf = (u - 128).toFloat()
        val vf = (v - 128).toFloat()
        
        val r = (yf + 1.14f * vf).roundToInt().coerceIn(0, 255)
        val g = (yf - 0.39f * uf - 0.58f * vf).roundToInt().coerceIn(0, 255)
        val b = (yf + 2.03f * uf).roundToInt().coerceIn(0, 255)
        
        return Color.rgb(r, g, b)
    }
    
    /**
     * YUV 到 RGB 转换 (ITU-R BT.601 标准)
     */
    fun yuvToRgbBt601(y: Int, u: Int, v: Int): Int {
        val yf = y.toFloat()
        val uf = (u - 128).toFloat()
        val vf = (v - 128).toFloat()
        
        val r = (yf + 1.402f * vf).roundToInt().coerceIn(0, 255)
        val g = (yf - 0.344136f * uf - 0.714136f * vf).roundToInt().coerceIn(0, 255)
        val b = (yf + 1.772f * uf).roundToInt().coerceIn(0, 255)
        
        return Color.rgb(r, g, b)
    }
}

// ==================== 通用采样函数 ====================

/**
 * 从频率数组中采样指定区域的像素值
 */
private fun sampleChannel(
    frequencies: FloatArray,
    startSample: Int,
    totalSamples: Int,
    pixelIndex: Int,
    width: Int
): Int {
    if (totalSamples <= 0 || width <= 0) return 0
    
    val samplesPerPixel = totalSamples.toFloat() / width
    val sampleStart = startSample + (pixelIndex * samplesPerPixel).toInt()
    val sampleEnd = startSample + ((pixelIndex + 1) * samplesPerPixel).toInt()
    
    var sum = 0f
    var count = 0
    for (s in sampleStart until minOf(sampleEnd, frequencies.size)) {
        if (s >= 0) {
            sum += frequencies[s]
            count++
        }
    }
    
    return if (count > 0) {
        FrequencyUtils.frequencyToLuminance(sum / count)
    } else {
        0
    }
}

// ==================== Robot 解码器 ====================

/**
 * Robot 模式行解码器 (YUV)
 * 
 * 行结构: [Sync] [Y] [Sep] [R-Y] [Sep] [B-Y] [Sep]
 */
class RobotLineDecoder(
    private val structure: LineStructure.Robot,
    override val width: Int
) : LineDecoder {
    
    override fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        val samplesPerMs = sampleRate / 1000.0
        
        // Y通道
        val yStart = (structure.syncMs * samplesPerMs).toInt()
        val ySamples = (structure.yScanMs * samplesPerMs).toInt()
        
        // V通道 (R-Y)
        val vStart = ((structure.syncMs + structure.yScanMs + structure.separatorMs) * samplesPerMs).toInt()
        val vSamples = (structure.chromaScanMs * samplesPerMs).toInt()
        
        // U通道 (B-Y)
        val uStart = ((structure.syncMs + structure.yScanMs + structure.separatorMs + 
                      structure.chromaScanMs + structure.separatorMs) * samplesPerMs).toInt()
        val uSamples = (structure.chromaScanMs * samplesPerMs).toInt()
        
        for (x in 0 until width) {
            val y = sampleChannel(frequencies, yStart, ySamples, x, width)
            val v = sampleChannel(frequencies, vStart, vSamples, x, width)
            val u = sampleChannel(frequencies, uStart, uSamples, x, width)
            
            pixels[x] = FrequencyUtils.yuvToRgb(y, u, v)
        }
        
        return pixels
    }
}

// ==================== Martin 解码器 ====================

/**
 * Martin 模式行解码器 (RGB)
 * 
 * 行结构: [Sync] [G] [Sep] [B] [Sep] [R] [Sep]
 */
class MartinLineDecoder(
    private val structure: LineStructure.Martin,
    override val width: Int
) : LineDecoder {
    
    override fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        val samplesPerMs = sampleRate / 1000.0
        
        val colorSamples = (structure.colorScanMs * samplesPerMs).toInt()
        
        // G通道
        val gStart = (structure.syncMs * samplesPerMs).toInt()
        
        // B通道
        val bStart = ((structure.syncMs + structure.colorScanMs + structure.separatorMs) * samplesPerMs).toInt()
        
        // R通道
        val rStart = ((structure.syncMs + 2 * structure.colorScanMs + 2 * structure.separatorMs) * samplesPerMs).toInt()
        
        for (x in 0 until width) {
            val r = sampleChannel(frequencies, rStart, colorSamples, x, width)
            val g = sampleChannel(frequencies, gStart, colorSamples, x, width)
            val b = sampleChannel(frequencies, bStart, colorSamples, x, width)
            
            pixels[x] = Color.rgb(r, g, b)
        }
        
        return pixels
    }
}

// ==================== Scottie 解码器 ====================

/**
 * Scottie 模式行解码器 (RGB)
 * 
 * 行结构: [G] [Sep] [B] [Sync] [R] [Sep]
 */
class ScottieLineDecoder(
    private val structure: LineStructure.Scottie,
    override val width: Int
) : LineDecoder {
    
    override fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        val samplesPerMs = sampleRate / 1000.0
        
        val colorSamples = (structure.colorScanMs * samplesPerMs).toInt()
        
        // G通道 (开头)
        val gStart = 0
        
        // B通道
        val bStart = ((structure.colorScanMs + structure.separatorMs) * samplesPerMs).toInt()
        
        // R通道 (在sync之后)
        val rStart = ((2 * structure.colorScanMs + structure.separatorMs + structure.syncMs) * samplesPerMs).toInt()
        
        for (x in 0 until width) {
            val r = sampleChannel(frequencies, rStart, colorSamples, x, width)
            val g = sampleChannel(frequencies, gStart, colorSamples, x, width)
            val b = sampleChannel(frequencies, bStart, colorSamples, x, width)
            
            pixels[x] = Color.rgb(r, g, b)
        }
        
        return pixels
    }
}

// ==================== PD 解码器 ====================

/**
 * PD 模式行解码器 (YUV)
 * 
 * 行结构: [Sync] [Y] [R-Y] [B-Y] [Y] (奇偶行交错)
 */
class PdLineDecoder(
    private val structure: LineStructure.PD,
    override val width: Int
) : LineDecoder {
    
    override fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        val samplesPerMs = sampleRate / 1000.0
        
        // 如果色度为0，说明是灰度模式
        if (structure.chromaScanMs <= 0) {
            val ySamples = (structure.yScanMs * samplesPerMs).toInt()
            val yStart = (structure.syncMs * samplesPerMs).toInt()
            
            for (x in 0 until width) {
                val y = sampleChannel(frequencies, yStart, ySamples, x, width)
                pixels[x] = Color.rgb(y, y, y)
            }
            return pixels
        }
        
        val ySamples = (structure.yScanMs * samplesPerMs).toInt()
        val chromaSamples = (structure.chromaScanMs * samplesPerMs).toInt()
        
        // Y通道
        val yStart = (structure.syncMs * samplesPerMs).toInt()
        
        // R-Y通道
        val vStart = ((structure.syncMs + structure.yScanMs) * samplesPerMs).toInt()
        
        // B-Y通道
        val uStart = ((structure.syncMs + structure.yScanMs + structure.chromaScanMs) * samplesPerMs).toInt()
        
        for (x in 0 until width) {
            val y = sampleChannel(frequencies, yStart, ySamples, x, width)
            val v = sampleChannel(frequencies, vStart, chromaSamples, x, width)
            val u = sampleChannel(frequencies, uStart, chromaSamples, x, width)
            
            pixels[x] = FrequencyUtils.yuvToRgb(y, u, v)
        }
        
        return pixels
    }
}

// ==================== Wraase 解码器 ====================

/**
 * Wraase SC2 模式行解码器 (RGB)
 * 
 * 行结构: [Sync] [R] [G] [B]
 */
class WraaseLineDecoder(
    private val structure: LineStructure.Wraase,
    override val width: Int
) : LineDecoder {
    
    override fun decodeLine(frequencies: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        val samplesPerMs = sampleRate / 1000.0
        
        val colorSamples = (structure.colorScanMs * samplesPerMs).toInt()
        
        // R通道
        val rStart = (structure.syncMs * samplesPerMs).toInt()
        
        // G通道
        val gStart = ((structure.syncMs + structure.colorScanMs) * samplesPerMs).toInt()
        
        // B通道
        val bStart = ((structure.syncMs + 2 * structure.colorScanMs) * samplesPerMs).toInt()
        
        for (x in 0 until width) {
            val r = sampleChannel(frequencies, rStart, colorSamples, x, width)
            val g = sampleChannel(frequencies, gStart, colorSamples, x, width)
            val b = sampleChannel(frequencies, bStart, colorSamples, x, width)
            
            pixels[x] = Color.rgb(r, g, b)
        }
        
        return pixels
    }
}
