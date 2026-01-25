package com.ham.tools.ui.screens.tools.cw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ham.tools.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Represents a morse code element (dot or dash)
 */
data class MorseElement(
    val isDash: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 莫斯电码到字母的映射表
 */
private val morseToChar = mapOf(
    ".-" to 'A',
    "-..." to 'B',
    "-.-." to 'C',
    "-.." to 'D',
    "." to 'E',
    "..-." to 'F',
    "--." to 'G',
    "...." to 'H',
    ".." to 'I',
    ".---" to 'J',
    "-.-" to 'K',
    ".-.." to 'L',
    "--" to 'M',
    "-." to 'N',
    "---" to 'O',
    ".--." to 'P',
    "--.-" to 'Q',
    ".-." to 'R',
    "..." to 'S',
    "-" to 'T',
    "..-" to 'U',
    "...-" to 'V',
    ".--" to 'W',
    "-..-" to 'X',
    "-.--" to 'Y',
    "--.." to 'Z',
    "-----" to '0',
    ".----" to '1',
    "..---" to '2',
    "...--" to '3',
    "....-" to '4',
    "....." to '5',
    "-...." to '6',
    "--..." to '7',
    "---.." to '8',
    "----." to '9'
)

/**
 * 将莫斯元素列表转换为字符串
 */
private fun elementsToMorseString(elements: List<MorseElement>): String {
    return elements.joinToString("") { if (it.isDash) "-" else "." }
}

/**
 * 解码莫斯电码序列，返回已识别的字母和剩余的元素
 * 返回: List<Pair<Char, Int>> - 字母和对应的元素数量
 */
private fun decodeMorseSequence(elements: List<MorseElement>): List<Pair<Char, Int>> {
    val result = mutableListOf<Pair<Char, Int>>()
    var currentMorse = ""
    var currentCount = 0
    var lastTimestamp = 0L
    
    for ((index, element) in elements.withIndex()) {
        // 检查是否有字符间隔（超过600ms表示新字母）
        if (lastTimestamp > 0 && element.timestamp - lastTimestamp > 600) {
            // 尝试解码当前积累的莫斯码
            if (currentMorse.isNotEmpty()) {
                val char = morseToChar[currentMorse] ?: '?'
                result.add(Pair(char, currentCount))
                currentMorse = ""
                currentCount = 0
            }
        }
        
        currentMorse += if (element.isDash) "-" else "."
        currentCount++
        lastTimestamp = element.timestamp + element.durationMs
        
        // 检查是否匹配任何字母
        val matchedChar = morseToChar[currentMorse]
        if (matchedChar != null) {
            // 检查是否是最后一个元素，或者下一个元素间隔足够长
            val isLast = index == elements.size - 1
            val nextHasGap = if (index < elements.size - 1) {
                elements[index + 1].timestamp - (element.timestamp + element.durationMs) > 300
            } else false
            
            if (isLast || nextHasGap) {
                result.add(Pair(matchedChar, currentCount))
                currentMorse = ""
                currentCount = 0
            }
        }
    }
    
    // 处理剩余的未匹配莫斯码
    if (currentMorse.isNotEmpty()) {
        val char = morseToChar[currentMorse] ?: '?'
        result.add(Pair(char, currentCount))
    }
    
    return result
}

/**
 * CW Practice Screen - Morse code key practice
 * 
 * Features:
 * - Large circular key button
 * - 700Hz tone generation using AudioTrack
 * - Visual timeline showing dots and dashes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CwPracticeScreen(
    onNavigateBack: () -> Unit
) {
    var isKeyPressed by remember { mutableStateOf(false) }
    var keyPressStartTime by remember { mutableLongStateOf(0L) }
    val morseElements = remember { mutableStateListOf<MorseElement>() }
    val listState = rememberLazyListState()
    
    // Audio generator
    val toneGenerator = remember { CwToneGenerator() }
    
    // Cleanup audio on dispose
    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.stop()
            toneGenerator.release()
        }
    }
    
    // Auto-scroll to end when new element added
    LaunchedEffect(morseElements.size) {
        if (morseElements.isNotEmpty()) {
            listState.animateScrollToItem(morseElements.size - 1)
        }
    }
    
    // Key press scale animation
    val keyScale by animateFloatAsState(
        targetValue = if (isKeyPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 50),
        label = "keyScale"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cw_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { morseElements.clear() }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cw_clear))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Morse code timeline / waterfall
            MorseTimeline(
                elements = morseElements,
                listState = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            )
            
            // Decoded text with morse code display
            MorseDecodedDisplay(
                elements = morseElements,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Timing guide
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimingHint(label = stringResource(R.string.cw_dot), duration = stringResource(R.string.cw_dot_duration))
                TimingHint(label = stringResource(R.string.cw_dash), duration = stringResource(R.string.cw_dash_duration))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Giant key button
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(keyScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isKeyPressed) {
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Key pressed
                                isKeyPressed = true
                                keyPressStartTime = System.currentTimeMillis()
                                toneGenerator.start()
                                
                                // Wait for release
                                tryAwaitRelease()
                                
                                // Key released
                                isKeyPressed = false
                                toneGenerator.stop()
                                
                                val duration = System.currentTimeMillis() - keyPressStartTime
                                val isDash = duration >= 200 // 200ms threshold
                                morseElements.add(MorseElement(isDash, duration))
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isKeyPressed) "●" else "○",
                        fontSize = 48.sp,
                        color = if (isKeyPressed) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.cw_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isKeyPressed) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * 解码后的莫斯电码显示组件
 * 显示：字母在上方，摩斯电码在中间，分隔线在下方
 */
@Composable
private fun MorseDecodedDisplay(
    elements: List<MorseElement>,
    modifier: Modifier = Modifier
) {
    val decodedChars = remember(elements.size) {
        if (elements.isEmpty()) emptyList() else decodeMorseSequence(elements)
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (elements.isEmpty()) {
                Text(
                    text = stringResource(R.string.cw_hold_to_practice),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            } else {
                // 解码后的字母显示
                Text(
                    text = stringResource(R.string.cw_decode_result),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 字母行
                Text(
                    text = decodedChars.joinToString("") { it.first.toString() }.ifEmpty { "..." },
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 8.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 摩斯电码与分隔线
                MorseCodeWithUnderlines(
                    elements = elements,
                    decodedChars = decodedChars
                )
            }
        }
    }
}

/**
 * 带有分隔下划线的摩斯电码显示
 */
@Composable
private fun MorseCodeWithUnderlines(
    elements: List<MorseElement>,
    decodedChars: List<Pair<Char, Int>>
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    
    // 计算每个字母对应的元素范围
    val charRanges = mutableListOf<IntRange>()
    var startIndex = 0
    for ((_, count) in decodedChars) {
        charRanges.add(startIndex until (startIndex + count))
        startIndex += count
    }
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val elementWidth = 20.dp.toPx()  // 点的宽度
        val dashWidth = 50.dp.toPx()     // 划的宽度
        val spacing = 8.dp.toPx()        // 元素间距
        val lineHeight = 8.dp.toPx()     // 点划高度
        val underlineY = size.height - 4.dp.toPx()  // 下划线Y位置
        val underlineHeight = 3.dp.toPx()
        val morseY = size.height / 2 - lineHeight / 2  // 摩斯码Y位置
        
        // 计算总宽度并居中
        var totalWidth = 0f
        elements.forEach { element ->
            totalWidth += if (element.isDash) dashWidth else elementWidth
            totalWidth += spacing
        }
        totalWidth -= spacing  // 移除最后一个间距
        
        var startX = (size.width - totalWidth) / 2
        if (startX < 0) startX = 0f
        
        var currentX = startX
        var elementIndex = 0
        
        // 遍历每个字母对应的元素
        for ((charIndex, range) in charRanges.withIndex()) {
            val charStartX = currentX
            
            // 绘制该字母的所有摩斯元素
            for (i in range) {
                if (i >= elements.size) break
                val element = elements[i]
                val width = if (element.isDash) dashWidth else elementWidth
                
                // 绘制点或划
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(currentX, morseY),
                    size = Size(width, lineHeight),
                    cornerRadius = CornerRadius(lineHeight / 2)
                )
                
                currentX += width + spacing
                elementIndex++
            }
            
            val charEndX = currentX - spacing
            
            // 绘制该字母下方的分隔线
            if (charEndX > charStartX) {
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(charStartX, underlineY),
                    size = Size(charEndX - charStartX, underlineHeight),
                    cornerRadius = CornerRadius(underlineHeight / 2)
                )
            }
            
            // 在字母之间添加间隙
            if (charIndex < charRanges.size - 1) {
                currentX += spacing * 2  // 额外间距分隔不同字母
            }
        }
    }
}

