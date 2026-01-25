package com.ham.tools.ui.screens.tools.sstv

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Goertzel 算法频率检测器
 * 
 * Goertzel 算法是一种高效的单频检测算法，特别适合检测特定频率的强度。
 * 相比 FFT，它在检测少量特定频率时更加高效，且抗噪能力更强。
 * 
 * 原理:
 * - 使用递归滤波器计算 DFT 的特定频率分量
 * - 计算复杂度为 O(N)，其中 N 是采样点数
 * - 特别适合 VIS 码检测（只需要检测 1100Hz, 1300Hz, 1900Hz）
 * 
 * 使用方式:
 * ```
 * val detector = GoertzelDetector(44100, 1200, 2205) // 检测 1200Hz，50ms 窗口
 * val magnitude = detector.detectMagnitude(audioSamples)
 * ```
 */
class GoertzelDetector(
    private val sampleRate: Int,
    private val targetFrequency: Int,
    private val blockSize: Int
) {
    // Goertzel 系数
    private val k: Int = (0.5 + blockSize * targetFrequency / sampleRate.toDouble()).toInt()
    private val omega: Double = 2.0 * PI * k / blockSize
    private val coeff: Double = 2.0 * cos(omega)
    
    // 归一化因子（用于计算相对幅度）
    private val normFactor: Double = blockSize / 2.0
    
    /**
     * 检测指定频率的幅度（归一化到 0.0 - 1.0）
     * 
     * @param samples 音频采样数据（16-bit PCM）
     * @return 目标频率的幅度值
     */
    fun detectMagnitude(samples: ShortArray): Double {
        if (samples.size < blockSize) {
            return 0.0
        }
        
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        
        // Goertzel 递归计算
        for (i in 0 until blockSize) {
            val sample = samples[i].toDouble() / Short.MAX_VALUE  // 归一化到 -1.0 ~ 1.0
            s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        
        // 计算功率
        val power = s1 * s1 + s2 * s2 - s1 * s2 * coeff
        
        // 返回归一化幅度
        return sqrt(power.coerceAtLeast(0.0)) / normFactor
    }
    
    /**
     * 检测指定频率的功率（平方幅度）
     * 
     * @param samples 音频采样数据
     * @return 目标频率的功率值
     */
    fun detectPower(samples: ShortArray): Double {
        if (samples.size < blockSize) {
            return 0.0
        }
        
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        
        for (i in 0 until blockSize) {
            val sample = samples[i].toDouble() / Short.MAX_VALUE
            s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        
        return (s1 * s1 + s2 * s2 - s1 * s2 * coeff).coerceAtLeast(0.0)
    }
}

/**
 * 多频率 Goertzel 检测器
 * 
 * 同时检测多个频率的强度，用于 VIS 码检测和 SSTV 信号识别。
 * 
 * VIS 码检测需要区分:
 * - 1900 Hz: VIS 起始音
 * - 1300 Hz: VIS 逻辑 0
 * - 1100 Hz: VIS 逻辑 1
 * - 1200 Hz: 同步脉冲
 */
class MultiFrequencyDetector(
    private val sampleRate: Int,
    targetFrequencies: List<Int>,
    blockSizeMs: Double = 30.0  // 默认 30ms 检测窗口
) {
    private val blockSize: Int = (sampleRate * blockSizeMs / 1000.0).toInt()
    
    private val detectors: Map<Int, GoertzelDetector> = targetFrequencies.associateWith { freq ->
        GoertzelDetector(sampleRate, freq, blockSize)
    }
    
    /**
     * 检测所有目标频率的幅度
     * 
     * @param samples 音频采样数据
     * @return 频率到幅度的映射
     */
    fun detectAll(samples: ShortArray): Map<Int, Double> {
        return detectors.mapValues { (_, detector) ->
            detector.detectMagnitude(samples)
        }
    }
    
    /**
     * 找出最强的频率
     * 
     * @param samples 音频采样数据
     * @param threshold 最小幅度阈值
     * @return 最强频率及其幅度，如果所有频率都低于阈值则返回 null
     */
    fun detectStrongest(samples: ShortArray, threshold: Double = 0.1): Pair<Int, Double>? {
        val magnitudes = detectAll(samples)
        return magnitudes.maxByOrNull { it.value }
            ?.takeIf { it.value >= threshold }
            ?.let { it.key to it.value }
    }
    
    /**
     * 检测是否存在特定频率信号
     * 
     * @param samples 音频采样数据
     * @param frequency 目标频率
     * @param threshold 幅度阈值
     * @return 如果目标频率幅度超过阈值且为最强频率，返回 true
     */
    fun isFrequencyDominant(samples: ShortArray, frequency: Int, threshold: Double = 0.15): Boolean {
        val magnitudes = detectAll(samples)
        val targetMag = magnitudes[frequency] ?: return false
        
        if (targetMag < threshold) return false
        
        // 检查是否为最强频率
        return magnitudes.all { (freq, mag) ->
            freq == frequency || mag < targetMag
        }
    }
    
    /**
     * 获取检测窗口大小（采样点数）
     */
    fun getBlockSize(): Int = blockSize
}

/**
 * VIS 码检测器
 * 
 * 使用 Goertzel 算法检测和解码 VIS (Vertical Interval Signaling) 码。
 * 
 * VIS 码结构:
 * 1. Leader Tone: 300ms @ 1900Hz
 * 2. Break: 10ms @ 1200Hz
 * 3. Leader Tone: 300ms @ 1900Hz
 * 4. VIS Start Bit: 30ms @ 1200Hz
 * 5. 8 Data Bits (7 bits + 1 parity): 每位 30ms @ 1100Hz(1) 或 1300Hz(0)
 * 6. VIS Stop Bit: 30ms @ 1200Hz
 */
class VisCodeDetector(
    private val sampleRate: Int = 44100
) {
    companion object {
        // VIS 相关频率
        const val FREQ_VIS_LEADER = 1900  // Leader tone
        const val FREQ_VIS_BREAK = 1200   // Break/Sync
        const val FREQ_VIS_BIT_1 = 1100   // 逻辑 1
        const val FREQ_VIS_BIT_0 = 1300   // 逻辑 0
        
        // 时序常量 (毫秒)
        const val LEADER_DURATION_MS = 300.0
        const val BREAK_DURATION_MS = 10.0
        const val BIT_DURATION_MS = 30.0
        
        // 检测阈值
        const val MAGNITUDE_THRESHOLD = 0.12
        const val DOMINANT_RATIO = 1.5  // 主频率必须是其他频率的 1.5 倍
    }
    
    /**
     * VIS 检测状态
     */
    enum class VisState {
        IDLE,           // 等待 Leader Tone
        LEADER_1,       // 检测到第一个 Leader Tone
        BREAK,          // 检测到 Break
        LEADER_2,       // 检测到第二个 Leader Tone
        START_BIT,      // 检测到 Start Bit
        DATA_BITS,      // 读取数据位
        COMPLETE        // VIS 码完成
    }
    
    // Goertzel 检测器（30ms 窗口，与 VIS 位时长匹配）
    private val detector = MultiFrequencyDetector(
        sampleRate = sampleRate,
        targetFrequencies = listOf(FREQ_VIS_LEADER, FREQ_VIS_BREAK, FREQ_VIS_BIT_1, FREQ_VIS_BIT_0),
        blockSizeMs = BIT_DURATION_MS
    )
    
    // 当前状态
    private var state = VisState.IDLE
    private var stateStartSample = 0L
    private var totalSamplesProcessed = 0L
    
    // Leader 检测计数
    private var leaderSampleCount = 0
    private var breakSampleCount = 0
    
    // VIS 数据
    private var visBits = mutableListOf<Boolean>()
    private var currentBitSamples = 0
    
    // 检测结果
    private var detectedVisCode: Int? = null
    
    /**
     * 处理音频采样块
     * 
     * @param samples 音频采样数据
     * @return 如果检测到完整的 VIS 码，返回 VIS 值，否则返回 null
     */
    fun processSamples(samples: ShortArray): Int? {
        val magnitudes = detector.detectAll(samples)
        val strongest = findStrongestFrequency(magnitudes)
        
        totalSamplesProcessed += samples.size
        
        when (state) {
            VisState.IDLE -> processIdle(strongest, samples.size)
            VisState.LEADER_1 -> processLeader1(strongest, samples.size)
            VisState.BREAK -> processBreak(strongest, samples.size)
            VisState.LEADER_2 -> processLeader2(strongest, samples.size)
            VisState.START_BIT -> processStartBit(strongest, samples.size)
            VisState.DATA_BITS -> processDataBits(strongest, samples.size, magnitudes)
            VisState.COMPLETE -> { /* 等待重置 */ }
        }
        
        return if (state == VisState.COMPLETE) detectedVisCode else null
    }
    
    /**
     * 处理 IDLE 状态 - 等待 1900Hz Leader Tone
     */
    private fun processIdle(strongest: Pair<Int, Double>?, sampleCount: Int) {
        if (strongest?.first == FREQ_VIS_LEADER && strongest.second > MAGNITUDE_THRESHOLD) {
            leaderSampleCount = sampleCount
            state = VisState.LEADER_1
            stateStartSample = totalSamplesProcessed
        }
    }
    
    /**
     * 处理 LEADER_1 状态 - 持续检测 1900Hz
     */
    private fun processLeader1(strongest: Pair<Int, Double>?, sampleCount: Int) {
        val leaderMinSamples = (LEADER_DURATION_MS * 0.5 * sampleRate / 1000).toInt()  // 至少 150ms
        
        if (strongest?.first == FREQ_VIS_LEADER && strongest.second > MAGNITUDE_THRESHOLD) {
            leaderSampleCount += sampleCount
        } else if (strongest?.first == FREQ_VIS_BREAK && leaderSampleCount >= leaderMinSamples) {
            // 检测到 Break
            breakSampleCount = sampleCount
            state = VisState.BREAK
            stateStartSample = totalSamplesProcessed
        } else {
            // 信号丢失，重置
            reset()
        }
    }
    
    /**
     * 处理 BREAK 状态 - 检测 1200Hz Break
     */
    private fun processBreak(strongest: Pair<Int, Double>?, sampleCount: Int) {
        if (strongest?.first == FREQ_VIS_BREAK) {
            breakSampleCount += sampleCount
        } else if (strongest?.first == FREQ_VIS_LEADER) {
            // 进入第二个 Leader
            leaderSampleCount = sampleCount
            state = VisState.LEADER_2
            stateStartSample = totalSamplesProcessed
        } else {
            reset()
        }
    }
    
    /**
     * 处理 LEADER_2 状态 - 第二个 1900Hz Leader
     */
    private fun processLeader2(strongest: Pair<Int, Double>?, sampleCount: Int) {
        val leaderMinSamples = (LEADER_DURATION_MS * 0.5 * sampleRate / 1000).toInt()
        
        if (strongest?.first == FREQ_VIS_LEADER && strongest.second > MAGNITUDE_THRESHOLD) {
            leaderSampleCount += sampleCount
        } else if (strongest?.first == FREQ_VIS_BREAK && leaderSampleCount >= leaderMinSamples) {
            // 检测到 Start Bit
            currentBitSamples = sampleCount
            state = VisState.START_BIT
            stateStartSample = totalSamplesProcessed
        } else {
            reset()
        }
    }
    
    /**
     * 处理 START_BIT 状态 - VIS 起始位 (1200Hz)
     */
    private fun processStartBit(strongest: Pair<Int, Double>?, sampleCount: Int) {
        val bitSamples = (BIT_DURATION_MS * sampleRate / 1000).toInt()
        
        if (strongest?.first == FREQ_VIS_BREAK) {
            currentBitSamples += sampleCount
            
            if (currentBitSamples >= bitSamples * 0.8) {
                // Start bit 完成，开始读取数据位
                visBits.clear()
                currentBitSamples = 0
                state = VisState.DATA_BITS
                stateStartSample = totalSamplesProcessed
            }
        } else if (strongest?.first == FREQ_VIS_BIT_0 || strongest?.first == FREQ_VIS_BIT_1) {
            // 已经进入数据位
            visBits.clear()
            currentBitSamples = sampleCount
            state = VisState.DATA_BITS
            
            // 记录第一个数据位
            if (strongest.first == FREQ_VIS_BIT_1) {
                visBits.add(true)
            } else {
                visBits.add(false)
            }
        } else {
            reset()
        }
    }
    
    /**
     * 处理 DATA_BITS 状态 - 读取 8 个数据位
     */
    private fun processDataBits(strongest: Pair<Int, Double>?, sampleCount: Int, magnitudes: Map<Int, Double>) {
        val bitSamples = (BIT_DURATION_MS * sampleRate / 1000).toInt()
        
        currentBitSamples += sampleCount
        
        // 当累积足够的采样时，确定当前位的值
        if (currentBitSamples >= bitSamples * 0.8 && visBits.size < 8) {
            val bit1Mag = magnitudes[FREQ_VIS_BIT_1] ?: 0.0
            val bit0Mag = magnitudes[FREQ_VIS_BIT_0] ?: 0.0
            
            // 根据哪个频率更强来确定位值
            if (bit1Mag > bit0Mag && bit1Mag > MAGNITUDE_THRESHOLD) {
                visBits.add(true)  // 逻辑 1
            } else if (bit0Mag > MAGNITUDE_THRESHOLD) {
                visBits.add(false) // 逻辑 0
            }
            
            currentBitSamples = 0
        }
        
        // 检查是否收集完所有位
        if (visBits.size >= 8) {
            // 解析 VIS 码 (7 位数据 + 1 位奇偶校验)
            detectedVisCode = decodeVisBits()
            state = VisState.COMPLETE
        }
        
        // 超时检测
        val maxDataDuration = (BIT_DURATION_MS * 10 * sampleRate / 1000).toLong()
        if (totalSamplesProcessed - stateStartSample > maxDataDuration) {
            // 超时，尝试用已收集的位解码
            if (visBits.size >= 7) {
                detectedVisCode = decodeVisBits()
                state = VisState.COMPLETE
            } else {
                reset()
            }
        }
    }
    
    /**
     * 解码 VIS 位序列
     * 
     * VIS 码格式: D0 D1 D2 D3 D4 D5 D6 P (LSB first)
     * 其中 D0-D6 是 7 位数据，P 是奇偶校验位
     */
    private fun decodeVisBits(): Int {
        if (visBits.size < 7) return -1
        
        var visCode = 0
        for (i in 0 until 7) {
            if (visBits[i]) {
                visCode = visCode or (1 shl i)
            }
        }
        
        return visCode
    }
    
    /**
     * 找出最强的频率
     */
    private fun findStrongestFrequency(magnitudes: Map<Int, Double>): Pair<Int, Double>? {
        val strongest = magnitudes.maxByOrNull { it.value } ?: return null
        
        if (strongest.value < MAGNITUDE_THRESHOLD) return null
        
        // 检查是否显著强于其他频率
        val secondStrongest = magnitudes.filter { it.key != strongest.key }.maxByOrNull { it.value }
        if (secondStrongest != null && secondStrongest.value > strongest.value / DOMINANT_RATIO) {
            // 信号不够清晰
            return null
        }
        
        return strongest.key to strongest.value
    }
    
    /**
     * 重置检测器状态
     */
    fun reset() {
        state = VisState.IDLE
        stateStartSample = 0L
        leaderSampleCount = 0
        breakSampleCount = 0
        visBits.clear()
        currentBitSamples = 0
        detectedVisCode = null
    }
    
    /**
     * 获取当前状态
     */
    fun getState(): VisState = state
    
    /**
     * 获取检测到的 VIS 码
     */
    fun getDetectedVisCode(): Int? = detectedVisCode
    
    /**
     * 获取 Goertzel 检测窗口大小
     */
    fun getBlockSize(): Int = detector.getBlockSize()
}

/**
 * 频率强度分析器
 * 
 * 用于实时分析 SSTV 信号频率，支持同时监测多个关键频率的强度。
 * 可用于调谐指示器和信号质量评估。
 */
class FrequencyAnalyzer(
    private val sampleRate: Int = 44100,
    blockSizeMs: Double = 20.0  // 20ms 检测窗口，平衡速度和精度
) {
    // SSTV 关键频率
    private val sstFrequencies = listOf(
        1200,  // 同步
        1500,  // 黑色
        1900,  // VIS
        2300   // 白色
    )
    
    private val detector = MultiFrequencyDetector(
        sampleRate = sampleRate,
        targetFrequencies = sstFrequencies,
        blockSizeMs = blockSizeMs
    )
    
    /**
     * 频率分析结果
     */
    data class AnalysisResult(
        val dominantFrequency: Int?,
        val dominantMagnitude: Double,
        val frequencyMagnitudes: Map<Int, Double>,
        val estimatedFrequency: Float,
        val signalStrength: Double
    )
    
    /**
     * 分析音频采样
     * 
     * @param samples 音频采样数据
     * @return 分析结果
     */
    fun analyze(samples: ShortArray): AnalysisResult {
        val magnitudes = detector.detectAll(samples)
        val strongest = magnitudes.maxByOrNull { it.value }
        
        // 估算实际频率（基于幅度加权）
        val estimatedFreq = estimateFrequency(magnitudes)
        
        // 计算总体信号强度
        val signalStrength = magnitudes.values.maxOrNull() ?: 0.0
        
        return AnalysisResult(
            dominantFrequency = strongest?.takeIf { it.value > 0.1 }?.key,
            dominantMagnitude = strongest?.value ?: 0.0,
            frequencyMagnitudes = magnitudes,
            estimatedFrequency = estimatedFreq,
            signalStrength = signalStrength
        )
    }
    
    /**
     * 基于幅度加权估算频率
     * 
     * 使用抛物线插值在检测到的频率点之间估算实际频率
     */
    private fun estimateFrequency(magnitudes: Map<Int, Double>): Float {
        val sortedFreqs = magnitudes.entries.sortedByDescending { it.value }
        
        if (sortedFreqs.isEmpty() || sortedFreqs[0].value < 0.05) {
            return 0f
        }
        
        // 简单加权平均
        var weightedSum = 0.0
        var weightSum = 0.0
        
        for ((freq, mag) in sortedFreqs) {
            if (mag > 0.05) {
                val weight = mag * mag  // 平方加权
                weightedSum += freq * weight
                weightSum += weight
            }
        }
        
        return if (weightSum > 0) (weightedSum / weightSum).toFloat() else 0f
    }
    
    /**
     * 获取检测窗口大小
     */
    fun getBlockSize(): Int = detector.getBlockSize()
}
