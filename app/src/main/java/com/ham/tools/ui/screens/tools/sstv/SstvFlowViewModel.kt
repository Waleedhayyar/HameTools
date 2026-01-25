package com.ham.tools.ui.screens.tools.sstv

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ham.tools.ui.screens.tools.sstv.strategies.Martin1Strategy
import com.ham.tools.ui.screens.tools.sstv.strategies.Robot36Strategy
import javax.inject.Inject

/**
 * SSTV 流式 ViewModel - 自由运行版本
 * 
 * 管理基于 Flow 的 SSTV 自由运行解码器，使用 Producer-Consumer 模式。
 * 
 * ## 自由运行模式核心特性
 * 
 * 1. **永不阻塞**: 扫描线以固定速度移动，即使没有信号也显示雪花
 * 2. **同步对齐**: 检测到 1200Hz 同步脉冲时微调时钟
 * 3. **实时输出**: 每行解码完成立即 emit，UI 可实时渲染
 * 
 * ## 架构概览
 * 
 * ```
 * ┌─────────────────────┐
 * │   SstvAudioProcessor│  ← 生产者: 采集音频
 * │   (AudioRecord)     │
 * └──────────┬──────────┘
 *            │ Flow<ShortArray>
 *            ▼
 * ┌──────────────────────────┐
 * │ FreeRunningSstvDecoder   │  ← 消费者: 自由运行解码
 * │ (永不阻塞，持续输出)      │
 * └──────────┬───────────────┘
 *            │ SharedFlow<SstvScanLine>
 *            ▼
 * ┌─────────────────────┐
 * │   UI / Compose      │  ← 消费者: 60FPS 渲染
 * │   (Canvas 扫描线)   │
 * └─────────────────────┘
 * ```
 */
@HiltViewModel
class SstvFlowViewModel @Inject constructor() : ViewModel() {
    
    companion object {
        private const val TAG = "SstvFlowViewModel"
    }
    
    // ==================== 音频处理器 ====================
    
    private val audioProcessor = SstvAudioProcessor()
    
    // ==================== 自由运行解码器 ====================
    
    private val decoder = FreeRunningSstvDecoder()
    
    // 解码协程 Job
    private var decodingJob: Job? = null
    
    // ==================== 音频 Flow (Producer) ====================
    
    /**
     * 音频数据 SharedFlow
     * 
     * 音频处理器通过此 Flow 生产数据，解码器消费数据。
     * 这是 Producer-Consumer 模式的核心连接点。
     */
    private val _audioFlow = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 50,  // 缓冲约 2.5 秒的音频
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    // ==================== 扫描线 Flow (Output) ====================
    
    /**
     * 扫描线输出 SharedFlow
     * 
     * 每解完一行立即 emit，UI 层应订阅此 Flow 实现扫描线效果。
     */
    val scanLineFlow: SharedFlow<SstvScanLine> = decoder.scanLineFlow
    
    // ==================== UI 状态 ====================
    
    private val _uiState = MutableStateFlow(SstvFlowUiState())
    val uiState: StateFlow<SstvFlowUiState> = _uiState.asStateFlow()
    
    // 解码后的完整 Bitmap
    private val _decodedBitmap = MutableStateFlow<Bitmap?>(null)
    val decodedBitmap: StateFlow<Bitmap?> = _decodedBitmap.asStateFlow()

    // 最近完成的一帧（用于输出展示/历史）
    private val _completedBitmap = MutableStateFlow<Bitmap?>(null)
    val completedBitmap: StateFlow<Bitmap?> = _completedBitmap.asStateFlow()
    
    // 调试信息
    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()
    
    // ==================== 策略管理 ====================
    
    /** 所有可用策略 */
    val availableStrategies: List<SstvModeStrategy> = FreeRunningDecoderFactory.getAllStrategies()
    
