package com.ham.tools.ui.screens.tools.sstv

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSTV Screen ViewModel
 * 
 * 管理 SSTV 解码器的状态和音频处理器的生命周期。
 * 支持多种 SSTV 模式（Martin 1, Scottie 1 等）的自动检测和解码。
 * 
 * 核心特性:
 * - 实时行回调：每解码一行立即更新 UI（扫描线效果）
 * - 自动 VIS 码检测
 * - 多模式支持
 */
@HiltViewModel
class SstvViewModel @Inject constructor() : ViewModel() {
    
    companion object {
        private const val TAG = "SstvViewModel"
    }
    
    // 音频处理器
    private val audioProcessor = SstvAudioProcessor()
    
    // 解码器
    private var decoder: SstvDecoder? = null
    
    // 扫描线渲染器（用于收集实时行数据）
    private var scanLineRenderer: ScanLineRenderer? = null
    
    // UI 状态
    private val _uiState = MutableStateFlow(SstvUiState())
    val uiState: StateFlow<SstvUiState> = _uiState.asStateFlow()
    
    // 解码后的图像
    private val _decodedBitmap = MutableStateFlow<Bitmap?>(null)
    val decodedBitmap: StateFlow<Bitmap?> = _decodedBitmap.asStateFlow()
    
    // 当前行像素数据（用于扫描线效果）
    private val _currentLinePixels = MutableStateFlow<IntArray?>(null)
    val currentLinePixels: StateFlow<IntArray?> = _currentLinePixels.asStateFlow()
    
    // 调试信息
    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()
    
    // 可用模式列表
    val availableModes: List<SstvMode> = SstvModeRepository.allModes
    
    // 当前选中的模式
    private val _selectedMode = MutableStateFlow(SstvModeRepository.getDefaultMode())
    val selectedMode: StateFlow<SstvMode> = _selectedMode.asStateFlow()
    
    // 是否启用自动模式检测
    private val _autoModeDetection = MutableStateFlow(true)
    val autoModeDetection: StateFlow<Boolean> = _autoModeDetection.asStateFlow()
    
    init {
        // 观察音频处理器的状态变化
        viewModelScope.launch {
            combine(
                audioProcessor.isRecording,
                audioProcessor.currentFrequency,
                audioProcessor.smoothedFrequency,
                audioProcessor.detectedMode
            ) { isRecording, currentFreq, smoothedFreq, detectedMode ->
                _uiState.value.copy(
                    isRecording = isRecording,
                    currentFrequency = currentFreq,
                    smoothedFrequency = smoothedFreq,
                    detectedMode = detectedMode
                )
            }.collect { newState ->
                _uiState.value = newState
                
                // 如果检测到模式且启用了自动检测，更新解码器
                if (newState.detectedMode != null && _autoModeDetection.value) {
                    updateDecoderMode(newState.detectedMode)
                }
            }
        }
    }
    
