package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 指数移动平均 - 移植自 Robot36
 */
class ExponentialMovingAverage {
    
    private var alpha = 1f
    private var prev = 0f
    
    fun avg(input: Float): Float {
        prev = prev * (1 - alpha) + alpha * input
        return prev
    }
    
    fun alpha(value: Double) {
        alpha = value.toFloat()
    }
    
    fun alpha(value: Double, order: Int) {
        alpha(value.pow(1.0 / order))
    }
    
    fun cutoff(freq: Double, rate: Double, order: Int = 1) {
        val x = cos(2 * PI * freq / rate)
        alpha(x - 1 + sqrt(x * (x - 4) + 3), order)
    }
    
    fun reset() {
        prev = 0f
    }
}
