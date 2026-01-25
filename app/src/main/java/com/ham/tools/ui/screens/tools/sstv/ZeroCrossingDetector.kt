package com.ham.tools.ui.screens.tools.sstv

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 过零检测器 (Zero-Crossing Detector)
 * 
 * 使用过零检测算法进行高效的实时频率估算。
 * 相比 FFT 和 Goertzel 算法，过零检测具有以下优势：
 * 
 * 1. **计算效率极高**: O(N) 复杂度，无需复杂的数学运算
 * 2. **实时性强**: 可逐样本处理，延迟极低
 * 3. **抗噪能力**: 通过滞后阈值和信号强度检测，有效过滤噪音
 * 4. **内存占用小**: 只需保存少量状态变量
 * 
 * ## 原理
 * 
 * 过零检测通过计算信号穿过零点的次数来估算频率：
 * - 正弦波每个周期有 2 次过零（一次上升，一次下降）
 * - 频率 = 过零次数 / (2 × 采样时间)
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * val detector = ZeroCrossingDetector(44100)
 * 
 * // 处理音频采样
 * audioBuffer.forEach { sample ->
 *     val result = detector.processSample(sample)
 *     if (result.isValid) {
 *         val frequency = result.frequency
 *         // 使用频率值...
 *     }
 * }
 * ```
 * 
 * @param sampleRate 采样率 (Hz)
 * @param hysteresis 滞后阈值 (0.0-1.0)，用于消除零点附近的噪音抖动
 * @param minSignalLevel 最小信号电平阈值 (0.0-1.0)
 */
class ZeroCrossingDetector(
    private val sampleRate: Int = 44100,
    private val hysteresis: Float = 0.02f,
    private val minSignalLevel: Float = 0.01f
) {
    companion object {
        /** SSTV 频率范围下限 (Hz) */
        const val MIN_SSTV_FREQ = 1100f
        
        /** SSTV 频率范围上限 (Hz) */
        const val MAX_SSTV_FREQ = 2500f
        
        /** 同步脉冲频率 (Hz) */
        const val SYNC_FREQ = 1200f
        
        /** 黑色频率 (Hz) */
        const val BLACK_FREQ = 1500f
        
        /** 白色频率 (Hz) */
        const val WHITE_FREQ = 2300f
        
        /** 窗口大小 (用于移动平均) */
        private const val WINDOW_SIZE = 8
        
        /** 最小有效周期数（用于可靠频率估算） */
        private const val MIN_CYCLES_FOR_VALID = 2
    }
    
    /**
     * 频率检测结果
     * 
     * @param frequency 估算的频率 (Hz)
     * @param isValid 频率是否有效
     * @param signalStrength 信号强度 (0.0-1.0)
     * @param confidence 置信度 (0.0-1.0)
     * @param isNoise 是否被判定为噪音
     */
    data class DetectionResult(
        val frequency: Float,
        val isValid: Boolean,
        val signalStrength: Float,
        val confidence: Float,
        val isNoise: Boolean
    ) {
        companion object {
            /** 无效/噪音结果 - 返回随机灰度频率 */
            fun noise(signalStrength: Float = 0f): DetectionResult {
                // 80% 概率返回黑色，20% 概率返回随机灰度
                val noiseFreq = if (Random.nextFloat() < 0.8f) {
                    BLACK_FREQ
                } else {
                    BLACK_FREQ + Random.nextFloat() * (WHITE_FREQ - BLACK_FREQ) * 0.3f
                }
                return DetectionResult(
                    frequency = noiseFreq,
                    isValid = false,
                    signalStrength = signalStrength,
                    confidence = 0f,
                    isNoise = true
                )
            }
            
            /** 有效频率结果 */
            fun valid(frequency: Float, signalStrength: Float, confidence: Float): DetectionResult {
                return DetectionResult(
                    frequency = frequency,
                    isValid = true,
                    signalStrength = signalStrength,
                    confidence = confidence,
                    isNoise = false
                )
            }
        }
    }
    
    // 状态变量
    private var previousSample = 0f
    private var isAboveZero = false
    private var zeroCrossingCount = 0
    private var samplesSinceLastCrossing = 0
    private var totalSamplesInWindow = 0
    
    // 用于信号强度计算的 RMS 累积
    private var rmsAccumulator = 0.0
    private var rmsCount = 0
    
    // 周期时间累积 (用于精确频率估算)
    private var periodAccumulator = 0f
    private var periodCount = 0
    
    // 移动平均窗口
    private val frequencyWindow = FloatArray(WINDOW_SIZE)
    private var windowIndex = 0
    private var windowFilled = false
    
    // 滞后阈值 (归一化到 -1.0 ~ 1.0)
    private val hysteresisThreshold = hysteresis
    
    /**
     * 重置检测器状态
     */
    fun reset() {
        previousSample = 0f
        isAboveZero = false
        zeroCrossingCount = 0
        samplesSinceLastCrossing = 0
        totalSamplesInWindow = 0
        rmsAccumulator = 0.0
        rmsCount = 0
        periodAccumulator = 0f
        periodCount = 0
        frequencyWindow.fill(0f)
        windowIndex = 0
        windowFilled = false
    }
    
    /**
     * 处理单个采样点
     * 
     * @param sample 采样值 (-32768 ~ 32767 的 Short 转换为 Float)
     * @return 当前的频率检测结果
     */
    fun processSample(sample: Short): DetectionResult {
        return processSampleNormalized(sample.toFloat() / Short.MAX_VALUE)
    }
    
    /**
     * 处理归一化的采样点 (-1.0 ~ 1.0)
     * 
     * @param normalizedSample 归一化采样值
     * @return 当前的频率检测结果
     */
    fun processSampleNormalized(normalizedSample: Float): DetectionResult {
        // 累积 RMS
        rmsAccumulator += normalizedSample * normalizedSample
        rmsCount++
        
        samplesSinceLastCrossing++
        totalSamplesInWindow++
        
        // 使用滞后阈值检测过零
        val crossedToPositive = !isAboveZero && normalizedSample > hysteresisThreshold
        val crossedToNegative = isAboveZero && normalizedSample < -hysteresisThreshold
        
        if (crossedToPositive || crossedToNegative) {
            // 检测到过零
            isAboveZero = crossedToPositive
            zeroCrossingCount++
            
            // 使用线性插值计算精确的过零位置
            if (previousSample != normalizedSample) {
                val fraction = abs(previousSample) / abs(normalizedSample - previousSample)
                val exactSamples = samplesSinceLastCrossing - 1 + fraction
                
                // 累积半周期时间
                periodAccumulator += exactSamples
                periodCount++
            }
            
            samplesSinceLastCrossing = 0
        }
        
        previousSample = normalizedSample
        
        // 返回基于当前状态的结果
        return getCurrentResult()
    }
    
    /**
     * 处理音频缓冲区并返回频率
     * 
     * 这是批量处理的便捷方法，适合处理音频块。
     * 
     * @param samples PCM 16-bit 采样数组
     * @return 检测结果
     */
    fun processBuffer(samples: ShortArray): DetectionResult {
        // 重置窗口状态
        zeroCrossingCount = 0
        totalSamplesInWindow = 0
        periodAccumulator = 0f
        periodCount = 0
        rmsAccumulator = 0.0
        rmsCount = 0
        
        // 处理所有采样
        for (sample in samples) {
            processSample(sample)
        }
        
        return getCurrentResult()
    }
    
    /**
     * 处理归一化的音频缓冲区
     * 
     * @param samples 归一化采样数组 (-1.0 ~ 1.0)
     * @return 检测结果
     */
    fun processBufferNormalized(samples: FloatArray): DetectionResult {
        zeroCrossingCount = 0
        totalSamplesInWindow = 0
        periodAccumulator = 0f
        periodCount = 0
        rmsAccumulator = 0.0
        rmsCount = 0
        
        for (sample in samples) {
            processSampleNormalized(sample)
        }
        
        return getCurrentResult()
    }
    
    /**
     * 获取当前的检测结果
     */
    private fun getCurrentResult(): DetectionResult {
        // 计算信号强度 (RMS)
        val signalStrength = if (rmsCount > 0) {
            sqrt(rmsAccumulator / rmsCount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // 信号太弱，返回噪音
        if (signalStrength < minSignalLevel) {
            return DetectionResult.noise(signalStrength)
        }
        
        // 检查是否有足够的过零次数
        if (periodCount < MIN_CYCLES_FOR_VALID) {
            return DetectionResult.noise(signalStrength)
        }
        
        // 计算频率
        // 每个周期有 2 次过零，所以频率 = 采样率 / (平均过零间隔 * 2)
        val averagePeriodSamples = periodAccumulator / periodCount
        if (averagePeriodSamples <= 0) {
            return DetectionResult.noise(signalStrength)
        }
        
        // 一个完整周期 = 2 个半周期 (上升沿 + 下降沿)
        val samplesPerCycle = averagePeriodSamples * 2
        val frequency = sampleRate.toFloat() / samplesPerCycle
        
        // 检查频率是否在有效 SSTV 范围内
        if (frequency < MIN_SSTV_FREQ || frequency > MAX_SSTV_FREQ) {
            return DetectionResult.noise(signalStrength)
        }
        
        // 计算置信度 (基于周期数和信号强度)
        val cycleConfidence = (periodCount.toFloat() / 10).coerceIn(0f, 1f)
        val confidence = (cycleConfidence * 0.5f + signalStrength * 0.5f).coerceIn(0f, 1f)
        
        // 更新移动平均窗口
        frequencyWindow[windowIndex] = frequency
        windowIndex = (windowIndex + 1) % WINDOW_SIZE
        if (windowIndex == 0) windowFilled = true
        
        // 计算平滑后的频率
        val smoothedFrequency = if (windowFilled) {
            frequencyWindow.average().toFloat()
        } else {
            var sum = 0f
            var count = 0
            for (i in 0 until windowIndex) {
                sum += frequencyWindow[i]
                count++
            }
            if (count > 0) sum / count else frequency
        }
        
        return DetectionResult.valid(smoothedFrequency, signalStrength, confidence)
    }
    
    /**
     * 将检测到的频率转换为亮度值 (0.0 - 1.0)
     * 
     * @param frequency 频率 (Hz)
     * @return 亮度值
     */
    fun frequencyToLuminance(frequency: Float): Float {
        return ((frequency - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ)).coerceIn(0f, 1f)
    }
    
    /**
     * 检查频率是否为同步脉冲
     * 
     * @param frequency 频率 (Hz)
     * @param tolerance 容差 (Hz)
     * @return 是否为同步脉冲
     */
    fun isSyncPulse(frequency: Float, tolerance: Float = 80f): Boolean {
        return frequency >= (SYNC_FREQ - tolerance) && frequency <= (SYNC_FREQ + tolerance)
    }
}

/**
 * 高精度过零检测器
 * 
 * 使用插值和多窗口分析提供更精确的频率估算。
 * 适用于需要更高精度的场景，但计算开销略大。
 * 
 * @param sampleRate 采样率 (Hz)
 * @param windowSizeMs 分析窗口大小 (毫秒)
 */
class HighPrecisionZeroCrossingDetector(
    private val sampleRate: Int = 44100,
    private val windowSizeMs: Double = 5.0
) {
    private val windowSamples = (sampleRate * windowSizeMs / 1000.0).toInt()
    private val buffer = FloatArray(windowSamples)
    private var bufferIndex = 0
    private var bufferFilled = false
    
    // 基础检测器
    private val baseDetector = ZeroCrossingDetector(sampleRate)
    
    /**
     * 添加采样点
     * 
     * @param sample 采样值
     * @return 如果缓冲区已满，返回频率检测结果，否则返回 null
     */
    fun addSample(sample: Short): ZeroCrossingDetector.DetectionResult? {
        buffer[bufferIndex] = sample.toFloat() / Short.MAX_VALUE
        bufferIndex++
        
        if (bufferIndex >= windowSamples) {
            bufferIndex = 0
            bufferFilled = true
            return analyze()
        }
        
        return null
    }
    
    /**
     * 分析当前缓冲区
     */
    private fun analyze(): ZeroCrossingDetector.DetectionResult {
        return baseDetector.processBufferNormalized(buffer)
    }
    
    /**
     * 重置检测器
     */
    fun reset() {
        buffer.fill(0f)
        bufferIndex = 0
        bufferFilled = false
        baseDetector.reset()
    }
}

/**
 * 逐采样频率检测器
 * 
 * 为每个采样点提供即时的频率估算，适用于需要极低延迟的实时处理。
 * 使用滑动窗口技术，在每个采样点都输出当前的频率估算值。
 * 
 * @param sampleRate 采样率 (Hz)
 * @param windowSizeMs 滑动窗口大小 (毫秒)
 */
class SlidingWindowZeroCrossingDetector(
    private val sampleRate: Int = 44100,
    windowSizeMs: Double = 2.0
) {
    private val windowSamples = (sampleRate * windowSizeMs / 1000.0).toInt()
    
    // 环形缓冲区
    private val sampleBuffer = FloatArray(windowSamples)
    private var writeIndex = 0
    private var samplesWritten = 0
    
    // 过零检测状态
    private var lastCrossingIndex = 0
    private var crossingIntervals = mutableListOf<Int>()
    private val maxIntervals = 20
    
    // RMS 累积
    private var rmsSum = 0.0
    
    /**
     * 处理一个采样点并返回当前频率估算
     * 
     * @param sample 采样值
     * @return 当前频率估算 (Hz)，如果无效则返回表示噪音的频率
     */
    fun process(sample: Short): Float {
        val normalized = sample.toFloat() / Short.MAX_VALUE
        
        // 更新 RMS
        if (samplesWritten >= windowSamples) {
            val oldSample = sampleBuffer[writeIndex]
            rmsSum -= oldSample * oldSample
        }
        rmsSum += normalized * normalized
        
        // 写入缓冲区
        val prevSample = if (samplesWritten > 0) {
            sampleBuffer[(writeIndex - 1 + windowSamples) % windowSamples]
        } else {
            0f
        }
        
        sampleBuffer[writeIndex] = normalized
        
        // 检测过零 (上升沿)
        if (prevSample <= 0 && normalized > 0 && samplesWritten > 0) {
            val interval = samplesWritten - lastCrossingIndex
            if (interval > 5) { // 最小间隔过滤
                crossingIntervals.add(interval)
                if (crossingIntervals.size > maxIntervals) {
                    crossingIntervals.removeAt(0)
                }
            }
            lastCrossingIndex = samplesWritten
        }
        
        writeIndex = (writeIndex + 1) % windowSamples
        samplesWritten++
        
        // 计算信号强度
        val effectiveSamples = minOf(samplesWritten, windowSamples)
        val rms = if (effectiveSamples > 0) {
            kotlin.math.sqrt(rmsSum / effectiveSamples).toFloat()
        } else {
            0f
        }
        
        // 信号太弱
        if (rms < 0.01f) {
            return generateNoiseFrequency()
        }
        
        // 需要至少 2 个间隔才能估算频率
        if (crossingIntervals.size < 2) {
            return generateNoiseFrequency()
        }
        
        // 计算平均周期
        val avgInterval = crossingIntervals.average().toFloat()
        if (avgInterval <= 0) {
            return generateNoiseFrequency()
        }
        
        // 这里仅统计“上升沿”过零，因此间隔对应完整周期
        val frequency = sampleRate.toFloat() / avgInterval
        
        // 验证频率范围
        return if (frequency in ZeroCrossingDetector.MIN_SSTV_FREQ..ZeroCrossingDetector.MAX_SSTV_FREQ) {
            frequency
        } else {
            generateNoiseFrequency()
        }
    }
    
    /**
     * 生成噪音频率 (模拟静态噪点)
     */
    private fun generateNoiseFrequency(): Float {
        return if (Random.nextFloat() < 0.8f) {
            ZeroCrossingDetector.BLACK_FREQ
        } else {
            ZeroCrossingDetector.BLACK_FREQ + 
                Random.nextFloat() * (ZeroCrossingDetector.WHITE_FREQ - ZeroCrossingDetector.BLACK_FREQ) * 0.3f
        }
    }
    
    /**
     * 重置检测器
     */
    fun reset() {
        sampleBuffer.fill(0f)
        writeIndex = 0
        samplesWritten = 0
        lastCrossingIndex = 0
        crossingIntervals.clear()
        rmsSum = 0.0
    }
}
