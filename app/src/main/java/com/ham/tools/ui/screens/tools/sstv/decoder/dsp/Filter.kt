package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * 滤波器工具 - 移植自 Robot36
 */
object Filter {
    
    /**
     * 生成低通滤波器系数
     */
    fun lowPass(cutoff: Double, sampleRate: Double, index: Int, length: Int): Double {
        val n = index - (length - 1) / 2.0
        val omega = 2 * PI * cutoff / sampleRate
        return if (n == 0.0) {
            omega / PI
        } else {
            sin(omega * n) / (PI * n)
        }
    }
}
