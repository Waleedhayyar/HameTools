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
 * 自由运行 SSTV 解码器 (Free-Running SSTV Decoder)
 * 
 * 基于过零检测算法的实时 SSTV 解码器，使用"自由运行"架构。
 * 
 * ## 核心原则：永不阻塞！
 * 
 * 这是与传统 SSTV 解码器的关键区别：
 * 
 * ### 传统解码器的问题
 * - 等待同步信号 → 没有信号时 UI 冻结
 * - 信号丢失 → 解码停止
 * - 用户体验差：屏幕要么正在解码，要么黑屏
 * 
 * ### 自由运行解码器的解决方案
 * - **永远在运行**: 内部时间计数器持续前进，不依赖外部同步
 * - **同步信号只用于校准**: 检测到同步脉冲时微调时钟，不阻塞等待
 * - **噪音也是数据**: 没有信号时输出随机灰度像素，模拟老式电视的雪花屏
 * - **固定帧率输出**: 像视频播放器一样，扫描线以固定速度移动
 * 
 * ## 架构设计
 * 
 * ```
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                     Free-Running 时钟                               │
 * │                                                                     │
 * │   音频输入 ──▶ 过零检测 ──▶ 频率估算 ──▶ ┌─────────────────────┐   │
 * │                                          │    行像素累积器     │   │
 * │                    ▲                     └─────────────────────┘   │
 * │                    │                              │                │
 * │                    │ 同步脉冲检测                  ▼                │
 * │                    │                     ┌─────────────────────┐   │
 * │                    │                     │   时间驱动换行      │   │
 * │                    ▼                     │  (不等待同步信号)   │   │
 * │              ┌──────────┐                └─────────────────────┘   │
 * │              │ 时钟校准  │                         │                │
 * │              │ (微调相位)│                         ▼                │
 * │              └──────────┘               SharedFlow<SstvScanLine>   │
 * └─────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * val decoder = FreeRunningSstvDecoder()
 * decoder.setMode(Robot36Strategy())
 * 
 * // 收集扫描线 - 扫描线会以固定速度到达！
 * decoder.scanLineFlow.collect { scanLine ->
 *     // 无论是真实图像还是雪花屏，都会收到数据
 *     updateUI(scanLine.lineNumber, scanLine.pixels)
 * }
 * 
 * // 在协程中启动解码
 * decoder.startDecoding(audioFlow)
 * ```
 * 
 * @author HAM Tools
 */
class FreeRunningSstvDecoder {
    
    companion object {
        private const val TAG = "FreeRunningDecoder"
        
        /** 默认采样率 */
        const val DEFAULT_SAMPLE_RATE = 44100
        
        /** 同步脉冲频率 (Hz) */
        private const val SYNC_FREQ = 1200f
        
        /** 同步频率容差 (Hz) */
        private const val SYNC_TOLERANCE = 80f
        
        /** 同步脉冲最小持续时间 (ms) */
        private const val SYNC_MIN_DURATION_MS = 3.0
        
        /** 同步脉冲最大持续时间 (ms) */
        private const val SYNC_MAX_DURATION_MS = 15.0
    }
    
    // ==================== 公开状态 ====================
    
    /**
     * 解码器运行状态
     */
    enum class RunState(val displayName: String) {
        /** 停止状态 */
        STOPPED("已停止"),
        
        /** 正在运行 (自由运行中) */
        RUNNING("运行中"),
        
        /** 已同步 (检测到有效同步信号) */
        SYNCED("已同步"),
        
        /** 解码完成一帧 */
        FRAME_COMPLETE("帧完成")
    }
    
    private val _runState = MutableStateFlow(RunState.STOPPED)
    /** 当前运行状态 */
    val runState: StateFlow<RunState> = _runState.asStateFlow()
    
    private val _currentLine = MutableStateFlow(0)
    /** 当前行号 (0-based) */
    val currentLine: StateFlow<Int> = _currentLine.asStateFlow()
    
    private val _modeName = MutableStateFlow("")
    /** 当前模式名称 */
    val modeName: StateFlow<String> = _modeName.asStateFlow()
    
    private val _signalStrength = MutableStateFlow(0f)
    /** 当前信号强度 (0.0 - 1.0) */
    val signalStrength: StateFlow<Float> = _signalStrength.asStateFlow()
    
    private val _isSynced = MutableStateFlow(false)
    /** 是否检测到同步信号 */
    val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()
    
    // ==================== 核心输出流 ====================
    
