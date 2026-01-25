package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.PI
import kotlin.math.cosh
import kotlin.math.sqrt

/**
 * Kaiser 窗函数 - 移植自 Robot36
 */
class Kaiser {
    
    /**
     * 计算 Kaiser 窗函数值
     * 
     * @param beta Kaiser 窗的 beta 参数
     * @param index 当前索引
     * @param length 窗长度
     */
    fun window(beta: Double, index: Int, length: Int): Double {
        val n = index - (length - 1) / 2.0
        val N = (length - 1) / 2.0
        val x = n / N
        if (x * x > 1) return 0.0
        return bessel0(beta * sqrt(1 - x * x)) / bessel0(beta)
    }
    
    /**
     * 第一类零阶修正贝塞尔函数的近似
     */
    private fun bessel0(x: Double): Double {
        // 使用近似: I0(x) ≈ cosh(x) for small x
        // 更精确的近似
        if (x == 0.0) return 1.0
        
        var sum = 1.0
        var term = 1.0
        
        for (k in 1..25) {
            term *= (x / (2 * k)) * (x / (2 * k))
            sum += term
            if (term < 1e-10) break
        }
        
        return sum
    }
}
