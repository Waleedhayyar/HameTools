package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

/**
 * 复数卷积滤波器 - 移植自 Robot36
 * 
 * 用于低通滤波基带信号
 */
class ComplexConvolution(val length: Int) {
    
    val taps = FloatArray(length)
    private val real = FloatArray(length)
    private val imag = FloatArray(length)
    private val sum = Complex()
    private var pos = 0
    
    /**
     * 推入新的复数值并返回卷积结果
     */
    fun push(input: Complex): Complex {
        real[pos] = input.real
        imag[pos] = input.imag
        
        if (++pos >= length) pos = 0
        
        sum.real = 0f
        sum.imag = 0f
        
        var p = pos
        for (tap in taps) {
            sum.real += tap * real[p]
            sum.imag += tap * imag[p]
            if (++p >= length) p = 0
        }
        
        return sum
    }
}