/**
 * Timeline visualization of morse elements
 */
@Composable
private fun MorseTimeline(
    elements: List<MorseElement>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (elements.isEmpty()) {
                Text(
                    text = stringResource(R.string.cw_timeline_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(elements) { element ->
                        MorseElementView(element = element)
                    }
                }
            }
        }
    }
}

/**
 * Visual representation of a single morse element
 */
@Composable
private fun MorseElementView(element: MorseElement) {
    val width = if (element.isDash) 48.dp else 16.dp
    val color = if (element.isDash) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    
    Surface(
        modifier = Modifier
            .size(width = width, height = 24.dp),
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {}
}

/**
 * Timing hint label
 */
@Composable
private fun TimingHint(label: String, duration: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * CW Tone Generator using AudioTrack
 * Generates a 700Hz sine wave tone
 */
class CwToneGenerator {
    private val sampleRate = 44100
    private val frequency = 700.0 // Hz
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playThread: Thread? = null
    
    fun start() {
        if (isPlaying) return
        isPlaying = true
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        
        playThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            var phase = 0.0
            val phaseIncrement = 2.0 * PI * frequency / sampleRate
            
            while (isPlaying) {
                for (i in buffer.indices) {
                    buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.8).toInt().toShort()
                    phase += phaseIncrement
                    if (phase >= 2.0 * PI) {
                        phase -= 2.0 * PI
                    }
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
        playThread?.start()
    }
    
    fun stop() {
        isPlaying = false
        playThread?.join(100)
        audioTrack?.stop()
    }
    
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
