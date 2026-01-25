package com.ham.tools.ui.screens.tools.sstv.decoder

import android.graphics.Bitmap
import android.util.Log
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.ColorConverter
import com.ham.tools.ui.screens.tools.sstv.decoder.dsp.ExponentialMovingAverage
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SSTV 解码器 - 移植自 Robot36
 * 
 * ## 核心原则：自由运行模式
 * 
 * 参考 Robot36 的 Decoder.java：
 * - 使用 scopeBuffer 作为瀑布图缓冲区
 * - 持续解码显示，不依赖同步信号
 * - 同步脉冲用于校准行时序
 */
class SstvDecoder {
    
    companion object {
        private const val TAG = "SstvDecoder"
        
        const val SAMPLE_RATE = 44100
        
        // 瀑布图尺寸
        const val SCOPE_WIDTH = 640
        const val SCOPE_HEIGHT = 1280  // 双缓冲
    }
    
    enum class DecoderState(val displayName: String) {
        IDLE("空闲"),
        RUNNING("运行中"),
        SYNCED("已同步"),
        FRAME_COMPLETE("帧完成")
    }
    
    // ==================== 状态流 ====================
    
    private val _state = MutableStateFlow(DecoderState.IDLE)
    val state: StateFlow<DecoderState> = _state.asStateFlow()
    
    private val _currentLine = MutableStateFlow(0)
    val currentLine: StateFlow<Int> = _currentLine.asStateFlow()
    
    private val _signalStrength = MutableStateFlow(0f)
    val signalStrength: StateFlow<Float> = _signalStrength.asStateFlow()
    
    private val _isSynced = MutableStateFlow(false)
    val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()
    
    private val _currentFrequency = MutableStateFlow(0f)
    val currentFrequency: StateFlow<Float> = _currentFrequency.asStateFlow()
    
    // ==================== 输出流 ====================
    
    private val _scanLineFlow = MutableSharedFlow<ScanLine>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scanLineFlow: SharedFlow<ScanLine> = _scanLineFlow.asSharedFlow()
    
    private val _completedFrameFlow = MutableSharedFlow<Bitmap>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val completedFrameFlow: SharedFlow<Bitmap> = _completedFrameFlow.asSharedFlow()
    
    // ==================== 内部状态 ====================
    
    private var decoderScope: CoroutineScope? = null
    
    // 解调器
    private val demodulator = SstvDemodulator(SAMPLE_RATE)
    
    // 瀑布图缓冲区 (类似 Robot36 的 scopeBuffer)
    private val scopeBuffer = PixelBuffer(SCOPE_WIDTH, SCOPE_HEIGHT)
    
    // 扫描线缓冲区
    private val scanLineBuffer = FloatArray(SAMPLE_RATE * 7) // 最大 7 秒一行
    private val scratchBuffer = FloatArray(SAMPLE_RATE)
    
    // 行解码低通滤波器
    private val lowPassFilter = ExponentialMovingAverage()
    
    // 当前状态
    private var currentSample = 0
    private var lastSyncPulseIndex = 0
    private var currentScanLineSamples = 0
    private var lastFrequencyOffset = 0f
    
    // 同步脉冲追踪
    private val syncPulseHistory = IntArray(5)
    private val scanLineHistory = IntArray(4)
    private var syncHistoryIndex = 0
    
    // ==================== API ====================
    
    suspend fun startDecoding(audioFlow: Flow<ShortArray>) {
        Log.d(TAG, "========== 开始 Robot36 风格解码 ==========")
        
        reset()
        _state.value = DecoderState.RUNNING
        
        decoderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // 默认扫描线采样数（基于典型 SSTV 模式）
        currentScanLineSamples = (0.15 * SAMPLE_RATE).toInt()  // 约 150ms
        
        audioFlow.collect { samples ->
            if (decoderScope?.isActive == true) {
                processAudio(samples)
            }
        }
    }
    
