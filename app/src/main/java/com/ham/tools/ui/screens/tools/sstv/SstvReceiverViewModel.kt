package com.ham.tools.ui.screens.tools.sstv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ham.tools.ui.screens.tools.sstv.decoder.ScanLine
import com.ham.tools.ui.screens.tools.sstv.decoder.SstvDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSTV 接收器 ViewModel - Robot36 风格
 * 
 * 简化架构：
 * 1. 音频采集
 * 2. 解调 + 解码
 * 3. 瀑布图显示
 */
@HiltViewModel
class SstvReceiverViewModel @Inject constructor() : ViewModel() {
    
    companion object {
        private const val TAG = "SstvReceiverViewModel"
    }
    
    // ==================== 组件 ====================
    
    private val audioProcessor = SstvAudioProcessor()
    private val decoder = SstvDecoder()
    
    private var decodingJob: Job? = null
    
    // ==================== 音频 Flow ====================
    
    private val _audioFlow = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    // ==================== 公开状态 ====================
    
    private val _uiState = MutableStateFlow(SstvReceiverUiState())
    val uiState: StateFlow<SstvReceiverUiState> = _uiState.asStateFlow()
    
    /** 扫描线输出流 - UI 应直接订阅此流 */
    val scanLineFlow: SharedFlow<ScanLine> = decoder.scanLineFlow
    
    // ==================== 初始化 ====================
    
    init {
        // 监听音频处理器状态
        viewModelScope.launch {
            combine(
                audioProcessor.isRecording,
                decoder.currentFrequency,
                decoder.signalStrength,
                decoder.isSynced,
                decoder.currentLine
            ) { isRecording, frequency, signalStrength, isSynced, currentLine ->
                _uiState.value.copy(
                    isRecording = isRecording,
                    currentFrequency = frequency,
                    signalStrength = signalStrength,
                    isSynced = isSynced,
                    currentLine = currentLine
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
    
    // ==================== 公开方法 ====================
    
    fun setPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = granted)
    }
    
    fun toggleRecording() {
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
        
        Log.d(TAG, "开始 SSTV 解码")
        
        // 重置状态
        decoder.reset()
        audioProcessor.resetFilter()
        
        // 启动解码协程
        decodingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                decoder.startDecoding(_audioFlow)
            } catch (e: Exception) {
                Log.e(TAG, "解码错误: ${e.message}")
            }
        }
        
        // 连接音频桥接
        audioProcessor.setRawAudioListener { samples, _ ->
            viewModelScope.launch(Dispatchers.Default) {
                _audioFlow.emit(samples)
            }
        }
        
        // 启动音频处理器
        audioProcessor.startRecording()
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        Log.d(TAG, "停止 SSTV 解码")
        
        audioProcessor.setRawAudioListener(null)
        audioProcessor.setFrequencyListener(null)
        audioProcessor.stopRecording()
        
        decodingJob?.cancel()
        decodingJob = null
        
        decoder.stop()
    }
    
    // ==================== 生命周期 ====================
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel 清理")
        
        stopRecording()
        audioProcessor.release()
        decoder.release()
    }
}

/**
 * SSTV 接收器 UI 状态
 */
data class SstvReceiverUiState(
    val hasPermission: Boolean = false,
    val isRecording: Boolean = false,
    val currentFrequency: Float = 1900f,
    val decoderState: SstvDecoder.DecoderState = SstvDecoder.DecoderState.IDLE,
    val currentLine: Int = 0,
    val signalStrength: Float = 0f,
    val isSynced: Boolean = false
)