    /**
     * 扫描线输出流
     * 
     * **核心输出**: 每行解码完成后立即 emit。
     * 
     * 自由运行模式保证：
     * 1. 扫描线以固定的时间间隔到达 (基于模式的理论行时间)
     * 2. 即使没有有效信号，也会输出噪音行
     * 3. 收集器永远不会长时间等待
     */
    private val _scanLineFlow = MutableSharedFlow<SstvScanLine>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /** 公开的扫描线 SharedFlow */
    val scanLineFlow: SharedFlow<SstvScanLine> = _scanLineFlow.asSharedFlow()
    
    // ==================== 配置 ====================
    
    /** 当前解码策略 */
    private var currentStrategy: SstvModeStrategy = Robot36Strategy()
    
    /** 采样率 */
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE
    
    // ==================== 内部状态 ====================
    
    private var decoderScope: CoroutineScope? = null
    
    // 过零频率检测器
    private val zeroCrossingDetector = SlidingWindowZeroCrossingDetector(DEFAULT_SAMPLE_RATE, 2.0)
    
    // ========== 自由运行时钟 ==========
    
    /** 当前行号 */
    private var lineNumber = 0
    
    /** 总处理采样数 (作为时间基准) */
    private var totalSamplesProcessed = 0L
    
    /** 当前行开始的采样位置 */
    private var lineStartSample = 0L
    
    /** 每行的理论采样数 */
    private var samplesPerLine = 0
    
    // ========== 行像素累积 ==========
    
    /** 当前行的频率缓冲区 */
    private var lineFrequencyBuffer = FloatArray(0)
    
    /** 当前行的缓冲区写入位置 */
    private var lineBufferIndex = 0
    
    // ========== 同步检测 ==========
    
    /** 是否处于同步脉冲中 */
    private var inSyncPulse = false
    
    /** 同步脉冲开始的采样位置 */
    private var syncPulseStartSample = 0L
    
    /** 上次同步对齐的行号 */
    private var lastSyncAlignedLine = -1
    
    /** 连续同步检测计数 */
    private var consecutiveSyncCount = 0
    
    // ==================== 公开 API ====================
    
    /**
     * 设置解码模式
     * 
     * @param strategy 解码策略 (如 Robot36Strategy, Martin1Strategy 等)
     */
    fun setMode(strategy: SstvModeStrategy) {
        currentStrategy = strategy
        _modeName.value = strategy.modeName
        initializeBuffers()
        
        Log.d(TAG, "========== 设置模式 ==========")
        Log.d(TAG, "模式: ${strategy.modeName}")
        Log.d(TAG, "分辨率: ${strategy.width}x${strategy.height}")
        Log.d(TAG, "行时间: ${strategy.scanLineTimeMs}ms")
        Log.d(TAG, "每行采样: $samplesPerLine")
    }
    
    /**
     * 设置采样率
     * 
     * @param rate 采样率 (Hz)
     */
    fun setSampleRate(rate: Int) {
        sampleRate = rate
        initializeBuffers()
    }
    
    /**
     * 获取当前策略
     */
    fun getStrategy(): SstvModeStrategy = currentStrategy
    
    /**
     * 开始解码
     * 
     * **自由运行原则**: 一旦调用此方法，解码器会持续运行，
     * 无论是否检测到有效的 SSTV 信号。
     * 
     * @param audioFlow 音频数据流 (ShortArray PCM 16-bit)
     */
    suspend fun startDecoding(audioFlow: Flow<ShortArray>) {
        Log.d(TAG, "========== 启动自由运行解码 ==========")
        Log.d(TAG, "模式: ${currentStrategy.modeName}")
        Log.d(TAG, "采样率: $sampleRate Hz")
        
        // 初始化
        reset()
        _runState.value = RunState.RUNNING
        
        // 创建作用域
        decoderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // 收集音频流
        audioFlow.collect { samples ->
            if (decoderScope?.isActive == true) {
                processAudioChunk(samples)
            }
        }
    }
    
    /**
     * 停止解码
     */
    fun stop() {
        _runState.value = RunState.STOPPED
        decoderScope?.cancel()
        decoderScope = null
        Log.d(TAG, "========== 解码器停止 ==========")
    }
    
    /**
     * 重置解码器
     */
    fun reset() {
        _runState.value = RunState.STOPPED
        _currentLine.value = 0
        _signalStrength.value = 0f
        _isSynced.value = false
        
        lineNumber = 0
        totalSamplesProcessed = 0L
        lineStartSample = 0L
        lineBufferIndex = 0
        
        inSyncPulse = false
        syncPulseStartSample = 0L
        lastSyncAlignedLine = -1
        consecutiveSyncCount = 0
        
        zeroCrossingDetector.reset()
        initializeBuffers()
        
        Log.d(TAG, "解码器已重置")
    }
    
