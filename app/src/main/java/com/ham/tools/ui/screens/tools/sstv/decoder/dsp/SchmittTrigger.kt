package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

/**
 * 施密特触发器 - 移植自 Robot36
 * 
 * 用于同步脉冲检测，带滞回特性
 */
class SchmittTrigger(
    private val low: Float,
    private val high: Float
) {
    private var previous = false
    
    /**
     * 处理输入值，返回锁存状态
     */
    fun latch(input: Float): Boolean {
        if (previous) {
            if (input < low) previous = false
        } else {
            if (input > high) previous = true
        }
        return previous
    }
    
    fun reset() {
        previous = false
    }
}
