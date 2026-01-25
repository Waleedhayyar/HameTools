package com.ham.tools.ui.screens.tools.sstv

/**
 * SSTV 扫描线数据类
 * 
 * 表示解码完成的一行像素数据。
 * 每解完一行立即通过 Flow emit 此数据。
 * 
 * 自由运行模式下，即使是噪音也会生成扫描线，
 * 可以通过 [signalQuality] 和 [isSynced] 判断信号质量。
 * 
 * @param lineNumber 当前行号 (0-based)
 * @param pixels 该行的像素数组 (ARGB 格式)
 * @param signalQuality 信号质量 (0.0 - 1.0)，0 表示纯噪音，1 表示完美信号
 * @param isSynced 该行是否检测到同步脉冲
 * @param timestamp 扫描线生成时间戳 (毫秒)
 */
data class SstvScanLine(
    val lineNumber: Int,
    val pixels: IntArray,
    val signalQuality: Float = 1.0f,
    val isSynced: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 是否为有效信号（非纯噪音）
     */
    val isValidSignal: Boolean
        get() = signalQuality > 0.1f
    
    /**
     * 是否为高质量信号
     */
    val isHighQuality: Boolean
        get() = signalQuality > 0.7f && isSynced
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SstvScanLine
        
        if (lineNumber != other.lineNumber) return false
        if (!pixels.contentEquals(other.pixels)) return false
        if (signalQuality != other.signalQuality) return false
        if (isSynced != other.isSynced) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = lineNumber
        result = 31 * result + pixels.contentHashCode()
        result = 31 * result + signalQuality.hashCode()
        result = 31 * result + isSynced.hashCode()
        return result
    }
    
    companion object {
        /**
         * 创建噪音扫描线
         * 
         * @param lineNumber 行号
         * @param width 图像宽度
         * @return 噪音扫描线
         */
        fun noise(lineNumber: Int, width: Int): SstvScanLine {
            val pixels = IntArray(width) { 
                val gray = (kotlin.random.Random.nextFloat() * 50).toInt()
                android.graphics.Color.rgb(gray, gray, gray)
            }
            return SstvScanLine(
                lineNumber = lineNumber,
                pixels = pixels,
                signalQuality = 0f,
                isSynced = false
            )
        }
    }
}

/**
 * 解码策略接口 (策略模式核心)
 * 
 * 定义 SSTV 行解码的核心方法。
 * 所有 SSTV 模式策略必须实现此接口。
 */
interface DecodingStrategy {
    /**
     * 解码一行音频数据为像素数组
     * 
     * 这是策略模式的核心方法。每种 SSTV 模式实现各自的解码逻辑：
     * - Martin/Scottie: RGB 模式，按 G-B-R 顺序采样
     * - Robot 36/72: YUV 模式，需要 YUV→RGB 转换
     * 
     * @param audioData 该行的音频频率数据 (Hz)
     * @return 该行的像素数组 (ARGB 格式)
     */
    fun decodeLine(audioData: FloatArray): IntArray
    
    /**
     * 使用复用缓冲区解码（性能优化版本）
     * 
     * Robot 36 每秒解码约 6-7 行，为避免 GC 压力，
     * 建议使用此方法复用像素缓冲区。
     * 
     * @param audioData 该行的音频频率数据 (Hz)
     * @param outputBuffer 输出缓冲区（必须与图像宽度相同）
     */
    fun decodeLine(audioData: FloatArray, outputBuffer: IntArray)
}

/**
 * SSTV 模式解码策略接口
 * 
 * 定义了不同 SSTV 模式的行解码策略。
 * 每种模式（如 Martin 1、Robot 36）有不同的颜色编码方式。
 * 
 * 使用策略模式，解码器可以在运行时切换不同的解码策略。
 */
interface SstvModeStrategy : DecodingStrategy {
    
    /** 模式名称 */
    val modeName: String
    