    fun stop() {
        _state.value = DecoderState.IDLE
        decoderScope?.cancel()
        decoderScope = null
    }
    
    fun reset() {
        _state.value = DecoderState.IDLE
        _currentLine.value = 0
        _signalStrength.value = 0f
        _isSynced.value = false
        
        currentSample = 0
        lastSyncPulseIndex = 0
        lastFrequencyOffset = 0f
        syncHistoryIndex = 0
        
        scopeBuffer.line = 0
        scopeBuffer.pixels.fill(0)
        scanLineBuffer.fill(0f)
        scratchBuffer.fill(0f)
        syncPulseHistory.fill(0)
        scanLineHistory.fill(0)
        
        demodulator.reset()
        lowPassFilter.reset()
    }
    
    fun release() {
        stop()
    }
    
    // ==================== 核心解码 ====================
    
    private suspend fun processAudio(samples: ShortArray) {
        // 转换为浮点
        val floatBuffer = FloatArray(samples.size) { i ->
            samples[i].toFloat() / 32768f
        }
        
        // 解调
        val syncPulseDetected = demodulator.process(floatBuffer)
        val syncPulseIndex = currentSample + demodulator.syncPulseOffset
        
        // 存储解调后的频率值到扫描线缓冲区
        for (j in floatBuffer.indices) {
            if (currentSample < scanLineBuffer.size) {
                scanLineBuffer[currentSample] = floatBuffer[j]
            }
            currentSample++
            
            // 缓冲区满时移位
            if (currentSample >= scanLineBuffer.size) {
                shiftSamples(currentScanLineSamples)
            }
        }
        
        // 更新当前频率显示
        if (floatBuffer.isNotEmpty()) {
            // 归一化频率值转换回实际频率
            val avgFreqValue = floatBuffer.average().toFloat()
            val centerFreq = 1900f
            val bandwidth = 800f
            _currentFrequency.value = centerFreq + avgFreqValue * bandwidth / 2
        }
        
        // 处理同步脉冲或自由运行
        if (syncPulseDetected) {
            processSyncPulse(syncPulseIndex)
        } else if (currentSample > lastSyncPulseIndex + (currentScanLineSamples * 5) / 4) {
            // 自由运行模式：超时后强制解码一行
            decodeScanLine(lastSyncPulseIndex, currentScanLineSamples, lastFrequencyOffset)
            lastSyncPulseIndex += currentScanLineSamples
        }
    }
    
    private suspend fun processSyncPulse(syncPulseIndex: Int) {
        // 记录同步脉冲历史
        syncPulseHistory[syncHistoryIndex % syncPulseHistory.size] = syncPulseIndex
        
        if (syncHistoryIndex > 0) {
            val prevPulse = syncPulseHistory[(syncHistoryIndex - 1) % syncPulseHistory.size]
            val scanLineSamples = syncPulseIndex - prevPulse
            
            if (scanLineSamples > 0) {
                scanLineHistory[syncHistoryIndex % scanLineHistory.size] = scanLineSamples
                
                // 更新扫描线采样数
                val avgScanLine = scanLineHistory.filter { it > 0 }.average()
                if (avgScanLine > SAMPLE_RATE * 0.05) {
                    currentScanLineSamples = avgScanLine.toInt()
                    _isSynced.value = true
                    _state.value = DecoderState.SYNCED
                }
            }
        }
        
        syncHistoryIndex++
        
        // 解码当前行
        lastFrequencyOffset = demodulator.frequencyOffset
        decodeScanLine(lastSyncPulseIndex, currentScanLineSamples, lastFrequencyOffset)
        lastSyncPulseIndex = syncPulseIndex
        
        // 移位缓冲区
        shiftSamples(syncPulseIndex)
    }
    