    // 当前选中的策略
    private val _selectedStrategy = MutableStateFlow<SstvModeStrategy>(Robot36Strategy())
    val selectedStrategy: StateFlow<SstvModeStrategy> = _selectedStrategy.asStateFlow()
    
    // 是否启用自动模式检测
    private val _autoModeDetection = MutableStateFlow(false)  // 默认关闭，使用手动选择
    val autoModeDetection: StateFlow<Boolean> = _autoModeDetection.asStateFlow()
    
    // ==================== Bitmap 渲染 ====================
    
    // 像素缓冲区
    private var pixelBuffer: IntArray = IntArray(0)
    private var currentBitmap: Bitmap? = null
    
    // ==================== 初始化 ====================
    
    init {
        // 设置默认策略为 Robot 36
        decoder.setMode(Robot36Strategy())
        
        // 监听音频处理器状态
        viewModelScope.launch {
            combine(
                audioProcessor.isRecording,
                audioProcessor.smoothedFrequency,
                audioProcessor.detectedMode
            ) { isRecording, frequency, detectedMode ->
                _uiState.value.copy(
                    isRecording = isRecording,
                    currentFrequency = frequency
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
        
        // 监听解码器状态
        viewModelScope.launch {
            combine(
                decoder.runState,
                decoder.currentLine,
                decoder.modeName,
                decoder.signalStrength,
                decoder.isSynced
            ) { state, line, modeName, signalStrength, isSynced ->
                DecoderStateBundle(state, line, modeName, signalStrength, isSynced)
            }.collect { bundle ->
                _uiState.value = _uiState.value.copy(
                    decoderState = bundle.state,
                    decodedLines = bundle.line,
                    modeName = bundle.modeName,
                    signalStrength = bundle.signalStrength,
                    isSynced = bundle.isSynced
                )
                
                // 更新调试信息
                val syncStatus = if (bundle.isSynced) "✓同步" else "未同步"
                val signalPercent = (bundle.signalStrength * 100).toInt()
                when (bundle.state) {
                    FreeRunningSstvDecoder.RunState.STOPPED -> 
                        _debugInfo.value = "已停止"
                    FreeRunningSstvDecoder.RunState.RUNNING -> 
                        _debugInfo.value = "自由运行: 行 ${bundle.line + 1}/${_uiState.value.totalLines} | 信号 $signalPercent% | $syncStatus"
                    FreeRunningSstvDecoder.RunState.SYNCED -> 
                        _debugInfo.value = "已同步: 行 ${bundle.line + 1}/${_uiState.value.totalLines} | 信号 $signalPercent%"
                    FreeRunningSstvDecoder.RunState.FRAME_COMPLETE -> 
                        _debugInfo.value = "帧完成！开始下一帧..."
                }
            }
        }
        
        // 监听扫描线输出，更新 Bitmap (内部处理，不影响 UI 直接收集)
        viewModelScope.launch {
            decoder.scanLineFlow.collect { scanLine ->
                updateBitmapWithScanLine(scanLine)
            }
        }
        
        // 监听 VIS 码检测
        audioProcessor.setVisCodeListener { visCode, mode ->
            viewModelScope.launch(Dispatchers.Main) {
                if (_autoModeDetection.value && mode != null) {
                    // 自动切换到检测到的模式
                    val strategy = FreeRunningDecoderFactory.getAllStrategies()
                        .find { it.visCode == visCode }
                    if (strategy != null) {
                        setStrategy(strategy)
                        _debugInfo.value = "检测到 VIS $visCode: ${strategy.modeName}"
                    }
                }
            }
        }
        
        Log.d(TAG, "SstvFlowViewModel 初始化完成 (自由运行模式)")
    }
    
    /** 解码器状态捆绑类 */
    private data class DecoderStateBundle(
        val state: FreeRunningSstvDecoder.RunState,
        val line: Int,
        val modeName: String,
        val signalStrength: Float,
        val isSynced: Boolean
    )
    
    // ==================== 公开 API ====================
    
    /**
     * 设置麦克风权限状态
     */
    fun setPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        Log.d(TAG, "麦克风权限: $granted")
    }
    
    /**
     * 设置解码策略
     * 
     * 切换模式时会重启解码器
     */
    fun setStrategy(strategy: SstvModeStrategy) {
        val wasRecording = _uiState.value.isRecording
        
        // 如果正在录音，先停止
        if (wasRecording) {
            stopRecording()
        }
        
        _selectedStrategy.value = strategy
        decoder.setMode(strategy)
        _autoModeDetection.value = false  // 手动选择后禁用自动检测
        
        // 重新初始化 Bitmap
        initializeBitmap(strategy.width, strategy.height)
        
        _uiState.value = _uiState.value.copy(
            totalLines = strategy.height,
            modeName = strategy.modeName
        )
        
        Log.d(TAG, "切换策略: ${strategy.modeName} (${strategy.width}×${strategy.height})")
        _debugInfo.value = "已切换到 ${strategy.modeName}"
        
        // 如果之前在录音，重新开始
        if (wasRecording) {
            startRecording()
        }
    }
    
    /**
     * 设置自动模式检测
     */
    fun setAutoModeDetection(enabled: Boolean) {
        _autoModeDetection.value = enabled
        audioProcessor.setVisDetectionEnabled(enabled)
        
        if (enabled) {
            _selectedStrategy.value = SstvStrategyFactory.getDefault()
        }
    }
    
    /**
     * 切换录音状态
     */
    fun toggleRecording() {
        if (!_uiState.value.hasPermission) {
            Log.w(TAG, "没有麦克风权限")
            return
        }
        
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * 开始录音和解码
     * 
     * 启动自由运行解码器，扫描线会立即开始移动，
     * 即使没有有效 SSTV 信号也会显示雪花屏。
     */
    fun startRecording() {
        if (!_uiState.value.hasPermission) {
            Log.w(TAG, "没有麦克风权限")
            return
        }
        
        Log.d(TAG, "========== 开始 SSTV 自由运行解码 ==========")
        
        // 重置状态
        resetState()
        
        // 初始化 Bitmap
        val strategy = _selectedStrategy.value
        initializeBitmap(strategy.width, strategy.height)
        
        // 连接音频处理器到音频 Flow (Producer)
        audioProcessor.setFrequencyListener { frequency, timestamp ->
            // 保留频率监听用于 VIS 检测和调试
        }
        
        // 启动解码协程 (Consumer) - 自由运行模式
        decodingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // 自由运行解码器：永不阻塞，持续输出
                decoder.startDecoding(_audioFlow)
            } catch (e: Exception) {
                Log.e(TAG, "解码错误: ${e.message}")
                withContext(Dispatchers.Main) {
                    _debugInfo.value = "解码错误: ${e.message}"
                }
            }
        }
        
        // 启动音频处理器并桥接到 audioFlow
        startAudioBridge()
        
        audioProcessor.startRecording()
        
        _debugInfo.value = "自由运行模式启动 - 等待信号..."
    }
    
    /**
     * 启动音频桥接
     * 
     * 将音频处理器的回调数据转换为 Flow
     * 这是 Producer-Consumer 模式的核心连接点
     */
    private fun startAudioBridge() {
        // 设置原始音频监听器，将音频数据桥接到 audioFlow
        audioProcessor.setRawAudioListener { samples, _ ->
            // 在协程中 emit 数据到 audioFlow
            viewModelScope.launch(Dispatchers.Default) {
                _audioFlow.emit(samples)
            }
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        Log.d(TAG, "停止 SSTV 解码...")
        
        audioProcessor.setFrequencyListener(null)
        audioProcessor.setRawAudioListener(null)
        audioProcessor.stopRecording()
        
        decodingJob?.cancel()
        decodingJob = null
        
        decoder.stop()
        
        _debugInfo.value = "已停止"
    }
    
    /**
     * 重置解码器
     * 
     * 清除当前图像并重新开始解码
     */
    fun resetDecoder() {
        val wasRecording = _uiState.value.isRecording
        
        decoder.reset()
        
        // 重新初始化 Bitmap（清除为黑色）
        val strategy = _selectedStrategy.value
        initializeBitmap(strategy.width, strategy.height)
        
        resetState()
        _debugInfo.value = "解码器已重置"
        
        // 如果正在录音，继续
        if (wasRecording) {
            startRecording()
        }
    }
    
    /**
     * 获取当前扫描线进度
     */
    fun getProgress(): Float = decoder.getProgress()
    
    // ==================== 内部方法 ====================
    
    /**
     * 重置状态
     */
    private fun resetState() {
        audioProcessor.resetFilter()
        _decodedBitmap.value = null
        _completedBitmap.value = null
        _uiState.value = _uiState.value.copy(
            decoderState = FreeRunningSstvDecoder.RunState.STOPPED,
            decodedLines = 0,
            signalStrength = 0f,
            isSynced = false
        )
    }
    
    /**
     * 初始化 Bitmap
     */
    private fun initializeBitmap(width: Int, height: Int) {
        pixelBuffer = IntArray(width * height)
        pixelBuffer.fill(Color.BLACK)
        
        currentBitmap?.recycle()
        currentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
    
    /**
     * 使用扫描线更新 Bitmap
     * 
     * 每收到一行扫描线数据，立即更新对应行的像素
     */
    private suspend fun updateBitmapWithScanLine(scanLine: SstvScanLine) {
        withContext(Dispatchers.Main) {
            val strategy = _selectedStrategy.value
            val width = strategy.width
            val lineNumber = scanLine.lineNumber
            
            // 检查边界
            if (lineNumber < 0 || lineNumber >= strategy.height) return@withContext
            if (scanLine.pixels.size != width) return@withContext
            
            // 更新像素缓冲区
            val offset = lineNumber * width
            System.arraycopy(scanLine.pixels, 0, pixelBuffer, offset, width)
            
            // 更新 Bitmap
            currentBitmap?.setPixels(
                scanLine.pixels,
                0,
                width,
                0,
                lineNumber,
                width,
                1
            )
            
            // 通知 UI 更新
            _decodedBitmap.value = currentBitmap?.copy(Bitmap.Config.ARGB_8888, false)

            // 最后一行完成时，保存一份“完成图像”用于输出展示
            if (lineNumber == strategy.height - 1) {
                _completedBitmap.value = currentBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            }
            
            Log.d(TAG, "扫描线 ${lineNumber + 1}/${strategy.height} 已渲染")
        }
    }
    
    /**
     * 向音频 Flow 发送数据 (供外部调用)
     * 
     * 这个方法允许外部音频源直接向解码器发送数据。
     */
    suspend fun feedAudio(samples: ShortArray) {
        _audioFlow.emit(samples)
    }
    
    // ==================== 生命周期 ====================
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel 清理")
        
        audioProcessor.setFrequencyListener(null)
        audioProcessor.setVisCodeListener(null)
        audioProcessor.setRawAudioListener(null)
        
        stopRecording()
        audioProcessor.release()
        decoder.release()
        currentBitmap?.recycle()
        currentBitmap = null
    }
}

/**
 * SSTV 流式 UI 状态
 */
data class SstvFlowUiState(
    val hasPermission: Boolean = false,
    val isRecording: Boolean = false,
    val currentFrequency: Float = 0f,
    val decoderState: FreeRunningSstvDecoder.RunState = FreeRunningSstvDecoder.RunState.STOPPED,
    val decodedLines: Int = 0,
    val totalLines: Int = 240,  // Robot 36 默认 240 行
    val modeName: String = "Robot 36",
    val signalStrength: Float = 0f,
    val isSynced: Boolean = false
)