    /** VIS 码 */
    val visCode: Int
    
    /** 图像宽度 */
    val width: Int
    
    /** 图像高度 */
    val height: Int
    
    /** 单行扫描时间 (毫秒) */
    val scanLineTimeMs: Double
    
    /** 同步脉冲时长 (毫秒) */
    val syncPulseMs: Double
    
    /** 同步脉冲频率 (Hz) */
    val syncPulseFreq: Int
    
    /** 采样率 (Hz) */
    val sampleRate: Int
        get() = 44100
    
    /**
     * 处理一行的音频数据，返回解码后的像素数组
     * 
     * 这是核心的行解码方法。不同模式实现各自的颜色解码逻辑：
     * - Martin/Scottie: RGB 模式，按 G-B-R 顺序采样
     * - Robot 36: YUV 模式，需要进行 YUV→RGB 转换
     * 
     * @param audioBuffer 该行的音频频率数据 (已转换为频率值 Hz)
     * @param sampleRate 采样率 (Hz)
     * @return 该行的像素数组 (ARGB 格式)
     */
    fun processLine(audioBuffer: FloatArray, sampleRate: Int): IntArray
    
    /**
     * 使用复用缓冲区处理行数据（性能优化版本）
     * 
     * @param audioBuffer 该行的音频频率数据
     * @param sampleRate 采样率
     * @param outputBuffer 输出缓冲区
     */
    fun processLine(audioBuffer: FloatArray, sampleRate: Int, outputBuffer: IntArray) {
        val result = processLine(audioBuffer, sampleRate)
        System.arraycopy(result, 0, outputBuffer, 0, minOf(result.size, outputBuffer.size))
    }
    
    // DecodingStrategy 接口实现
    override fun decodeLine(audioData: FloatArray): IntArray {
        return processLine(audioData, sampleRate)
    }
    
    override fun decodeLine(audioData: FloatArray, outputBuffer: IntArray) {
        processLine(audioData, sampleRate, outputBuffer)
    }
    
    /**
     * 获取该模式需要的每行采样数
     * 
     * @param sampleRate 采样率 (Hz)
     * @return 每行所需的采样点数
     */
    fun getSamplesPerLine(sampleRate: Int): Int {
        return (scanLineTimeMs * sampleRate / 1000.0).toInt()
    }
    
    /**
     * 获取同步脉冲的采样数
     * 
     * @param sampleRate 采样率 (Hz)
     * @return 同步脉冲采样点数
     */
    fun getSyncPulseSamples(sampleRate: Int): Int {
        return (syncPulseMs * sampleRate / 1000.0).toInt()
    }
    
    /**
     * 将频率转换为亮度值 (0.0 - 1.0)
     * 
     * SSTV 标准：
     * - 1500 Hz = 黑色 (0.0)
     * - 2300 Hz = 白色 (1.0)
     * 
     * @param frequency 频率 (Hz)
     * @return 亮度值 (0.0 - 1.0)
     */
    fun frequencyToLuminance(frequency: Float): Float {
        return ((frequency - SstvConstants.BLACK_FREQ) / SstvConstants.FREQ_RANGE).coerceIn(0f, 1f)
    }
    
    /**
     * 获取每像素的采样数
     * 
     * @param colorDurationMs 颜色通道扫描时长 (毫秒)
     * @param sampleRate 采样率 (Hz)
     * @return 每像素采样数
     */
    fun getSamplesPerPixel(colorDurationMs: Double, sampleRate: Int): Double {
        return (colorDurationMs * sampleRate / 1000.0) / width
    }
}

/**
 * RGB 模式策略的抽象基类
 * 
 * Martin 和 Scottie 系列都使用 RGB 颜色模式，
 * 只是颜色通道的顺序和时序略有不同。
 * 
 * 性能优化：
 * - 使用内部缓冲区复用，避免每行都分配新数组
 * - 预计算采样参数
 */
