package com.ham.tools.ui.screens.tools.sstv.decoder.dsp

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 复数类 - 移植自 Robot36
 */
class Complex(
    var real: Float = 0f,
    var imag: Float = 0f
) {
    
    fun set(other: Complex): Complex {
        real = other.real
        imag = other.imag
        return this
    }
    
    fun set(real: Float, imag: Float = 0f): Complex {
        this.real = real
        this.imag = imag
        return this
    }
    
    fun norm(): Float = real * real + imag * imag
    
    fun abs(): Float = sqrt(norm())
    
    fun arg(): Float = atan2(imag, real)
    
    fun polar(a: Float, b: Float): Complex {
        real = a * cos(b)
        imag = a * sin(b)
        return this
    }
    
    fun conj(): Complex {
        imag = -imag
        return this
    }
    
    fun add(other: Complex): Complex {
        real += other.real
        imag += other.imag
        return this
    }
    
    fun sub(other: Complex): Complex {
        real -= other.real
        imag -= other.imag
        return this
    }
    
    fun mul(value: Float): Complex {
        real *= value
        imag *= value
        return this
    }
    
    fun mul(other: Complex): Complex {
        val tmp = real * other.real - imag * other.imag
        imag = real * other.imag + imag * other.real
        real = tmp
        return this
    }
    
    fun div(value: Float): Complex {
        real /= value
        imag /= value
        return this
    }
    
    fun div(other: Complex): Complex {
        val den = other.norm()
        val tmp = (real * other.real + imag * other.imag) / den
        imag = (imag * other.real - real * other.imag) / den
        real = tmp
        return this
    }
    
    fun copy(): Complex = Complex(real, imag)
}
