package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 数控振荡器 (NCO) - 移植自 Robot36
 * 
 * 用于生成指定频率的正弦/余弦信号，用于混频
 */
class Phasor(freq: Double, rate: Double) {
    
    private val value = Complex(1f, 0f)
    private val delta: Complex
    
    init {
        val omega = 2 * PI * freq / rate
        delta = Complex(cos(omega).toFloat(), sin(omega).toFloat())
    }
    
    /**
     * 旋转并返回当前复数值
     */
    fun rotate(): Complex {
        value.mul(delta)
        val absVal = value.abs()
        if (absVal > 0) {
            value.div(absVal)
        }
        return value
    }
}