abstract class RgbModeStrategy : SstvModeStrategy {
    
    /**
     * 颜色通道配置
     * 
     * @param channel 颜色通道类型
     * @param startMs 该通道在行内的起始时间 (毫秒)
     * @param durationMs 该通道的扫描时长 (毫秒)
     */
    data class ChannelConfig(
        val channel: ColorChannel,
        val startMs: Double,
        val durationMs: Double
    )
    
    /** 颜色通道配置列表（按时间顺序） */
    abstract val channelConfigs: List<ChannelConfig>
    
    // ========== 缓冲区复用（性能优化）==========
    
    /** 红色通道缓冲区 */
    private var redBuffer: IntArray? = null
    
    /** 绿色通道缓冲区 */
    private var greenBuffer: IntArray? = null
    
    /** 蓝色通道缓冲区 */
    private var blueBuffer: IntArray? = null
    
    /**
     * 获取或创建缓冲区
     */
    private fun ensureBuffers() {
        if (redBuffer == null || redBuffer!!.size != width) {
            redBuffer = IntArray(width)
            greenBuffer = IntArray(width)
            blueBuffer = IntArray(width)
        }
    }
    
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int): IntArray {
        val pixels = IntArray(width)
        processLine(audioBuffer, sampleRate, pixels)
        return pixels
    }
    
    override fun processLine(audioBuffer: FloatArray, sampleRate: Int, outputBuffer: IntArray) {
        ensureBuffers()
        
        val samplesPerMs = sampleRate / 1000.0
        val red = redBuffer!!
        val green = greenBuffer!!
        val blue = blueBuffer!!
        
        // 清空缓冲区
        red.fill(0)
        green.fill(0)
        blue.fill(0)
        
        // 处理每个颜色通道
        for (config in channelConfigs) {
            val startSample = (config.startMs * samplesPerMs).toInt()
            val samplesPerPixel = (config.durationMs * samplesPerMs) / width
            
            val buffer = when (config.channel) {
                ColorChannel.RED -> red
                ColorChannel.GREEN -> green
                ColorChannel.BLUE -> blue
                // RGB 策略不处理 YUV 通道，跳过
                ColorChannel.Y, ColorChannel.U, ColorChannel.V -> continue
            }
            
            // 对每个像素进行采样
            for (x in 0 until width) {
                val sampleStart = startSample + (x * samplesPerPixel).toInt()
                val sampleEnd = startSample + ((x + 1) * samplesPerPixel).toInt()
                
                // 平均该像素范围内的频率值
                var freqSum = 0f
                var count = 0
                for (s in sampleStart until minOf(sampleEnd, audioBuffer.size)) {
                    if (s >= 0 && s < audioBuffer.size) {
                        freqSum += audioBuffer[s]
                        count++
                    }
                }
                
                if (count > 0) {
                    val avgFreq = freqSum / count
                    val luminance = frequencyToLuminance(avgFreq)
                    buffer[x] = (luminance * 255).toInt().coerceIn(0, 255)
                }
            }
        }
        
        // 合并 RGB 到 ARGB 像素
        for (x in 0 until width) {
            outputBuffer[x] = android.graphics.Color.rgb(red[x], green[x], blue[x])
        }
    }
}

/**
 * YUV 模式策略的抽象基类
 * 
 * Robot 系列使用 YUV 颜色模式：
 * - Y: 亮度 (Luminance)
 * - U (Cb): 蓝色色度 (B-Y)
 * - V (Cr): 红色色度 (R-Y)
 * 
 * 需要进行 YUV → RGB 数学转换。
 * 
 * ## 关于 YUV 归一化
 * 
 * SSTV 的频率到 YUV 映射：
 * - 1500 Hz → Y=0 (黑色), U/V=0 (对应 -128 偏移后)
 * - 2300 Hz → Y=255 (白色), U/V=255 (对应 +127 偏移后)
 * - 1900 Hz → Y=128 (中灰), U/V=128 (无色差)
 * 
 * 对于色度通道 U/V：
 * - 1900 Hz 是中性色（无色差）
 * - 低于 1900 Hz 表示负色差
 * - 高于 1900 Hz 表示正色差
 * 
 * ## 性能优化
 * 
 * 使用内部缓冲区复用，避免每行分配新数组。
 * Robot 36 每秒约 6-7 行，缓冲区复用对性能至关重要。
 */
