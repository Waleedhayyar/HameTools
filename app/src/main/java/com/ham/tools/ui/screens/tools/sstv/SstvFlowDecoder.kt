package com.ham.tools.ui.screens.tools.sstv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.ham.tools.ui.screens.tools.sstv.strategies.Martin1Strategy
import com.ham.tools.ui.screens.tools.sstv.strategies.Martin2Strategy
import com.ham.tools.ui.screens.tools.sstv.strategies.Robot36Strategy
import com.ham.tools.ui.screens.tools.sstv.strategies.Robot72Strategy
import kotlin.random.Random

/**
 * SSTV 流式解码器 - 自由运行架构 (Free-Running Architecture)
 * 
 * 基于 Kotlin Flow 的实时流式处理 SSTV 解码器。
 * 
 * ## 核心特性: 自由运行 (Free-Running)
 * 
 * **关键原则**: 解码器永远不会停止！扫描线永远以固定速度往下扫。
 * 
 * 1. **时间驱动**: 根据当前模式的理论时间（如 Robot 36 每行 150ms）自动强制换行
 * 2. **同步对齐**: 检测到 1200Hz 同步信号时，重置行计时器进行对齐
 * 3. **噪音容忍**: 即使信号是噪音，也必须产出像素数据（映射为随机灰度或黑色）
 * 4. **流畅扫描**: 保证屏幕上的扫描线永远以固定速度移动，不会卡顿
 * 
 * ## 状态机设计
 * 
 * ```
 *     ┌──────────────────────────────────────────────────────────────┐
 *     │                    FREE_RUNNING 模式                         │
 *     │                                                              │
 *     │   ┌─────────┐    时间到达     ┌─────────────────────────┐   │
 *     │   │ 累积    │ ──────────────▶ │ 强制换行 + emit 扫描线   │   │
 *     │   │ 音频    │                 │                         │   │
 *     │   │ 数据    │ ◀────────────── │ 重置缓冲区, 下一行       │   │
 *     │   └─────────┘    继续解码     └─────────────────────────┘   │
 *     │        │                               ▲                     │
 *     │        │ 检测到 1200Hz                 │                     │
 *     │        ▼                               │                     │
 *     │   ┌─────────────────────────────────────┐                   │
 *     │   │ 同步对齐: 重置行计时器               │                   │
 *     │   │ (不影响扫描速度，只调整相位)         │                   │
 *     │   └─────────────────────────────────────┘                   │
 *     └──────────────────────────────────────────────────────────────┘
 * ```
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * val decoder = SstvFlowDecoder()
 * decoder.setStrategy(Robot36Strategy())
 * 
 * // 收集解码结果 - 扫描线永远以固定速度到达
 * decoder.scanLineFlow.collect { scanLine ->
 *     updateUI(scanLine.lineNumber, scanLine.pixels)
 * }
 * 
 * // 开始解码
 * decoder.decode(audioFlow)
 * ```
 */
class SstvFlowDecoder {
    
    companion object {
        private const val TAG = "SstvFlowDecoder"
        
        /** 默认采样率 */
        const val DEFAULT_SAMPLE_RATE = 44100
        
        /** 频率检测容差 */
        private const val FREQ_TOLERANCE = 80
        
        /** 校准信号最少持续时间 (ms) */
        private const val CALIBRATION_MIN_DURATION_MS = 50
        
        /** 同步脉冲最小时长 (ms) */
        private const val SYNC_MIN_DURATION_MS = 3.0
        
        /** Goertzel 检测窗口大小 (ms) */
        private const val GOERTZEL_WINDOW_MS = 20.0
        
        /** 噪音频率阈值 - 低于此值视为无效信号 */
        private const val NOISE_FREQ_MIN = 800f
        
        /** 噪音频率阈值 - 高于此值视为无效信号 */
        private const val NOISE_FREQ_MAX = 2600f
    }
    
