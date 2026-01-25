package com.ham.tools.ui.screens.tools.sstv.decoder

import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.Complex
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.ComplexConvolution
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.Filter
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.FrequencyModulation
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.Kaiser
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.Phasor
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.SchmittTrigger
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.SimpleMovingAverage
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SSTV 解调器 - 移植自 Robot36
 * 
 * 核心信号处理：
 * 1. 混频到基带 (1900 Hz 中心)
 * 2. 低通滤波
 * 3. FM 解调
 * 4. 同步脉冲检测
 */
class SstvDemodulator(private val sampleRate: Int) {
    
    companion object {
        const val SYNC_PULSE_FREQ = 1200.0
        const val BLACK_FREQ = 1500.0
        const val WHITE_FREQ = 2300.0
        
        private const val LOWEST_FREQ = 1000.0
        private const val HIGHEST_FREQ = 2800.0
    }
    
    enum class SyncPulseWidth {
        FiveMilliSeconds,
        NineMilliSeconds,
        TwentyMilliSeconds
    }
    
    // 公开状态
    var syncPulseWidth = SyncPulseWidth.NineMilliSeconds
        private set
    var syncPulseOffset = 0
        private set
    var frequencyOffset = 0f
        private set
    
    // DSP 组件
    private val scanLineBandwidth = WHITE_FREQ - BLACK_FREQ
    private val centerFrequency = (LOWEST_FREQ + HIGHEST_FREQ) / 2
    private val frequencyModulation = FrequencyModulation(scanLineBandwidth, sampleRate.toDouble())
    
    // 基带振荡器（混频用）
    private val baseBandOscillator = Phasor(-centerFrequency, sampleRate.toDouble())
    
    // 低通滤波器
    private val baseBandLowPass: ComplexConvolution
    
    // 同步脉冲检测
    private val syncPulseFilter: SimpleMovingAverage
    private val syncPulseTrigger: SchmittTrigger
    private val syncPulseValueDelay: FloatArray
    private var syncPulseDelayPos = 0
    private val syncPulseFilterDelay: Int
    
    // 同步脉冲宽度阈值（采样数）
    private val syncPulse5msMinSamples: Int
    private val syncPulse5msMaxSamples: Int
    private val syncPulse9msMaxSamples: Int
    private val syncPulse20msMaxSamples: Int
    
    // 同步脉冲计数
    private var syncPulseCounter = 0
    
    // 归一化的同步频率值
    private val syncPulseFrequencyValue: Float
    private val syncPulseFrequencyTolerance: Float
    
    // 当前复数基带信号
    private var baseBand = Complex()
    
    init {
        // 同步脉冲滤波器
        val syncPulseFilterSeconds = 0.005 / 2
        val syncPulseFilterSamples = (syncPulseFilterSeconds * sampleRate).roundToInt() or 1
        syncPulseFilterDelay = (syncPulseFilterSamples - 1) / 2
        syncPulseFilter = SimpleMovingAverage(syncPulseFilterSamples)
        syncPulseValueDelay = FloatArray(syncPulseFilterSamples)
        
        // 同步脉冲宽度阈值
        syncPulse5msMinSamples = (0.005 / 2 * sampleRate).roundToInt()
        syncPulse5msMaxSamples = ((0.005 + 0.009) / 2 * sampleRate).roundToInt()
        syncPulse9msMaxSamples = ((0.009 + 0.020) / 2 * sampleRate).roundToInt()
        syncPulse20msMaxSamples = ((0.020 + 0.005) * sampleRate).roundToInt()
        
        // 低通滤波器
        val cutoffFrequency = (HIGHEST_FREQ - LOWEST_FREQ) / 2
        val baseBandLowPassSeconds = 0.002
        val baseBandLowPassSamples = (baseBandLowPassSeconds * sampleRate).roundToInt() or 1
        baseBandLowPass = ComplexConvolution(baseBandLowPassSamples)
        
        // 初始化滤波器系数
        val kaiser = Kaiser()
        for (i in 0 until baseBandLowPass.length) {
            baseBandLowPass.taps[i] = (kaiser.window(2.0, i, baseBandLowPass.length) *
                    Filter.lowPass(cutoffFrequency, sampleRate.toDouble(), i, baseBandLowPass.length)).toFloat()
        }
        
        // 同步脉冲频率值（归一化）
        syncPulseFrequencyValue = normalizeFrequency(SYNC_PULSE_FREQ).toFloat()
        syncPulseFrequencyTolerance = (50 * 2 / scanLineBandwidth).toFloat()
        
        // 施密特触发器阈值
        val syncPorchFrequency = 1500.0
        val syncHighFrequency = (SYNC_PULSE_FREQ + syncPorchFrequency) / 2
        val syncLowFrequency = (SYNC_PULSE_FREQ + syncHighFrequency) / 2
        syncPulseTrigger = SchmittTrigger(
            normalizeFrequency(syncLowFrequency).toFloat(),
            normalizeFrequency(syncHighFrequency).toFloat()
        )
    }
    
    /**
     * 归一化频率到 [-1, 1]
     */
    private fun normalizeFrequency(frequency: Double): Double {
        return (frequency - centerFrequency) * 2 / scanLineBandwidth
    }
    
    /**
     * 处理音频缓冲区
     * 
     * @param buffer 输入音频（浮点），处理后原地写入解调后的频率值
     * @return 是否检测到同步脉冲
     */
    fun process(buffer: FloatArray): Boolean {
        var syncPulseDetected = false
        
        for (i in buffer.indices) {
            // 设置复数输入
            baseBand.set(buffer[i])
            
            // 混频到基带
            baseBand = baseBandLowPass.push(baseBand.mul(baseBandOscillator.rotate()))
            
            // FM 解调
            val frequencyValue = frequencyModulation.demod(baseBand)
            
            // 同步脉冲滤波
            val syncPulseValue = syncPulseFilter.avg(frequencyValue)
            
            // 延迟的同步脉冲值
            val syncPulseDelayedValue = syncPulseValueDelay[syncPulseDelayPos]
            syncPulseValueDelay[syncPulseDelayPos] = syncPulseValue
            if (++syncPulseDelayPos >= syncPulseValueDelay.size) {
                syncPulseDelayPos = 0
            }
            
            // 原地写入解调后的频率值
            buffer[i] = frequencyValue
            
            // 同步脉冲检测
            if (!syncPulseTrigger.latch(syncPulseValue)) {
                syncPulseCounter++
            } else if (syncPulseCounter < syncPulse5msMinSamples || 
                       syncPulseCounter > syncPulse20msMaxSamples ||
                       abs(syncPulseDelayedValue - syncPulseFrequencyValue) > syncPulseFrequencyTolerance) {
                syncPulseCounter = 0
            } else {
                // 检测到有效同步脉冲
                syncPulseWidth = when {
                    syncPulseCounter < syncPulse5msMaxSamples -> SyncPulseWidth.FiveMilliSeconds
                    syncPulseCounter < syncPulse9msMaxSamples -> SyncPulseWidth.NineMilliSeconds
                    else -> SyncPulseWidth.TwentyMilliSeconds
                }
                syncPulseOffset = i - syncPulseFilterDelay
                frequencyOffset = syncPulseDelayedValue - syncPulseFrequencyValue
                syncPulseDetected = true
                syncPulseCounter = 0
            }
        }
        
        return syncPulseDetected
    }
    
    fun reset() {
        syncPulseFilter.reset()
        syncPulseTrigger.reset()
        syncPulseValueDelay.fill(0f)
        syncPulseDelayPos = 0
        syncPulseCounter = 0
        frequencyModulation.reset()
    }
}