abstract class YuvModeStrategy : SstvModeStrategy {
    
    /**
     * YUV 通道配置
     * 
     * @param type 通道类型 (Y, U, V)
     * @param startMs 该通道在行内的起始时间 (毫秒)
     * @param durationMs 该通道的扫描时长 (毫秒)
     */
    data class YuvChannelConfig(
        val type: YuvChannel,
        val startMs: Double,
        val durationMs: Double
    )
    
    /**
     * YUV 通道类型
     */
    enum class YuvChannel {
        Y,   // 亮度
        U,   // 蓝色色度 (Cb, B-Y)
        V    // 红色色度 (Cr, R-Y)
    }
    
    /** YUV 通道配置列表（按时间顺序） */
    abstract val yuvChannelConfigs: List<YuvChannelConfig>
    
    // ========== 缓冲区复用（性能优化）==========
    
    /** Y 通道缓冲区 */
    private var yBuffer: IntArray? = null
    
    /** U 通道缓冲区 */
    private var uBuffer: IntArray? = null
    
    /** V 通道缓冲区 */
    private var vBuffer: IntArray? = null
    
    /**
     * 确保缓冲区已初始化
     */
    private fun ensureBuffers() {
        if (yBuffer == null || yBuffer!!.size != width) {
            yBuffer = IntArray(width)
            uBuffer = IntArray(width)
            vBuffer = IntArray(width)
        }
    }
    
    /**
     * YUV 到 RGB 转换 (SSTV 专用版本)
     * 
     * 使用 SSTV 社区常用的转换公式：
     * R = Y + 1.14 × (V - 128)
     * G = Y - 0.39 × (U - 128) - 0.58 × (V - 128)
     * B = Y + 2.03 × (U - 128)
     * 
     * 注意：这些系数与 ITU-R BT.601 略有不同，
     * 是针对 SSTV 传输特性优化的版本。
     * 
     * @param y 亮度值 (0-255)
     * @param u 蓝色色度 Cb/U (0-255, 128 为中性)
     * @param v 红色色度 Cr/V (0-255, 128 为中性)
     * @return ARGB 像素值
     */
    protected fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        // SSTV 优化的 YUV→RGB 转换公式
        val yf = y.toFloat()
        val uf = (u - 128).toFloat()  // 中性点偏移：128 → 0
        val vf = (v - 128).toFloat()
        
        // R = Y + 1.14 × (R-Y)
        val r = (yf + 1.14f * vf).toInt().coerceIn(0, 255)
        
        // G = Y - 0.39 × (B-Y) - 0.58 × (R-Y)
        val g = (yf - 0.39f * uf - 0.58f * vf).toInt().coerceIn(0, 255)
        
        // B = Y + 2.03 × (B-Y)
        val b = (yf + 2.03f * uf).toInt().coerceIn(0, 255)
        