    private suspend fun decodeScanLine(syncPulseIndex: Int, scanLineSamples: Int, frequencyOffset: Float) {
        if (syncPulseIndex < 0 || scanLineSamples <= 0) return
        if (syncPulseIndex + scanLineSamples > scanLineBuffer.size) return
        
        // 确定输出宽度
        val horizontalPixels = when {
            scanLineSamples < (0.125 * SAMPLE_RATE).toInt() -> SCOPE_WIDTH / 4
            scanLineSamples < (0.175 * SAMPLE_RATE).toInt() -> SCOPE_WIDTH / 2
            else -> SCOPE_WIDTH
        }
        
        // 低通滤波 + 解码
        lowPassFilter.cutoff(horizontalPixels.toDouble(), (2 * scanLineSamples).toDouble(), 2)
        lowPassFilter.reset()
        
        // 前向滤波
        for (i in 0 until scanLineSamples.coerceAtMost(scratchBuffer.size)) {
            val srcIdx = syncPulseIndex + i
            if (srcIdx < scanLineBuffer.size) {
                scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[srcIdx])
            }
        }
        
        // 反向滤波 + 频率到亮度转换
        lowPassFilter.reset()
        for (i in (scanLineSamples - 1).coerceAtMost(scratchBuffer.size - 1) downTo 0) {
            scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset)
        }
        
        // 生成像素
        val pixels = IntArray(horizontalPixels)
        for (i in 0 until horizontalPixels) {
            val position = (i * scanLineSamples) / horizontalPixels
            if (position < scratchBuffer.size) {
                pixels[i] = ColorConverter.GRAY(scratchBuffer[position])
            }
        }
        
        // 写入瀑布图缓冲区
        val scopeLine = scopeBuffer.line
        System.arraycopy(pixels, 0, scopeBuffer.pixels, scopeLine * SCOPE_WIDTH, 
            pixels.size.coerceAtMost(SCOPE_WIDTH))
        
        // 双缓冲复制
        System.arraycopy(
            scopeBuffer.pixels, scopeLine * SCOPE_WIDTH,
            scopeBuffer.pixels, (scopeLine + SCOPE_HEIGHT / 2) * SCOPE_WIDTH,
            SCOPE_WIDTH
        )
        
        scopeBuffer.line = (scopeBuffer.line + 1) % (SCOPE_HEIGHT / 2)
        _currentLine.value = scopeBuffer.line
        
        // 发射扫描线
        val scanLine = ScanLine(
            lineNumber = scopeBuffer.line,
            pixels = pixels.copyOf(),
            signalQuality = _signalStrength.value,
            isSynced = _isSynced.value
        )
        _scanLineFlow.emit(scanLine)
    }
    
    /**
     * 频率值转亮度
     * 
     * @param frequency 归一化频率值 [-1, 1]
     * @param offset 频率偏移
     * @return 亮度值 [0, 1]
     */
    private fun freqToLevel(frequency: Float, offset: Float): Float {
        return 0.5f * (frequency - offset + 1f)
    }
    
    private fun shiftSamples(shift: Int) {
        if (shift <= 0 || shift > currentSample) return
        
        currentSample -= shift
        lastSyncPulseIndex -= shift
        
        // 移位同步脉冲历史
        for (i in syncPulseHistory.indices) {
            syncPulseHistory[i] -= shift
        }
        
        // 移位扫描线缓冲区
        if (currentSample > 0) {
            System.arraycopy(scanLineBuffer, shift, scanLineBuffer, 0, currentSample)
        }
    }
    
    /**
     * 像素缓冲区
     */
    class PixelBuffer(val width: Int, height: Int) {
        val pixels = IntArray(width * height)
        var line = 0
    }
}

/**
 * 扫描线数据
 */
data class ScanLine(
    val lineNumber: Int,
    val pixels: IntArray,
    val signalQuality: Float = 1f,
    val isSynced: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScanLine
        return lineNumber == other.lineNumber && pixels.contentEquals(other.pixels)
    }
    
    override fun hashCode(): Int = 31 * lineNumber + pixels.contentHashCode()
}