    // ==================== 核心处理逻辑 ====================
    
    /**
     * 初始化缓冲区
     */
    private fun initializeBuffers() {
        samplesPerLine = currentStrategy.getSamplesPerLine(sampleRate)
        lineFrequencyBuffer = FloatArray(samplesPerLine)
        lineBufferIndex = 0
        
        Log.d(TAG, "缓冲区初始化: 每行 $samplesPerLine 采样")
    }
    
    /**
     * 处理音频块 - 自由运行核心逻辑
     * 
     * 关键点：
     * 1. 逐采样处理，使用过零检测获取频率
     * 2. 同步信号只用于校准，不阻塞
     * 3. 根据采样计数强制换行
     */
    private suspend fun processAudioChunk(samples: ShortArray) {
        for (sample in samples) {
            // 1. 过零检测获取频率
            val frequency = zeroCrossingDetector.process(sample)
            
            // 2. 更新信号强度 (简化版，基于频率有效性)
            updateSignalStrength(frequency)
            
            // 3. 同步脉冲检测
            detectSyncPulse(frequency)
            
            // 4. 累积频率到行缓冲区
            accumulateFrequency(frequency)
            
            // 5. 更新采样计数
            totalSamplesProcessed++
            
            // 6. 检查是否需要换行
            val samplesInLine = totalSamplesProcessed - lineStartSample
            if (samplesInLine >= samplesPerLine) {
                // **自由运行关键**: 达到理论行时间，强制换行！
                completeLine()
            }
        }
    }
    
    /**
     * 更新信号强度估算
     */
    private fun updateSignalStrength(frequency: Float) {
        // 如果频率在有效 SSTV 范围内，认为信号有效
        val isValidFreq = frequency >= SstvConstants.BLACK_FREQ - 100 &&
                          frequency <= SstvConstants.WHITE_FREQ + 100
        
        val newStrength = if (isValidFreq) {
            // 指数移动平均
            _signalStrength.value * 0.95f + 0.05f
        } else {
            _signalStrength.value * 0.98f
        }
        
        _signalStrength.value = newStrength.coerceIn(0f, 1f)
    }
    
    /**
     * 检测同步脉冲
     * 
     * 同步脉冲 (1200Hz) 用于校准行起始位置。
     * 但检测不到同步脉冲时，解码器仍然继续运行。
     */
    private fun detectSyncPulse(frequency: Float) {
        val isSyncFreq = frequency >= SYNC_FREQ - SYNC_TOLERANCE &&
                         frequency <= SYNC_FREQ + SYNC_TOLERANCE
        
        if (isSyncFreq) {
            if (!inSyncPulse) {
                // 开始同步脉冲
                syncPulseStartSample = totalSamplesProcessed
                inSyncPulse = true
            }
        } else {
            if (inSyncPulse) {
                // 同步脉冲结束，检查持续时间
                val pulseDuration = (totalSamplesProcessed - syncPulseStartSample) * 1000.0 / sampleRate
                
                if (pulseDuration >= SYNC_MIN_DURATION_MS && pulseDuration <= SYNC_MAX_DURATION_MS) {
                    // 有效同步脉冲
                    onValidSyncPulse(pulseDuration)
                }
                
                inSyncPulse = false
            }
        }
    }
    
    /**
     * 检测到有效同步脉冲
     * 
     * 执行时钟校准，但不阻塞解码流程
     */
    private fun onValidSyncPulse(pulseDuration: Double) {
        // 避免重复对齐同一行
        if (lineNumber == lastSyncAlignedLine) {
            return
        }
        
        // 计算当前行的进度
        val samplesInLine = totalSamplesProcessed - lineStartSample
        val progress = samplesInLine.toFloat() / samplesPerLine
        
        // 只在行开始阶段进行对齐 (前 15%)
        if (progress < 0.15f) {
            // 校准：重置行起始位置
            lineStartSample = totalSamplesProcessed
            lineBufferIndex = 0
            lineFrequencyBuffer.fill(0f)
            lastSyncAlignedLine = lineNumber
            consecutiveSyncCount++
            
            // 更新同步状态
            if (consecutiveSyncCount >= 2) {
                _isSynced.value = true
                _runState.value = RunState.SYNCED
            }
            
            Log.d(TAG, "行 $lineNumber 同步对齐 (脉冲: ${String.format("%.1f", pulseDuration)}ms)")
        }
    }
    