    /**
     * 解码器状态
     */
    enum class DecoderState(val displayName: String) {
        IDLE("空闲 - 等待信号"),
        CALIBRATING("校准中 - 检测同步信号"),
        FREE_RUNNING("自由运行 - 解码中"),
        COMPLETE("解码完成")
    }
    
    // ==================== 策略配置 ====================
    
    /** 当前解码策略 */
    private var currentStrategy: SstvModeStrategy = Martin1Strategy()
    
    /** 采样率 */
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE
    
    // ==================== 状态流 ====================
    
    private val _state = MutableStateFlow(DecoderState.IDLE)
    /** 解码器当前状态 */
    val state: StateFlow<DecoderState> = _state.asStateFlow()
    
    private val _currentLine = MutableStateFlow(0)
    /** 当前正在解码的行号 */
    val currentLine: StateFlow<Int> = _currentLine.asStateFlow()
    
    private val _modeName = MutableStateFlow("")
    /** 当前模式名称 */
    val modeName: StateFlow<String> = _modeName.asStateFlow()
    
    // ==================== 核心输出流 ====================
    
    /**
     * 扫描线输出流
     * 
     * **核心输出**: 每解完一行立即 emit `SstvScanLine`。
     * 
     * 自由运行模式下，扫描线会以固定的时间间隔输出：
     * - Robot 36: 约每 150ms 一行
     * - Martin 1: 约每 446ms 一行
     * 
     * 即使是噪音，也会输出（随机灰度像素）
     */
    private val _scanLineFlow = MutableSharedFlow<SstvScanLine>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /** 公开的扫描线 SharedFlow */
    val scanLineFlow: SharedFlow<SstvScanLine> = _scanLineFlow.asSharedFlow()
    
    // ==================== 内部状态 ====================
    
    private var decoderScope: CoroutineScope? = null
    
    // 行音频缓冲区 (用于累积一行的频率数据)
    private var lineAudioBuffer = FloatArray(0)
    private var lineAudioIndex = 0
    
    // 频率检测器
    private lateinit var frequencyAnalyzer: FrequencyAnalyzer
    
    // ==================== 自由运行时序控制 ====================
    
    /** 当前行号 (核心计数器) */
    private var currentLineNumber = 0
    
    /** 行开始时间戳 (ms) */
    private var lineStartTimeMs = 0L
    
    /** 总处理采样数 */
    private var totalSamplesProcessed = 0L
    
    /** 当前行已处理的采样数 */
    private var lineSamplesProcessed = 0
    
    /** 同步脉冲检测开始时间 */
    private var syncPulseStartMs = 0L
    
    /** 是否在同步脉冲期间 */
    private var inSyncPulse = false
    
    /** 校准开始时间 */
    private var calibrationStartMs = 0L
    
    /** 上次同步对齐的行号 */
    private var lastSyncAlignedLine = -1
    
    // ==================== 公开 API ====================
    
    /**
     * 设置解码策略
     */
    fun setStrategy(strategy: SstvModeStrategy) {
        currentStrategy = strategy
        _modeName.value = strategy.modeName
        initializeBuffers()
        Log.d(TAG, "切换策略: ${strategy.modeName} (VIS: ${strategy.visCode})")
        Log.d(TAG, "理论行时间: ${strategy.scanLineTimeMs}ms")
    }
    
    /**
     * 根据 VIS 码设置策略
     */
    fun setStrategyByVisCode(visCode: Int): Boolean {
        val strategy = getStrategyByVisCode(visCode)
        return if (strategy != null) {
            setStrategy(strategy)
            true
        } else {
            Log.w(TAG, "未知的 VIS 码: $visCode")
            false
        }
    }
    
    /**
     * 获取当前策略
     */
    fun getStrategy(): SstvModeStrategy = currentStrategy
    
    /**
     * 设置采样率
     */
    fun setSampleRate(rate: Int) {
        sampleRate = rate
        frequencyAnalyzer = FrequencyAnalyzer(sampleRate, GOERTZEL_WINDOW_MS)
        initializeBuffers()
    }
    
