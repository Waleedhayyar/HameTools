package com.ham.tools.ui.screens.tools.sstv

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * SSTV 多模式解码器
 * 
 * 通用解码器，支持多种 SSTV 模式（Martin、Scottie 等）。
 * 使用状态机分析传入的频率数据，并实时生成图像。
 * 
 * 核心特性:
 * - 通用解码循环 (decodeImageLoop)：根据 SstvMode 参数动态解码
 * - 实时行回调：每解码完成一行，立即触发回调，实现扫描线效果
 * - 根据 colorSequence 通用处理颜色通道顺序
 * 
 * 使用方式:
 * 1. 创建实例并设置回调
 * 2. 调用 start() 开始解码
 * 3. （可选）通过 setMode() 设置解码模式
 * 4. 通过 feedFrequency() 持续输入频率数据
 * 5. 监听 onLineDecoded 回调获取实时行数据（解一行，发一行）
 * 6. 调用 stop() 停止解码
 */
class SstvDecoder(
    private val callback: DecoderCallback
) {
    companion object {
        private const val TAG = "SstvDecoder"
        
        // 校准检测参数
        private const val CALIBRATION_MIN_DURATION_MS = 100  // 校准信号最少持续时间
        private const val SYNC_TOLERANCE_MS = 2.0            // 同步脉冲时长容差
        
        // 频率容差
        private const val FREQ_TOLERANCE = 80
    }
    
    /**
     * 解码器状态
     */
    enum class State(val displayName: String) {
        IDLE("空闲 - 等待校准信号"),
        CALIBRATING("校准中 - 检测 1200Hz 信号"),
        WAIT_SYNC("等待行同步"),
        DECODING_LINE("解码行数据"),
        COMPLETE("解码完成")
    }
    
    /**
     * 颜色读取阶段（通用，根据 colorSequence 索引）
     */
    private data class ColorPhase(
        val index: Int,           // 在 colorSequence 中的索引
        val channel: ColorChannel,
        val durationMs: Double
    )
    
    /**
     * 解码器回调接口
     */
    interface DecoderCallback {
        /** 状态变化 */
        fun onStateChanged(state: State)
        
        /**
         * 【核心回调】单行解码完成 - 实时回调像素数据
         * 
         * 重要: 每解码完成一行立即调用，不等待整张图完成。
         * 这使得 UI 可以实现扫描线效果。
         * 
         * @param lineNumber 当前行号 (0-based)
         * @param pixels 该行的像素数组 (ARGB 格式, 长度 = mode.width)
         * @param totalLines 总行数
         */
        fun onLineDecoded(lineNumber: Int, pixels: IntArray, totalLines: Int)
        
        /** 图像更新 (可用于实时预览 Bitmap) */
        fun onImageUpdated(bitmap: Bitmap)
        
        /** 解码完成 */
        fun onDecodeComplete(bitmap: Bitmap)
        
        /** 解码错误 */
        fun onError(message: String)
        
        /** 调试信息 */
        fun onDebugInfo(info: String)
        
        /** 模式变化 */
        fun onModeChanged(mode: SstvMode)
        
        /** 解码进度 (百分比) */
        fun onProgress(lineNumber: Int, totalLines: Int)
    }
    
    // 当前使用的模式
    private var currentMode: SstvMode = SstvModeRepository.getDefaultMode()
    
    // 解码器状态
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    
    // 当前解码行
    private val _currentLine = MutableStateFlow(0)
    val currentLine: StateFlow<Int> = _currentLine.asStateFlow()
    
    // 当前模式名称
    private val _modeName = MutableStateFlow(currentMode.modeName)
    val modeName: StateFlow<String> = _modeName.asStateFlow()
    
    // 协程
    private var decoderScope: CoroutineScope? = null
    private var decoderJob: Job? = null
    
    // 频率数据队列 (线程安全)
    private val frequencyQueue = ConcurrentLinkedQueue<FrequencySample>()
    
    // 图像缓冲区
    private var imageBitmap: Bitmap? = null
    private var imagePixels: IntArray = IntArray(0)
    
    // 当前行的 RGB 缓冲区（根据 colorSequence 通用处理）
    private var lineBuffers: MutableMap<ColorChannel, IntArray> = mutableMapOf()
    
    // 当前行的像素缓冲区（合成后的 ARGB）
    private var currentLinePixels: IntArray = IntArray(0)
    
    // 时序追踪
    private var stateStartTime = 0L           // 当前状态开始时间 (ms)
    private var syncDetectionStart = 0L       // 同步信号检测开始时间
    private var calibrationStartTime = 0L     // 校准开始时间
    private var pixelIndex = 0                // 当前像素索引
    private var currentLineNumber = 0         // 当前行号
    
    // 颜色通道解码追踪
    private var currentColorPhaseIndex = 0    // 当前颜色阶段索引
    private var colorPhaseStartTime = 0L      // 当前颜色阶段开始时间
    
    // 频率累积 (用于像素平均)
    private var freqAccumulator = 0f
    private var freqSampleCount = 0
    
    // 模式特殊标记
    private var isFirstLine = true
    
    /**
     * 频率采样数据
     */
    data class FrequencySample(
        val frequency: Float,
        val timestamp: Long  // 系统时间戳 (ms)
    )
    
    /**
     * 设置解码模式
     * 
     * @param mode 要使用的 SSTV 模式
     */
    fun setMode(mode: SstvMode) {
        currentMode = mode
        _modeName.value = mode.modeName
        
        // 重新初始化缓冲区
        initializeBuffers()
        
        Log.d(TAG, "切换模式: ${mode.modeName} (VIS: ${mode.visCode})")
        callback.onModeChanged(mode)
        callback.onDebugInfo("模式: ${mode.modeName}")
    }
    
    /**
     * 获取当前模式
     */
    fun getMode(): SstvMode = currentMode
    
    /**
     * 初始化图像和行缓冲区
     */
    private fun initializeBuffers() {
        val width = currentMode.width
        val height = currentMode.height
        
        // 初始化整张图像的像素缓冲
        imagePixels = IntArray(width * height)
        imagePixels.fill(Color.BLACK)
        
        // 根据 colorSequence 初始化各颜色通道的行缓冲区
        lineBuffers.clear()
        for (segment in currentMode.colorSequence) {
            lineBuffers[segment.channel] = IntArray(width)
        }
        
        // 当前行像素缓冲（用于回调）
        currentLinePixels = IntArray(width)
        
        // 初始化 Bitmap
        imageBitmap?.recycle()
        imageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
    
    /**
     * 开始解码
     */
    fun start() {
        if (decoderJob?.isActive == true) {
            Log.w(TAG, "解码器已在运行")
            return
        }
        
        reset()
        
        decoderScope = CoroutineScope(Dispatchers.Default)
        decoderJob = decoderScope?.launch {
            Log.d(TAG, "========== SSTV 解码器启动 ==========")
            Log.d(TAG, "模式: ${currentMode.modeName}")
            Log.d(TAG, "分辨率: ${currentMode.width}x${currentMode.height}")
            Log.d(TAG, "行时长: ${currentMode.scanLineTimeMs}ms")
            Log.d(TAG, "颜色顺序: ${currentMode.colorSequence.map { it.channel.name }}")
            
            callback.onStateChanged(State.IDLE)
            
            // 主解码循环
            while (isActive) {
                val sample = frequencyQueue.poll()
                if (sample != null) {
                    processFrequency(sample)
                } else {
                    kotlinx.coroutines.delay(1)
                }
            }
        }
    }
    
    /**
     * 停止解码
     */
    fun stop() {
        decoderJob?.cancel()
        decoderJob = null
        decoderScope?.cancel()
        decoderScope = null
        
        frequencyQueue.clear()
        Log.d(TAG, "========== SSTV 解码器停止 ==========")
    }
    
    /**
     * 重置解码器状态
     */
    fun reset() {
        _state.value = State.IDLE
        _currentLine.value = 0
        
        frequencyQueue.clear()
        
        initializeBuffers()
        
        // 清空所有行缓冲
        lineBuffers.values.forEach { it.fill(0) }
        currentLinePixels.fill(Color.BLACK)
        
        stateStartTime = 0L
        syncDetectionStart = 0L
        calibrationStartTime = 0L
        pixelIndex = 0
        currentLineNumber = 0
        currentColorPhaseIndex = 0
        colorPhaseStartTime = 0L
        freqAccumulator = 0f
        freqSampleCount = 0
        isFirstLine = true
        
        Log.d(TAG, "解码器已重置 (模式: ${currentMode.modeName})")
    }
    
    /**
     * 输入频率数据
     * @param frequency 检测到的频率 (Hz)
     * @param timestamp 时间戳 (ms)，如果为 null 则使用系统时间
     */
    fun feedFrequency(frequency: Float, timestamp: Long? = null) {
        val ts = timestamp ?: System.currentTimeMillis()
        frequencyQueue.offer(FrequencySample(frequency, ts))
    }
    
    // ==================== 核心解码状态机 ====================
    
    /**
     * 处理单个频率采样 - 状态机入口
     */
    private fun processFrequency(sample: FrequencySample) {
        val freq = sample.frequency
        val time = sample.timestamp
        
        when (_state.value) {
            State.IDLE -> processIdle(freq, time)
            State.CALIBRATING -> processCalibrating(freq, time)
            State.WAIT_SYNC -> processWaitSync(freq, time)
            State.DECODING_LINE -> processDecodeLine(freq, time)
            State.COMPLETE -> { /* 解码完成，忽略后续数据 */ }
        }
    }
    
    /**
     * IDLE 状态 - 等待校准信号
     */
    private fun processIdle(freq: Float, time: Long) {
        if (isFrequencyInRange(freq, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            calibrationStartTime = time
            changeState(State.CALIBRATING, time)
            Log.d(TAG, "检测到可能的校准信号: ${freq.toInt()} Hz")
        }
    }
    
    /**
     * CALIBRATING 状态 - 校准信号检测
     */
    private fun processCalibrating(freq: Float, time: Long) {
        if (isFrequencyInRange(freq, SstvConstants.SYNC_FREQ, FREQ_TOLERANCE)) {
            val duration = time - calibrationStartTime
            
            if (duration >= CALIBRATION_MIN_DURATION_MS) {
                Log.d(TAG, "校准完成，持续时间: ${duration}ms")
                callback.onDebugInfo("校准信号检测完成 (${duration}ms)")
                
                // 开始图像解码循环
                startImageDecodeLoop(time)
            }
        } else {
            val duration = time - calibrationStartTime
            
            if (duration >= CALIBRATION_MIN_DURATION_MS) {
                Log.d(TAG, "校准信号结束，持续: ${duration}ms")
                startImageDecodeLoop(time)
            } else {
                Log.d(TAG, "校准信号太短 (${duration}ms)，回到 IDLE")
                changeState(State.IDLE, time)
            }
        }
    }
    
    /**
     * 开始图像解码循环
     * 
     * 这是核心的解码入口，初始化后进入 WAIT_SYNC 状态等待第一行
     */
    private fun startImageDecodeLoop(time: Long) {
        currentLineNumber = 0
        isFirstLine = true
        changeState(State.WAIT_SYNC, time)
        
        callback.onDebugInfo("开始解码 ${currentMode.modeName} (${currentMode.height} 行)")
        Log.d(TAG, "========== 开始图像解码循环 ==========")
        Log.d(TAG, "目标: ${currentMode.height} 行, ${currentMode.width} 像素/行")
    }
    
    /**
     * WAIT_SYNC 状态 - 等待行同步脉冲
     */
    private fun processWaitSync(freq: Float, time: Long) {
        val expectedSyncMs = currentMode.syncPulseMs
        
        if (isFrequencyInRange(freq, currentMode.syncPulseFreq, FREQ_TOLERANCE)) {
            if (syncDetectionStart == 0L) {
                syncDetectionStart = time
            }
        } else {
            if (syncDetectionStart > 0) {
                val syncDuration = time - syncDetectionStart
                val minDuration = expectedSyncMs - SYNC_TOLERANCE_MS
                val maxDuration = expectedSyncMs + SYNC_TOLERANCE_MS + 5
                
                if (syncDuration >= minDuration && syncDuration <= maxDuration) {
                    // 有效同步脉冲，开始解码该行
                    callback.onDebugInfo("行 $currentLineNumber 同步 (${String.format("%.1f", syncDuration)}ms)")
                    startLineDecoding(time, freq)
                } else if (syncDuration > maxDuration) {
                    Log.d(TAG, "同步脉冲过长 (${syncDuration}ms)")
                }
                
                syncDetectionStart = 0L
            }
            
            // Scottie 模式第一行特殊处理
            if (currentMode.lineStructureType == LineStructureType.SCOTTIE && isFirstLine) {
                if (isFrequencyInRange(freq, SstvConstants.BLACK_FREQ, FREQ_TOLERANCE + 100) ||
                    (freq >= SstvConstants.BLACK_FREQ && freq <= SstvConstants.WHITE_FREQ)) {
                    startLineDecoding(time, freq)
                }
            }
        }
    }
    
    /**
     * 开始单行解码
     * 
     * 根据 currentMode.colorSequence 初始化颜色阶段
     */
    private fun startLineDecoding(time: Long, initialFreq: Float) {
        // 重置行缓冲
        lineBuffers.values.forEach { it.fill(0) }
        
        // 初始化颜色阶段
        currentColorPhaseIndex = 0
        colorPhaseStartTime = time
        pixelIndex = 0
        freqAccumulator = 0f
        freqSampleCount = 0
        
        isFirstLine = false
        
        changeState(State.DECODING_LINE, time)
        
        // 处理第一个频率样本
        processDecodeLine(initialFreq, time)
    }
    
    /**
     * DECODING_LINE 状态 - 解码行数据
     * 
     * 核心通用行解码逻辑：
     * 按照 colorSequence 的顺序依次读取各颜色通道
     */
    private fun processDecodeLine(freq: Float, time: Long) {
        val colorSequence = currentMode.colorSequence
        
        if (currentColorPhaseIndex >= colorSequence.size) {
            // 所有颜色通道已读取完成，完成该行
            onLineComplete()
            return
        }
        
        val currentPhase = colorSequence[currentColorPhaseIndex]
        val channel = currentPhase.channel
        val scanDurationMs = currentPhase.durationMs
        
        // 计算当前颜色阶段的经过时间
        val elapsedMs = time - colorPhaseStartTime
        
        // 计算当前应该在哪个像素位置
        val targetPixel = ((elapsedMs / scanDurationMs) * currentMode.width).toInt()
            .coerceIn(0, currentMode.width - 1)
        
        // 检查是否完成当前颜色通道
        if (elapsedMs >= scanDurationMs || pixelIndex >= currentMode.width) {
            // 保存最后一个像素
            finalizeCurrentPixel(channel)
            
            // 进入下一个颜色阶段
            transitionToNextColorPhase(time)
            return
        }
        
        // 累积有效频率（在黑-白范围内）
        if (freq >= SstvConstants.BLACK_FREQ - FREQ_TOLERANCE && 
            freq <= SstvConstants.WHITE_FREQ + FREQ_TOLERANCE) {
            freqAccumulator += freq
            freqSampleCount++
        }
        
        // 像素位置变化时，保存前一个像素
        if (targetPixel > pixelIndex && pixelIndex < currentMode.width) {
            finalizeCurrentPixel(channel)
            pixelIndex = targetPixel
        }
    }
    
    /**
     * 转换到下一个颜色阶段
     */
    private fun transitionToNextColorPhase(time: Long) {
        currentColorPhaseIndex++
        colorPhaseStartTime = time
        pixelIndex = 0
        freqAccumulator = 0f
        freqSampleCount = 0
        
        // 检查是否所有颜色通道都已完成
        if (currentColorPhaseIndex >= currentMode.colorSequence.size) {
            onLineComplete()
        } else {
            val nextPhase = currentMode.colorSequence[currentColorPhaseIndex]
            Log.d(TAG, "行 $currentLineNumber: 进入 ${nextPhase.channel} 通道")
        }
    }
    
    /**
     * 完成当前像素的计算并保存到对应颜色通道缓冲区
     */
    private fun finalizeCurrentPixel(channel: ColorChannel) {
        if (freqSampleCount > 0 && pixelIndex < currentMode.width) {
            val avgFreq = freqAccumulator / freqSampleCount
            val intensity = SstvConstants.frequencyToLuminance(avgFreq)
            val value = (intensity * 255).toInt().coerceIn(0, 255)
            
            lineBuffers[channel]?.let { buffer ->
                if (pixelIndex < buffer.size) {
                    buffer[pixelIndex] = value
                }
            }
        }
        
        freqAccumulator = 0f
        freqSampleCount = 0
    }
    
    // ==================== 行完成处理 ====================
    
    /**
     * 【核心】单行解码完成
     * 
     * 合并 RGB 数据并立即触发回调。
     * 实现"解一行，发一行"的实时回调机制。
     */
    private fun onLineComplete() {
        val width = currentMode.width
        
        // 从颜色通道缓冲区获取 RGB 数据
        val redBuffer = lineBuffers[ColorChannel.RED] ?: IntArray(width)
        val greenBuffer = lineBuffers[ColorChannel.GREEN] ?: IntArray(width)
        val blueBuffer = lineBuffers[ColorChannel.BLUE] ?: IntArray(width)
        
        // 合并 RGB 到像素数组 (ARGB 格式)
        for (x in 0 until width) {
            val r = redBuffer.getOrElse(x) { 0 }
            val g = greenBuffer.getOrElse(x) { 0 }
            val b = blueBuffer.getOrElse(x) { 0 }
            currentLinePixels[x] = Color.rgb(r, g, b)
        }
        
        // 更新整张图像的像素缓冲
        val lineOffset = currentLineNumber * width
        System.arraycopy(currentLinePixels, 0, imagePixels, lineOffset, width)
        
        // 更新 Bitmap (单行更新，高效)
        imageBitmap?.setPixels(
            currentLinePixels,
            0,
            width,
            0,
            currentLineNumber,
            width,
            1
        )
        
        Log.d(TAG, "行 $currentLineNumber 解码完成")
        
        // ========== 实时回调：解一行，发一行 ==========
        
        // 1. 回调像素数据（用于自定义渲染）
        callback.onLineDecoded(
            lineNumber = currentLineNumber,
            pixels = currentLinePixels.copyOf(),  // 传递副本避免并发问题
            totalLines = currentMode.height
        )
        
        // 2. 回调进度
        callback.onProgress(currentLineNumber, currentMode.height)
        
        // 3. 回调 Bitmap 更新（用于 UI 预览）
        imageBitmap?.let { callback.onImageUpdated(it) }
        
        // ========== 准备下一行 ==========
        
        currentLineNumber++
        _currentLine.value = currentLineNumber
        
        // 清空行缓冲
        lineBuffers.values.forEach { it.fill(0) }
        
        // 检查是否完成整张图像
        if (currentLineNumber >= currentMode.height) {
            onImageDecodeComplete()
        } else {
            // 重置状态，等待下一行同步脉冲
            syncDetectionStart = 0L
            changeState(State.WAIT_SYNC, System.currentTimeMillis())
        }
    }
    
    /**
     * 整张图像解码完成
     */
    private fun onImageDecodeComplete() {
        changeState(State.COMPLETE, System.currentTimeMillis())
        
        Log.d(TAG, "========== 图像解码完成 ==========")
        Log.d(TAG, "模式: ${currentMode.modeName}")
        Log.d(TAG, "分辨率: ${currentMode.width}x${currentMode.height}")
        Log.d(TAG, "总行数: $currentLineNumber")
        
        callback.onDebugInfo("解码完成！")
        
        imageBitmap?.let {
            callback.onDecodeComplete(it)
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 切换状态
     */
    private fun changeState(newState: State, time: Long) {
        val oldState = _state.value
        _state.value = newState
        stateStartTime = time
        
        Log.d(TAG, "状态变化: ${oldState.name} -> ${newState.name}")
        callback.onStateChanged(newState)
    }
    
    /**
     * 检查频率是否在指定范围内
     */
    private fun isFrequencyInRange(freq: Float, target: Int, tolerance: Int): Boolean {
        return freq >= (target - tolerance) && freq <= (target + tolerance)
    }
    
    /**
     * 获取当前解码的 Bitmap (用于预览)
     */
    fun getCurrentBitmap(): Bitmap? {
        return imageBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }
    
    /**
     * 获取图像宽度
     */
    fun getImageWidth(): Int = currentMode.width
    
    /**
     * 获取图像高度
     */
    fun getImageHeight(): Int = currentMode.height
    
    /**
     * 获取当前行的像素数据副本
     */
    fun getCurrentLinePixels(): IntArray = currentLinePixels.copyOf()
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        imageBitmap?.recycle()
        imageBitmap = null
        lineBuffers.clear()
    }
}

/**
 * 空实现的回调 (方便只覆盖需要的方法)
 */
open class SimpleDecoderCallback : SstvDecoder.DecoderCallback {
    override fun onStateChanged(state: SstvDecoder.State) {}
    override fun onLineDecoded(lineNumber: Int, pixels: IntArray, totalLines: Int) {}
    override fun onImageUpdated(bitmap: Bitmap) {}
    override fun onDecodeComplete(bitmap: Bitmap) {}
    override fun onError(message: String) {}
    override fun onDebugInfo(info: String) {}
    override fun onModeChanged(mode: SstvMode) {}
    override fun onProgress(lineNumber: Int, totalLines: Int) {}
}

/**
 * 扫描线渲染辅助类
 * 
 * 用于收集解码的行数据并构建最终图像。
 * 支持实时扫描线效果渲染。
 */
class ScanLineRenderer(
    private val width: Int,
    private val height: Int
) {
    private val pixels = IntArray(width * height)
    private var lastDecodedLine = -1
    
    init {
        pixels.fill(Color.BLACK)
    }
    
    /**
     * 更新指定行的像素数据
     */
    fun updateLine(lineNumber: Int, linePixels: IntArray) {
        if (lineNumber < 0 || lineNumber >= height) return
        if (linePixels.size != width) return
        
        val offset = lineNumber * width
        System.arraycopy(linePixels, 0, pixels, offset, width)
        lastDecodedLine = lineNumber
    }
    
    /**
     * 获取所有像素数据
     */
    fun getPixels(): IntArray = pixels.copyOf()
    
    /**
     * 获取最后解码的行号
     */
    fun getLastDecodedLine(): Int = lastDecodedLine
    
    /**
     * 获取解码进度 (0.0 - 1.0)
     */
    fun getProgress(): Float = (lastDecodedLine + 1).toFloat() / height
    
    /**
     * 创建当前状态的 Bitmap
     */
    fun createBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    /**
     * 重置渲染器
     */
    fun reset() {
        pixels.fill(Color.BLACK)
        lastDecodedLine = -1
    }
}