    /**
     * 累积频率到行缓冲区
     */
    private fun accumulateFrequency(frequency: Float) {
        if (lineBufferIndex < lineFrequencyBuffer.size) {
            lineFrequencyBuffer[lineBufferIndex++] = frequency
        }
    }
    
    /**
     * 完成当前行并发射扫描线
     * 
     * **自由运行关键**: 这个方法由时间驱动调用，不依赖同步信号！
     */
    private suspend fun completeLine() {
        // 填充未写入的部分（如果有）
        while (lineBufferIndex < lineFrequencyBuffer.size) {
            lineFrequencyBuffer[lineBufferIndex++] = generateNoiseFrequency()
        }
        
        // 使用策略处理行数据，生成像素
        val pixels = currentStrategy.processLine(lineFrequencyBuffer, sampleRate)
        
        // 创建扫描线对象，包含信号质量信息
        val scanLine = SstvScanLine(
            lineNumber = lineNumber,
            pixels = pixels,
            signalQuality = _signalStrength.value,
            isSynced = _isSynced.value
        )
        
        // **立即发射！不等待！**
        _scanLineFlow.emit(scanLine)
        
        // 更新状态
        _currentLine.value = lineNumber
        
        // 日志 (每 10 行输出一次)
        if (lineNumber % 10 == 0) {
            Log.d(TAG, "行 $lineNumber 完成 (信号: ${String.format("%.1f", _signalStrength.value * 100)}%, 同步: ${_isSynced.value})")
        }
        
        // 准备下一行
        lineNumber++
        lineStartSample = totalSamplesProcessed
        lineBufferIndex = 0
        lineFrequencyBuffer.fill(0f)
        
        // 检查帧完成
        if (lineNumber >= currentStrategy.height) {
            onFrameComplete()
        }
    }
    
    /**
     * 帧解码完成
     */
    private fun onFrameComplete() {
        Log.d(TAG, "========== 帧解码完成 ==========")
        Log.d(TAG, "总行数: $lineNumber")
        
        _runState.value = RunState.FRAME_COMPLETE
        
        // 重置行号开始下一帧
        lineNumber = 0
        consecutiveSyncCount = 0
        _isSynced.value = false
        
        // 继续运行（自由运行模式不停止）
        _runState.value = RunState.RUNNING
    }
    
    /**
     * 生成噪音频率
     */
    private fun generateNoiseFrequency(): Float {
        return if (Random.nextFloat() < 0.8f) {
            SstvConstants.BLACK_FREQ.toFloat()
        } else {
            SstvConstants.BLACK_FREQ + 
                Random.nextFloat() * SstvConstants.FREQ_RANGE * 0.3f
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取图像宽度
     */
    fun getImageWidth(): Int = currentStrategy.width
    
    /**
     * 获取图像高度
     */
    fun getImageHeight(): Int = currentStrategy.height
    
    /**
     * 获取解码进度 (0.0 - 1.0)
     */
    fun getProgress(): Float = lineNumber.toFloat() / currentStrategy.height
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
    }
}

/**
 * 解码器状态信息
 * 
 * 用于 UI 显示解码器的详细状态
 */
data class DecoderStatusInfo(
    val runState: FreeRunningSstvDecoder.RunState,
    val modeName: String,
    val currentLine: Int,
    val totalLines: Int,
    val progress: Float,
    val signalStrength: Float,
    val isSynced: Boolean
)

/**
 * 工厂方法：创建预配置的解码器
 */
object FreeRunningDecoderFactory {
    
    /**
     * 创建 Robot 36 解码器
     */
    fun createRobot36(): FreeRunningSstvDecoder {
        return FreeRunningSstvDecoder().apply {
            setMode(Robot36Strategy())
        }
    }
    
    /**
     * 创建 Robot 72 解码器
     */
    fun createRobot72(): FreeRunningSstvDecoder {
        return FreeRunningSstvDecoder().apply {
            setMode(Robot72Strategy())
        }
    }
    
    /**
     * 创建 Martin 1 解码器
     */
    fun createMartin1(): FreeRunningSstvDecoder {
        return FreeRunningSstvDecoder().apply {
            setMode(Martin1Strategy())
        }
    }
    
    /**
     * 创建 Martin 2 解码器
     */
    fun createMartin2(): FreeRunningSstvDecoder {
        return FreeRunningSstvDecoder().apply {
            setMode(Martin2Strategy())
        }
    }
    
    /**
     * 获取所有可用的策略
     */
    fun getAllStrategies(): List<SstvModeStrategy> {
        return listOf(
            Robot36Strategy(),
            Robot72Strategy(),
            Martin1Strategy(),
            Martin2Strategy()
        )
    }
}
