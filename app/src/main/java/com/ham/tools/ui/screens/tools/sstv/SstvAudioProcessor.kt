package com.ham.tools.ui.screens.tools.sstv

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

/**
 * SSTV Audio Processor
 * 
 * 核心音频处理模块，负责：
 * 1. 从麦克风捕获音频 (AudioRecord, 44100Hz, Mono, PCM 16bit)
 * 2. 使用 Goertzel 算法进行频率检测（更好的抗噪性能）
 * 3. 使用 VIS 码检测器自动识别 SSTV 模式
 * 4. 滑动平均滤波器平滑频率读数
 * 5. 输出检测到的频率到 Logcat
 * 
 * SSTV 典型频率:
 * - 1200 Hz: 同步脉冲
 * - 1500 Hz: 黑色
 * - 2300 Hz: 白色
 * - 1900 Hz: VIS 起始音
 * - 1100 Hz: VIS 逻辑 1
 * - 1300 Hz: VIS 逻辑 0
 */
class SstvAudioProcessor {
    
    companion object {
        private const val TAG = "SstvAudioProcessor"
        
        // 音频配置参数
        const val SAMPLE_RATE = 44100       // 采样率 44.1 kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO  // 单声道
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16位 PCM
        
        // 缓冲区大小 - 约 50ms 的音频数据
        private const val BUFFER_SIZE_MS = 50
        
        // 滑动平均滤波器窗口大小
        private const val MOVING_AVERAGE_WINDOW = 8
        
        // 频率检测有效范围 (Hz) - SSTV 信号范围
        private const val MIN_VALID_FREQUENCY = 1000f
        private const val MAX_VALID_FREQUENCY = 2500f
        
        // 最小有效过零次数阈值 (避免静音时的误检测)
        private const val MIN_ZERO_CROSSINGS = 10
        
        // Goertzel 检测阈值
        private const val GOERTZEL_THRESHOLD = 0.08
    }
    
    // 音频录制器
    private var audioRecord: AudioRecord? = null
    
    // 协程作用域
    private var processingScope: CoroutineScope? = null
    private var recordingJob: Job? = null
    
    // 滑动平均滤波器缓冲区
    private val frequencyBuffer = ArrayDeque<Float>(MOVING_AVERAGE_WINDOW)
    
    // 状态流
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _currentFrequency = MutableStateFlow(0f)
    val currentFrequency: StateFlow<Float> = _currentFrequency.asStateFlow()
    
    private val _smoothedFrequency = MutableStateFlow(0f)
    val smoothedFrequency: StateFlow<Float> = _smoothedFrequency.asStateFlow()
    
    // 检测到的 VIS 码
    private val _detectedVisCode = MutableStateFlow<Int?>(null)
    val detectedVisCode: StateFlow<Int?> = _detectedVisCode.asStateFlow()
    
    // 检测到的模式
    private val _detectedMode = MutableStateFlow<SstvMode?>(null)
    val detectedMode: StateFlow<SstvMode?> = _detectedMode.asStateFlow()
    
    // 频率监听器 (供解码器使用)
    private var frequencyListener: FrequencyListener? = null
    
    // VIS 码监听器
    private var visCodeListener: VisCodeListener? = null
    
    // 原始音频监听器 (用于流式解码)
    private var rawAudioListener: RawAudioListener? = null
    
    // Goertzel 频率分析器
    private val frequencyAnalyzer = FrequencyAnalyzer(SAMPLE_RATE, 20.0)
    
    // VIS 码检测器
    private val visCodeDetector = VisCodeDetector(SAMPLE_RATE)
    
    // 是否启用 VIS 检测
    private var visDetectionEnabled = true
    
    /**
     * 频率监听器接口
     */
    fun interface FrequencyListener {
        /**
         * 当检测到有效频率时调用
         * @param frequency 平滑后的频率 (Hz)
         * @param timestamp 时间戳 (ms)
         */
        fun onFrequencyDetected(frequency: Float, timestamp: Long)
    }
    
    /**
     * VIS 码监听器接口
     */
    fun interface VisCodeListener {
        /**
         * 当检测到 VIS 码时调用
         * @param visCode 检测到的 VIS 码
         * @param mode 对应的 SSTV 模式（如果已知）
         */
        fun onVisCodeDetected(visCode: Int, mode: SstvMode?)
    }
    