    /**
     * 核心解码方法 - 自由运行模式
     * 
     * **自由运行原则**:
     * 1. 一旦开始，扫描线永远以固定速度移动
     * 2. 同步信号只用于对齐，不会阻止扫描
     * 3. 噪音也会产出像素数据
     * 4. 根据理论时间自动强制换行
     * 
     * @param audioFlow 音频数据流 (ShortArray PCM 16-bit)
     */
    suspend fun decode(audioFlow: Flow<ShortArray>) {
        Log.d(TAG, "========== 开始 SSTV 自由运行解码 ==========")
        Log.d(TAG, "模式: ${currentStrategy.modeName}")
        Log.d(TAG, "分辨率: ${currentStrategy.width}x${currentStrategy.height}")
        Log.d(TAG, "采样率: $sampleRate Hz")
        Log.d(TAG, "每行理论时间: ${currentStrategy.scanLineTimeMs}ms")
        Log.d(TAG, "每行采样数: ${currentStrategy.getSamplesPerLine(sampleRate)}")
        
        // 初始化
        reset()
        _state.value = DecoderState.IDLE
        
        // 创建解码器作用域
        decoderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // 收集音频流并处理
        audioFlow.collect { samples ->
            if (decoderScope?.isActive == true) {
                processAudioChunkFreeRunning(samples)
            }
        }
    }
    
    /**
     * 停止解码
     */
    fun stop() {
        decoderScope?.cancel()
        decoderScope = null
        _state.value = DecoderState.IDLE
        Log.d(TAG, "========== 解码器停止 ==========")
    }
    
    /**
     * 重置解码器
     */
    fun reset() {
        _state.value = DecoderState.IDLE
        _currentLine.value = 0
        currentLineNumber = 0
        
        lineStartTimeMs = 0L
        totalSamplesProcessed = 0L
        lineSamplesProcessed = 0
        lineAudioIndex = 0
        
        syncPulseStartMs = 0L
        inSyncPulse = false
        calibrationStartMs = 0L
        lastSyncAlignedLine = -1
        
        initializeBuffers()
        
        Log.d(TAG, "解码器已重置")
    }
    
    // ==================== 自由运行处理逻辑 ====================
    
    /**
     * 初始化缓冲区
     */
    private fun initializeBuffers() {
        val samplesPerLine = currentStrategy.getSamplesPerLine(sampleRate)
        lineAudioBuffer = FloatArray(samplesPerLine)
        lineAudioIndex = 0
        lineSamplesProcessed = 0
        
        if (!::frequencyAnalyzer.isInitialized) {
            frequencyAnalyzer = FrequencyAnalyzer(sampleRate, GOERTZEL_WINDOW_MS)
        }
        
        Log.d(TAG, "缓冲区初始化: 每行 $samplesPerLine 采样点")
    }
    
    /**
     * 自由运行模式处理音频块
     * 
     * 核心逻辑:
     * 1. 累积音频数据
     * 2. 检测同步信号进行对齐（可选）
     * 3. 当达到理论时间时，强制换行并 emit 扫描线
     * 4. 即使没有同步信号，也要继续前进
     */
    private suspend fun processAudioChunkFreeRunning(samples: ShortArray) {
        // 分析频率
        val analysisResult = frequencyAnalyzer.analyze(samples)
        val frequency = analysisResult.estimatedFrequency
        val signalStrength = analysisResult.signalStrength
        
        // 计算当前时间
        val currentTimeMs = (totalSamplesProcessed * 1000.0 / sampleRate).toLong()
        totalSamplesProcessed += samples.size
        
        when (_state.value) {
            DecoderState.IDLE -> processIdleFreeRunning(frequency, currentTimeMs, samples)
            DecoderState.CALIBRATING -> processCalibratingFreeRunning(frequency, currentTimeMs, samples)
            DecoderState.FREE_RUNNING -> processFreeRunning(frequency, currentTimeMs, samples, signalStrength)
            DecoderState.COMPLETE -> { /* 解码完成 */ }
        }
    }
    