        return android.graphics.Color.rgb(r, g, b)
    }
    
    /**
     * YUV 到 RGB 转换 (ITU-R BT.601 标准版本)
     * 
     * 如果 SSTV 公式颜色不正确，可以尝试此版本。
     * 
     * 公式：
     * R = Y + 1.402 × (V - 128)
     * G = Y - 0.344136 × (U - 128) - 0.714136 × (V - 128)
     * B = Y + 1.772 × (U - 128)
     * 
     * @param y 亮度值 (0-255)
     * @param u 蓝色色度 Cb/U (0-255)
     * @param v 红色色度 Cr/V (0-255)
     * @return ARGB 像素值
     */
    protected fun yuvToRgbBt601(y: Int, u: Int, v: Int): Int {
        val yf = y.toFloat()
        val uf = (u - 128).toFloat()
        val vf = (v - 128).toFloat()
        
        val r = (yf + 1.402f * vf).toInt().coerceIn(0, 255)
        val g = (yf - 0.344136f * uf - 0.714136f * vf).toInt().coerceIn(0, 255)
        val b = (yf + 1.772f * uf).toInt().coerceIn(0, 255)
        
        return android.graphics.Color.rgb(r, g, b)
    }
    
    /**
     * 从音频缓冲区采样指定通道的数据
     * 
     * @param audioBuffer 音频频率数据
     * @param config 通道配置
     * @param sampleRate 采样率
     * @param targetWidth 目标宽度 (可能与图像宽度不同，用于色度下采样)
     * @return 该通道的值数组 (0-255)
     */
    protected fun sampleChannel(
        audioBuffer: FloatArray,
        config: YuvChannelConfig,
        sampleRate: Int,
        targetWidth: Int
    ): IntArray {
        val values = IntArray(targetWidth)
        sampleChannelInto(audioBuffer, config, sampleRate, targetWidth, values)
        return values
    }
    
    /**
     * 从音频缓冲区采样到指定缓冲区（缓冲区复用版本）
     * 
     * @param audioBuffer 音频频率数据
     * @param config 通道配置
     * @param sampleRate 采样率
     * @param targetWidth 目标宽度
     * @param outputBuffer 输出缓冲区
     */
    protected fun sampleChannelInto(
        audioBuffer: FloatArray,
        config: YuvChannelConfig,
        sampleRate: Int,
        targetWidth: Int,
        outputBuffer: IntArray
    ) {
        val samplesPerMs = sampleRate / 1000.0
        val startSample = (config.startMs * samplesPerMs).toInt()
        val samplesPerPixel = (config.durationMs * samplesPerMs) / targetWidth
        
        for (x in 0 until targetWidth) {
            val sampleStart = startSample + (x * samplesPerPixel).toInt()
            val sampleEnd = startSample + ((x + 1) * samplesPerPixel).toInt()
            
            var freqSum = 0f
            var count = 0
            for (s in sampleStart until minOf(sampleEnd, audioBuffer.size)) {
                if (s >= 0 && s < audioBuffer.size) {
                    freqSum += audioBuffer[s]
                    count++
                }
            }
            
            if (count > 0) {
                val avgFreq = freqSum / count
                // 频率 → 归一化值 (0.0-1.0) → 0-255
                val normalized = frequencyToLuminance(avgFreq)
                outputBuffer[x] = (normalized * 255).toInt().coerceIn(0, 255)
            } else {
                // 无有效样本时，使用中性值
                // 对于 Y: 0 (黑色)
                // 对于 U/V: 128 (无色差)
                outputBuffer[x] = if (config.type == YuvChannel.Y) 0 else 128
            }
        }
    }
    
    /**
     * 使用缓冲区复用的行处理（推荐使用）
     */
    protected fun processLineWithBuffers(
        audioBuffer: FloatArray,
        sampleRate: Int,
        outputBuffer: IntArray
    ) {
        ensureBuffers()
        
        val y = yBuffer!!
        val u = uBuffer!!
        val v = vBuffer!!
        
        // 采样各个通道
        for (config in yuvChannelConfigs) {
            val buffer = when (config.type) {
                YuvChannel.Y -> y
                YuvChannel.U -> u
                YuvChannel.V -> v
            }
            sampleChannelInto(audioBuffer, config, sampleRate, width, buffer)
        }
        
        // YUV → RGB 转换
        for (x in 0 until width) {
            outputBuffer[x] = yuvToRgb(y[x], u[x], v[x])
        }
    }
}