    /**
     * 原始音频监听器接口
     * 
     * 用于 Producer-Consumer 模式，将原始音频数据传递给消费者（解码器）
     */
    fun interface RawAudioListener {
        /**
         * 当有原始音频数据时调用
         * @param samples 原始音频采样数据 (16-bit PCM)
         * @param timestamp 时间戳 (ms)
         */
        fun onAudioSamples(samples: ShortArray, timestamp: Long)
    }
    
    /**
     * 设置频率监听器
     */
    fun setFrequencyListener(listener: FrequencyListener?) {
        this.frequencyListener = listener
    }
    
    /**
     * 设置 VIS 码监听器
     */
    fun setVisCodeListener(listener: VisCodeListener?) {
        this.visCodeListener = listener
    }
    
    /**
     * 设置原始音频监听器
     * 
     * 用于流式解码器的 Producer-Consumer 模式
     */
    fun setRawAudioListener(listener: RawAudioListener?) {
        this.rawAudioListener = listener
    }
    
    /**
     * 启用或禁用 VIS 检测
     */
    fun setVisDetectionEnabled(enabled: Boolean) {
        visDetectionEnabled = enabled
        if (enabled) {
            visCodeDetector.reset()
        }
    }
    
    // 缓冲区大小
    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val desiredSize = (SAMPLE_RATE * BUFFER_SIZE_MS / 1000) * 2 // 16bit = 2 bytes per sample
        maxOf(minBufferSize, desiredSize)
    }
    
    /**
     * 初始化 AudioRecord
     * @return true 如果初始化成功
     */
    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败")
                audioRecord?.release()
                audioRecord = null
                false
            } else {
                Log.d(TAG, "AudioRecord 初始化成功 - 采样率: $SAMPLE_RATE Hz, 缓冲区: $bufferSize bytes")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 创建失败: ${e.message}")
            false
        }
    }
    
    /**
     * 开始录音和频率检测
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "已在录音中")
            return
        }
        
        if (audioRecord == null && !initialize()) {
            Log.e(TAG, "无法开始录音: AudioRecord 未初始化")
            return
        }
        
        // 重置 VIS 检测器
        visCodeDetector.reset()
        _detectedVisCode.value = null
        _detectedMode.value = null
        
        processingScope = CoroutineScope(Dispatchers.Default)
        
        recordingJob = processingScope?.launch {
            try {
                audioRecord?.startRecording()
                _isRecording.value = true
                Log.d(TAG, "========== SSTV 录音开始 (Goertzel + VIS 检测) ==========")
                
                val buffer = ShortArray(bufferSize / 2)  // 16bit = 2 bytes per sample
                
                while (isActive && _isRecording.value) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readCount > 0) {
                        // 使用读取到的有效数据进行频率检测
                        val validBuffer = if (readCount < buffer.size) {
                            buffer.copyOf(readCount)
                        } else {
                            buffer
                        }
                        
                        // ========== 原始音频回调 (Producer-Consumer 模式) ==========
                        // 将原始音频数据传递给流式解码器
                        rawAudioListener?.onAudioSamples(
                            validBuffer.copyOf(),  // 传递副本避免并发问题
                            System.currentTimeMillis()
                        )
                        
                        // 使用 Goertzel 算法进行频率分析（主要用于信号强度）
                        val analysisResult = frequencyAnalyzer.analyze(validBuffer)
                        
                        // 使用过零检测获取更接近真实的连续频率
                        val zeroCrossFreq = getInstantFrequency(validBuffer)
                        val instantFreq = if (zeroCrossFreq > 0f) {
                            zeroCrossFreq
                        } else {
                            // 回退到 Goertzel 估算（离散频点）
                            analysisResult.estimatedFrequency
                        }
                        
                        _currentFrequency.value = instantFreq
                        
                        // 应用滑动平均滤波器
                        val smoothedFreq = applyMovingAverageFilter(instantFreq)
                        _smoothedFrequency.value = smoothedFreq
                        
                        // VIS 码检测（如果启用）
                        if (visDetectionEnabled && _detectedVisCode.value == null) {
                            val visCode = visCodeDetector.processSamples(validBuffer)
                            if (visCode != null && visCode >= 0) {
                                _detectedVisCode.value = visCode
                                val mode = SstvModeRepository.getModeByVisCode(visCode)
                                _detectedMode.value = mode
                                
                                Log.d(TAG, "检测到 VIS 码: $visCode, 模式: ${mode?.modeName ?: "未知"}")
                                visCodeListener?.onVisCodeDetected(visCode, mode)
                            }
                        }
                        
                        // 输出到 Logcat
                        if (smoothedFreq > MIN_VALID_FREQUENCY && smoothedFreq < MAX_VALID_FREQUENCY) {
                            Log.d(TAG, "检测频率: ${smoothedFreq.toInt()} Hz " +
                                  "(强度: ${String.format("%.2f", analysisResult.signalStrength)}, " +
                                  "VIS状态: ${visCodeDetector.getState()})")
                            
                            // 通知监听器 (解码器)
                            frequencyListener?.onFrequencyDetected(
                                smoothedFreq,
                                System.currentTimeMillis()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "录音过程出错: ${e.message}")
            } finally {
                stopRecordingInternal()
            }
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            return
        }
        
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        Log.d(TAG, "========== SSTV 录音停止 ==========")
    }
    
    /**
     * 内部停止录音清理
     */
    private fun stopRecordingInternal() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "停止 AudioRecord 出错: ${e.message}")
        }
        _isRecording.value = false
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
        processingScope?.cancel()
        processingScope = null
        
        audioRecord?.release()
        audioRecord = null
        
        frequencyBuffer.clear()
        Log.d(TAG, "SstvAudioProcessor 资源已释放")
    }
    
    /**
     * 过零检测算法 - 计算音频缓冲区的瞬时频率（作为 Goertzel 的回退方案）
     * 
     * 原理：统计信号从正到负或从负到正的过零次数，
     * 频率 = (过零次数 / 2) / 时间
     * 
     * @param buffer 音频缓冲区 (16bit PCM)
     * @return 检测到的频率 (Hz)，如果无法检测返回 0
     */
    fun getInstantFrequency(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        
        var zeroCrossings = 0
        var previousSample = buffer[0]
        
        // 统计过零次数
        for (i in 1 until buffer.size) {
            val currentSample = buffer[i]
            
            // 检测符号变化 (过零)
            // 正到负：previous > 0 && current <= 0
            // 负到正：previous < 0 && current >= 0
            if ((previousSample > 0 && currentSample <= 0) ||
                (previousSample < 0 && currentSample >= 0)) {
                zeroCrossings++
            }
            
            previousSample = currentSample
        }
        
        // 如果过零次数太少，可能是静音或噪声
        if (zeroCrossings < MIN_ZERO_CROSSINGS) {
            return 0f
        }
        
        // 计算频率
        // 一个完整周期有 2 次过零 (正到负 + 负到正)
        // 时间 = 样本数 / 采样率
        val duration = buffer.size.toFloat() / SAMPLE_RATE
        val frequency = (zeroCrossings.toFloat() / 2f) / duration
        
        return frequency
    }
    
    /**
     * 滑动平均滤波器 - 平滑频率读数
     * 
     * @param newFrequency 新的频率值
     * @return 平滑后的频率值
     */
    private fun applyMovingAverageFilter(newFrequency: Float): Float {
        // 如果新频率无效，不加入缓冲区
        if (newFrequency <= 0f) {
            return if (frequencyBuffer.isNotEmpty()) {
                frequencyBuffer.average().toFloat()
            } else {
                0f
            }
        }
        
        // 添加新值到缓冲区
        frequencyBuffer.addLast(newFrequency)
        
        // 保持窗口大小
        while (frequencyBuffer.size > MOVING_AVERAGE_WINDOW) {
            frequencyBuffer.removeFirst()
        }
        
        // 计算平均值
        return if (frequencyBuffer.isNotEmpty()) {
            frequencyBuffer.average().toFloat()
        } else {
            0f
        }
    }
    
    /**
     * 清空滤波器缓冲区
     */
    fun resetFilter() {
        frequencyBuffer.clear()
        _currentFrequency.value = 0f
        _smoothedFrequency.value = 0f
        visCodeDetector.reset()
        _detectedVisCode.value = null
        _detectedMode.value = null
    }
    
    /**
     * 获取当前 VIS 检测状态
     */
    fun getVisDetectionState(): VisCodeDetector.VisState {
        return visCodeDetector.getState()
    }
    
    /**
     * 手动设置检测到的模式（用于跳过 VIS 检测）
     */
    fun setDetectedMode(mode: SstvMode) {
        _detectedMode.value = mode
        _detectedVisCode.value = mode.visCode
        visDetectionEnabled = false  // 禁用自动 VIS 检测
    }
}
