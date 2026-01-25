package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

/**
 * 简单移动平均 - 移植自 Robot36
 */
class SimpleMovingAverage(val length: Int) {
    
    private val buffer = FloatArray(length)
    private var pos = 0
    private var sum = 0f
    
    /**
     * 推入新值并返回平均值
     */
    fun avg(input: Float): Float {
        sum -= buffer[pos]
        sum += input
        buffer[pos] = input
        if (++pos >= length) pos = 0
        return sum / length
    }
    
    fun reset() {
        buffer.fill(0f)
        pos = 0
        sum = 0f
    }
}
