package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 颜色转换器 - 移植自 Robot36
 */
object ColorConverter {
    
    private fun clamp(value: Int): Int = value.coerceIn(0, 255)
    
    private fun clamp(value: Float): Float = value.coerceIn(0f, 1f)
    
    private fun float2int(level: Float): Int {
        val intensity = (255 * level).roundToInt()
        return clamp(intensity)
    }
    
    private fun compress(level: Float): Int {
        val compressed = sqrt(clamp(level))
        return float2int(compressed)
    }
    
    private fun YUV2RGB(Y: Int, U: Int, V: Int): Int {
        val y = Y - 16
        val u = U - 128
        val v = V - 128
        val R = clamp((298 * y + 409 * v + 128) shr 8)
        val G = clamp((298 * y - 100 * u - 208 * v + 128) shr 8)
        val B = clamp((298 * y + 516 * u + 128) shr 8)
        return 0xff000000.toInt() or (R shl 16) or (G shl 8) or B
    }
    
    /**
     * 灰度转换（带伽马压缩）
     */
    fun GRAY(level: Float): Int {
        return 0xff000000.toInt() or (0x00010101 * compress(level))
    }
    
    /**
     * RGB 转换
     */
    fun RGB(red: Float, green: Float, blue: Float): Int {
        return 0xff000000.toInt() or 
               (float2int(red) shl 16) or 
               (float2int(green) shl 8) or 
               float2int(blue)
    }
    
    /**
     * YUV 转 RGB（浮点输入）
     */
    fun YUV2RGB(Y: Float, U: Float, V: Float): Int {
        return YUV2RGB(float2int(Y), float2int(U), float2int(V))
    }
    
    /**
     * YUV 转 RGB（打包整数输入）
     */
    fun YUV2RGB(YUV: Int): Int {
        return YUV2RGB(
            (YUV and 0x00ff0000) shr 16,
            (YUV and 0x0000ff00) shr 8,
            YUV and 0x000000ff
        )
    }
}