    /**
     * 设置麦克风权限状态
     */
    fun setPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        Log.d(TAG, "麦克风权限状态: $granted")
    }
    
    /**
     * 手动选择 SSTV 模式
     */
    fun selectMode(mode: SstvMode) {
        _selectedMode.value = mode
        _autoModeDetection.value = false  // 手动选择后禁用自动检测
        
        // 如果正在解码，更新解码器模式
        decoder?.setMode(mode)
        audioProcessor.setDetectedMode(mode)
        
        // 重新初始化扫描线渲染器
        scanLineRenderer = ScanLineRenderer(mode.width, mode.height)
        
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            totalLines = mode.height
        )
        
        Log.d(TAG, "手动选择模式: ${mode.modeName}")
    }
    
    /**
     * 启用/禁用自动模式检测
     */
    fun setAutoModeDetection(enabled: Boolean) {
        _autoModeDetection.value = enabled
        audioProcessor.setVisDetectionEnabled(enabled)
        
        if (enabled) {
            // 重置为默认模式
            _selectedMode.value = SstvModeRepository.getDefaultMode()
        }
        
        Log.d(TAG, "自动模式检测: ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 更新解码器模式
     */
    private fun updateDecoderMode(mode: SstvMode) {
        if (_selectedMode.value.visCode != mode.visCode) {
            _selectedMode.value = mode
            decoder?.setMode(mode)
            
            // 重新初始化扫描线渲染器
            scanLineRenderer = ScanLineRenderer(mode.width, mode.height)
            
            _uiState.value = _uiState.value.copy(
                selectedMode = mode,
                totalLines = mode.height
            )
            
            _debugInfo.value = "检测到模式: ${mode.modeName}"
            Log.d(TAG, "自动检测模式: ${mode.modeName} (VIS: ${mode.visCode})")
        }
    }
    
    /**
     * 切换录音状态
     */
    fun toggleRecording() {
        if (!_uiState.value.hasPermission) {
            Log.w(TAG, "没有麦克风权限，无法录音")
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
     */
    fun startRecording() {
        if (!_uiState.value.hasPermission) {
            Log.w(TAG, "没有麦克风权限")
            return
        }
        
        Log.d(TAG, "开始 SSTV 录音和解码...")
        
        // 重置状态
        audioProcessor.resetFilter()
        _decodedBitmap.value = null
        _currentLinePixels.value = null
        _debugInfo.value = "等待 SSTV 信号..."
        
        // 获取当前模式
        val currentMode = _selectedMode.value
        
        // 初始化扫描线渲染器
        scanLineRenderer = ScanLineRenderer(currentMode.width, currentMode.height)
        
        // 创建并启动解码器
        decoder?.release()
        decoder = SstvDecoder(object : SstvDecoder.DecoderCallback {
            override fun onStateChanged(state: SstvDecoder.State) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        decoderState = state
                    )
                }
            }
            
            /**
             * 【核心回调】实时行解码完成
             * 
             * 每解码完成一行立即触发，实现扫描线效果
             */
            override fun onLineDecoded(lineNumber: Int, pixels: IntArray, totalLines: Int) {
                viewModelScope.launch(Dispatchers.Main) {
                    // 更新扫描线渲染器
                    scanLineRenderer?.updateLine(lineNumber, pixels)
                    
                    // 更新当前行像素（用于扫描线动画）
                    _currentLinePixels.value = pixels
                    
                    // 更新 UI 状态
                    _uiState.value = _uiState.value.copy(
                        decodedLines = lineNumber + 1,
                        totalLines = totalLines
                    )
                    
                    _debugInfo.value = "解码行 ${lineNumber + 1}/$totalLines"
                    
                    Log.d(TAG, "实时回调: 行 ${lineNumber + 1}/$totalLines 已解码")
                }
            }
            
            override fun onProgress(lineNumber: Int, totalLines: Int) {
                // 进度信息已在 onLineDecoded 中处理
            }
            
            override fun onImageUpdated(bitmap: Bitmap) {
                viewModelScope.launch(Dispatchers.Main) {
                    // 复制 Bitmap 以避免线程问题
                    _decodedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            
            override fun onDecodeComplete(bitmap: Bitmap) {
                viewModelScope.launch(Dispatchers.Main) {
                    _decodedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    _debugInfo.value = "解码完成！"
                    Log.d(TAG, "SSTV 图像解码完成")
                }
            }
            
            override fun onError(message: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _debugInfo.value = "错误: $message"
                    Log.e(TAG, "解码错误: $message")
                }
            }
            
            override fun onDebugInfo(info: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _debugInfo.value = info
                }
            }
            
            override fun onModeChanged(mode: SstvMode) {
                viewModelScope.launch(Dispatchers.Main) {
                    _selectedMode.value = mode
                    scanLineRenderer = ScanLineRenderer(mode.width, mode.height)
                    
                    _uiState.value = _uiState.value.copy(
                        selectedMode = mode,
                        totalLines = mode.height
                    )
                    _debugInfo.value = "模式: ${mode.modeName}"
                }
            }
        }).also { 
            it.setMode(currentMode)
            it.start() 
        }
        
        // 设置 VIS 码监听器
        audioProcessor.setVisCodeListener { visCode, mode ->
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    detectedVisCode = visCode,
                    detectedMode = mode
                )
                
                if (mode != null && _autoModeDetection.value) {
                    decoder?.setMode(mode)
                    _debugInfo.value = "VIS 码: $visCode - ${mode.modeName}"
                } else {
                    _debugInfo.value = "VIS 码: $visCode - 未知模式"
                }
            }
        }
        
        // 连接音频处理器和解码器
        audioProcessor.setFrequencyListener { frequency, timestamp ->
            decoder?.feedFrequency(frequency, timestamp)
        }
        
        // 开始录音
        audioProcessor.startRecording()
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        Log.d(TAG, "停止 SSTV 录音...")
        
        audioProcessor.setFrequencyListener(null)
        audioProcessor.setVisCodeListener(null)
        audioProcessor.stopRecording()
        decoder?.stop()
    }
    
    /**
     * 重置解码器 (开始新的解码)
     */
    fun resetDecoder() {
        val currentMode = _selectedMode.value
        
        decoder?.reset()
        audioProcessor.resetFilter()
        scanLineRenderer?.reset()
        
        _decodedBitmap.value = null
        _currentLinePixels.value = null
        _uiState.value = _uiState.value.copy(
            decoderState = SstvDecoder.State.IDLE,
            decodedLines = 0,
            totalLines = currentMode.height,
            detectedVisCode = null,
            detectedMode = null
        )
        _debugInfo.value = "解码器已重置，等待信号..."
    }
    
    /**
     * 获取当前扫描线渲染器的进度
     */
    fun getScanLineProgress(): Float {
        return scanLineRenderer?.getProgress() ?: 0f
    }
    
    /**
     * 获取最后解码的行号
     */
    fun getLastDecodedLine(): Int {
        return scanLineRenderer?.getLastDecodedLine() ?: -1
    }
    
    /**
     * 判断检测到的频率是否为 SSTV 信号特征频率
     */
    fun getSstvSignalType(frequency: Float): SstvSignalType {
        return when {
            frequency < 100f -> SstvSignalType.NONE
            frequency in 1100f..1300f -> SstvSignalType.SYNC      // ~1200 Hz 同步
            frequency in 1400f..1600f -> SstvSignalType.BLACK     // ~1500 Hz 黑色
            frequency in 1800f..2000f -> SstvSignalType.VIS       // ~1900 Hz VIS
            frequency in 2200f..2400f -> SstvSignalType.WHITE     // ~2300 Hz 白色
            frequency in 1000f..2500f -> SstvSignalType.DATA      // 其他数据
            else -> SstvSignalType.NONE
        }
    }
    
    /**
     * 获取 VIS 检测状态描述
     */
    fun getVisDetectionStatus(): String {
        return when (audioProcessor.getVisDetectionState()) {
            VisCodeDetector.VisState.IDLE -> "等待 VIS 信号"
            VisCodeDetector.VisState.LEADER_1 -> "检测 Leader..."
            VisCodeDetector.VisState.BREAK -> "检测 Break..."
            VisCodeDetector.VisState.LEADER_2 -> "检测 Leader 2..."
            VisCodeDetector.VisState.START_BIT -> "检测 Start Bit..."
            VisCodeDetector.VisState.DATA_BITS -> "读取 VIS 数据..."
            VisCodeDetector.VisState.COMPLETE -> "VIS 检测完成"
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel 清理，释放资源")
        audioProcessor.setFrequencyListener(null)
        audioProcessor.setVisCodeListener(null)
        audioProcessor.release()
        decoder?.release()
        decoder = null
        scanLineRenderer = null
    }
}

/**
 * SSTV UI 状态
 */
data class SstvUiState(
    val hasPermission: Boolean = false,
    val isRecording: Boolean = false,
    val currentFrequency: Float = 0f,
    val smoothedFrequency: Float = 0f,
    val decoderState: SstvDecoder.State = SstvDecoder.State.IDLE,
    val decodedLines: Int = 0,
    val totalLines: Int = SstvModeRepository.getDefaultMode().height,
    val selectedMode: SstvMode = SstvModeRepository.getDefaultMode(),
    val detectedVisCode: Int? = null,
    val detectedMode: SstvMode? = null
)

/**
 * SSTV 信号类型
 */
enum class SstvSignalType(val displayName: String, val frequency: String) {
    NONE("无信号", "-"),
    SYNC("同步脉冲", "~1200 Hz"),
    BLACK("黑色", "~1500 Hz"),
    VIS("VIS 码", "~1900 Hz"),
    WHITE("白色", "~2300 Hz"),
    DATA("数据", "1000-2500 Hz")
}