    /**
     * IDLE 状态 - 等待信号开始
     */
    private fun processIdleFreeRunning(frequency: Float, timeMs: Long, samples: ShortArray) {
        // 检测同步频率 (1200Hz) 或有效图像数据频率
        if (isFrequencyInRange(frequency, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            calibrationStartMs = timeMs
            _state.value = DecoderState.CALIBRATING
            Log.d(TAG, "检测到校准信号: ${frequency.toInt()} Hz")
        } else if (isValidImageFrequency(frequency)) {
            // 检测到有效图像数据，直接开始自由运行
            Log.d(TAG, "检测到图像数据，直接开始自由运行: ${frequency.toInt()} Hz")
            startFreeRunning(timeMs)
            accumulateAudioWithFrequency(frequency, samples)
        }
    }
    
    /**
     * CALIBRATING 状态 - 短暂校准后立即进入自由运行
     */
    private fun processCalibratingFreeRunning(frequency: Float, timeMs: Long, samples: ShortArray) {
        val duration = timeMs - calibrationStartMs
        
        if (isFrequencyInRange(frequency, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            // 继续校准
            if (duration >= CALIBRATION_MIN_DURATION_MS) {
                Log.d(TAG, "校准完成 (${duration}ms)，开始自由运行")
                startFreeRunning(timeMs)
            }
        } else {
            // 校准信号结束或检测到其他信号
            if (duration >= SYNC_MIN_DURATION_MS) {
                Log.d(TAG, "校准信号结束，开始自由运行")
                startFreeRunning(timeMs)
                accumulateAudioWithFrequency(frequency, samples)
            } else {
                // 太短，回到 IDLE
                _state.value = DecoderState.IDLE
            }
        }
    }
    
    /**
     * 开始自由运行模式
     */
    private fun startFreeRunning(timeMs: Long) {
        _state.value = DecoderState.FREE_RUNNING
        currentLineNumber = 0
        lineStartTimeMs = timeMs
        lineSamplesProcessed = 0
        lineAudioIndex = 0
        lineAudioBuffer.fill(0f)
        
        Log.d(TAG, "========== 进入自由运行模式 ==========")
    }
    
    /**
     * FREE_RUNNING 状态 - 核心自由运行逻辑
     * 
     * **关键**: 永不停止！根据理论时间强制换行
     */
    private suspend fun processFreeRunning(
        frequency: Float, 
        timeMs: Long, 
        samples: ShortArray,
        signalStrength: Double
    ) {
        val samplesPerLine = currentStrategy.getSamplesPerLine(sampleRate)
        
        // ========== 1. 同步信号检测与对齐 ==========
        checkSyncAndAlign(frequency, timeMs)
        
        // ========== 2. 累积音频数据 ==========
        // 处理频率：如果是噪音，生成随机/黑色值
        val processedFrequency = processFrequencyWithNoise(frequency, signalStrength)
        accumulateAudioWithFrequency(processedFrequency, samples)
        
        // 更新行采样计数
        lineSamplesProcessed += samples.size
        
        // ========== 3. 检查是否需要强制换行 ==========
        // 关键点：根据理论时间自动强制换行，不等待同步信号！
        if (lineSamplesProcessed >= samplesPerLine) {
            // 达到理论采样数，强制完成当前行
            forceCompleteLine()
        }
    }
    
    /**
     * 检测同步信号并进行对齐
     * 
     * 同步信号 (1200Hz) 用于对齐行起始位置，但不会阻止扫描继续
     */
    private fun checkSyncAndAlign(frequency: Float, timeMs: Long) {
        if (isFrequencyInRange(frequency, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            if (!inSyncPulse) {
                // 开始检测同步脉冲
                syncPulseStartMs = timeMs
                inSyncPulse = true
            }
        } else {
            if (inSyncPulse) {
                // 同步脉冲结束
                val pulseDuration = timeMs - syncPulseStartMs
                
                // 检查是否是有效的同步脉冲（接近理论时长）
                val expectedSyncMs = currentStrategy.syncPulseMs
                if (pulseDuration >= expectedSyncMs * 0.5 && pulseDuration <= expectedSyncMs * 2) {
                    // 有效同步脉冲 - 执行对齐
                    performSyncAlignment(timeMs, pulseDuration.toDouble())
                }
                
                inSyncPulse = false
            }
        }
    }
    
    /**
     * 执行同步对齐
     * 
     * 重置行计时器，调整相位，但不影响扫描速度
     */
    private fun performSyncAlignment(timeMs: Long, pulseDuration: Double) {
        // 避免重复对齐同一行
        if (currentLineNumber == lastSyncAlignedLine) {
            return
        }
        
        // 计算当前行的进度
        val samplesPerLine = currentStrategy.getSamplesPerLine(sampleRate)
        val progress = lineSamplesProcessed.toFloat() / samplesPerLine
        
        // 如果当前行已经接近完成（>90%），等待下一行再对齐
        if (progress > 0.9f) {
            return
        }
        
        // 如果当前行刚开始（<10%），执行对齐
        if (progress < 0.1f) {
            // 重置行计时器
            lineStartTimeMs = timeMs
            lineSamplesProcessed = 0
            lineAudioIndex = 0
            lineAudioBuffer.fill(0f)
            lastSyncAlignedLine = currentLineNumber
            
            Log.d(TAG, "行 $currentLineNumber 同步对齐 (脉冲: ${String.format("%.1f", pulseDuration)}ms)")
        }
    }
    
    /**
     * 处理频率，处理噪音情况
     * 
     * 如果频率是噪音（超出 SSTV 频率范围），映射为随机灰度或黑色
     * 
     * @param frequency 原始频率
     * @param signalStrength 信号强度 (0.0 - 1.0)
     * @return 处理后的频率值
     */
    private fun processFrequencyWithNoise(frequency: Float, signalStrength: Double): Float {
        // 检查信号强度
        val minSignalStrength = 0.05
        
        // 如果信号太弱，生成随机噪点
        if (signalStrength < minSignalStrength) {
            return generateNoiseFrequency()
        }
        
        // 如果频率超出有效范围，也视为噪音
        if (frequency < NOISE_FREQ_MIN || frequency > NOISE_FREQ_MAX) {
            return generateNoiseFrequency()
        }
        
        // 如果在同步频率范围内，映射为黑色（避免同步脉冲变成图像数据）
        if (isFrequencyInRange(frequency, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            return SstvConstants.BLACK_FREQ.toFloat()
        }
        
        // 有效频率，直接返回
        return frequency
    }
    
    /**
     * 生成噪音频率值
     * 
     * 映射为随机灰度或黑色
     */
    private fun generateNoiseFrequency(): Float {
        // 80% 概率返回黑色，20% 概率返回随机灰度
        return if (Random.nextFloat() < 0.8f) {
            SstvConstants.BLACK_FREQ.toFloat()
        } else {
            // 随机灰度 (1500-2300 Hz 之间)
            SstvConstants.BLACK_FREQ + Random.nextFloat() * SstvConstants.FREQ_RANGE * 0.3f
        }
    }
    
    /**
     * 累积音频数据（使用频率值填充缓冲区）
     */
    private fun accumulateAudioWithFrequency(frequency: Float, samples: ShortArray) {
        // 每个采样对应一个频率值
        for (i in samples.indices) {
            if (lineAudioIndex < lineAudioBuffer.size) {
                lineAudioBuffer[lineAudioIndex++] = frequency
            }
        }
    }
    
    /**
     * 强制完成当前行并 emit 扫描线
     * 
     * **核心**: 无论是否有同步信号，达到理论时间就必须换行
     */
    private suspend fun forceCompleteLine() {
        // 如果缓冲区没有填满，使用噪音填充剩余部分
        while (lineAudioIndex < lineAudioBuffer.size) {
            lineAudioBuffer[lineAudioIndex++] = generateNoiseFrequency()
        }
        
        // 使用策略处理行数据
        val pixels = currentStrategy.processLine(lineAudioBuffer, sampleRate)
        
        // 创建扫描线对象
        val scanLine = SstvScanLine(
            lineNumber = currentLineNumber,
            pixels = pixels
        )
        
        // ========== 立即 emit 扫描线数据 ==========
        // 强制要求：绝对不能等待，必须立即 emit
        _scanLineFlow.emit(scanLine)
        
        Log.d(TAG, "行 $currentLineNumber 完成 (采样: $lineSamplesProcessed/${currentStrategy.getSamplesPerLine(sampleRate)})")
        
        // 更新状态
        _currentLine.value = currentLineNumber
        currentLineNumber++
        
        // 重置行状态，准备下一行
        lineAudioIndex = 0
        lineAudioBuffer.fill(0f)
        lineSamplesProcessed = 0
        lineStartTimeMs = (totalSamplesProcessed * 1000.0 / sampleRate).toLong()
        
        // 检查是否完成整张图像
        if (currentLineNumber >= currentStrategy.height) {
            _state.value = DecoderState.COMPLETE
            Log.d(TAG, "========== 图像解码完成 ==========")
            Log.d(TAG, "总行数: $currentLineNumber")
            
            // 可选：自动重置开始下一张图像
            // reset()
            // startFreeRunning(...)
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查频率是否在指定范围内
     */
    private fun isFrequencyInRange(freq: Float, target: Int, tolerance: Int): Boolean {
        return freq >= (target - tolerance) && freq <= (target + tolerance)
    }
    
    /**
     * 检查是否是有效的图像数据频率 (1500-2300 Hz)
     */
    private fun isValidImageFrequency(freq: Float): Boolean {
        return freq >= (SstvConstants.BLACK_FREQ - FREQ_TOLERANCE) && 
               freq <= (SstvConstants.WHITE_FREQ + FREQ_TOLERANCE)
    }
    
    /**
     * 根据 VIS 码获取对应的策略
     */
    private fun getStrategyByVisCode(visCode: Int): SstvModeStrategy? {
        return when (visCode) {
            Martin1Strategy.VIS_CODE -> Martin1Strategy()
            Martin2Strategy.VIS_CODE -> Martin2Strategy()
            Robot36Strategy.VIS_CODE -> Robot36Strategy()
            Robot72Strategy.VIS_CODE -> Robot72Strategy()
            else -> null
        }
    }
    
    /**
     * 获取当前图像宽度
     */
    fun getImageWidth(): Int = currentStrategy.width
    
    /**
     * 获取当前图像高度
     */
    fun getImageHeight(): Int = currentStrategy.height
    
    /**
     * 获取解码进度 (0.0 - 1.0)
     */
    fun getProgress(): Float = currentLineNumber.toFloat() / currentStrategy.height
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
    }
}

/**
 * 策略工厂
 */
object SstvStrategyFactory {
    
    /** 所有支持的策略 */
    val allStrategies: List<SstvModeStrategy> by lazy {
        listOf(
            Martin1Strategy(),
            Martin2Strategy(),
            Robot36Strategy(),
            Robot72Strategy()
        )
    }
    
    /**
     * 根据 VIS 码获取策略
     */
    fun getByVisCode(visCode: Int): SstvModeStrategy? {
        return allStrategies.find { it.visCode == visCode }
    }
    
    /**
     * 根据模式名称获取策略
     */
    fun getByName(name: String): SstvModeStrategy? {
        return allStrategies.find { it.modeName.equals(name, ignoreCase = true) }
    }
    
    /**
     * 获取默认策略 (Martin 1)
     */
    fun getDefault(): SstvModeStrategy = Martin1Strategy()
}
