package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.PI

/**
 * FM 解调器 - 移植自 Robot36
 * 
 * 使用相位差分法解调 FM 信号
 */
class FrequencyModulation(bandwidth: Double, sampleRate: Double) {
    
    private var prev = 0f
    private val scale = (sampleRate / (bandwidth * PI)).toFloat()
    
    companion object {
        private const val PI_F = PI.toFloat()
        private const val TWO_PI_F = (2 * PI).toFloat()
    }
    
    private fun wrap(value: Float): Float {
        return when {
            value < -PI_F -> value + TWO_PI_F
            value > PI_F -> value - TWO_PI_F
            else -> value
        }
    }
    
    /**
     * 解调复数基带信号
     * 
     * @param input 复数基带信号
     * @return 归一化频率值 [-1, 1]，对应 [黑色, 白色]
     */
    fun demod(input: Complex): Float {
        val phase = input.arg()
        val delta = wrap(phase - prev)
        prev = phase
        return scale * delta
    }
    
    fun reset() {
        prev = 0f
    }
}
