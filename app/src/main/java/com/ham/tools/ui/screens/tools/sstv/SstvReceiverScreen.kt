package com.ham.tools.ui.screens.tools.sstv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ham.tools.ui.screens.tools.sstv.decoder.SstvDecoder
import kotlin.math.roundToInt

/**
 * SSTV 接收器屏幕 - Robot36 风格
 * 
 * 核心设计：
 * 1. 大面积瀑布图显示区域（类似 Robot36 的 scopeView）
 * 2. 连续滚动的解码图像
 * 3. 简洁的控制栏
 * 4. 点击开始即自由解码
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SstvReceiverScreen(
    onNavigateBack: () -> Unit,
    viewModel: SstvReceiverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 在 Composable 初始化时检查权限状态
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(hasPermission)
    }
    
    // 瀑布图尺寸
    val waterfallWidth = SstvDecoder.SCOPE_WIDTH
    val waterfallHeight = SstvDecoder.SCOPE_HEIGHT / 2  // 单缓冲显示高度
    
    // 瀑布图位图
    var waterfallBitmap by remember {
        mutableStateOf(
            Bitmap.createBitmap(waterfallWidth, waterfallHeight, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(AndroidColor.BLACK)
            }
        )
    }
    
    // 当前行和重绘触发器
    var currentRow by remember { mutableIntStateOf(0) }
    var frameCounter by remember { mutableLongStateOf(0L) }
    
    // 水平滚动偏移
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
        if (granted && !uiState.isRecording) {
            viewModel.startRecording()
        }
    }
    
    // 收集扫描线并更新瀑布图
    LaunchedEffect(Unit) {
        viewModel.scanLineFlow.collect { scanLine ->
            val bmp = waterfallBitmap
            val lineNum = scanLine.lineNumber % waterfallHeight
            val pixels = scanLine.pixels
            
            // 确保像素数组足够
            val targetPixels = if (pixels.size >= waterfallWidth) {
                pixels
            } else {
                // 扩展像素到瀑布图宽度
                IntArray(waterfallWidth) { i ->
                    val srcIdx = (i * pixels.size) / waterfallWidth
                    pixels[srcIdx.coerceIn(0, pixels.size - 1)]
                }
            }
            
            try {
                bmp.setPixels(targetPixels, 0, waterfallWidth, 0, lineNum, waterfallWidth, 1)
                currentRow = lineNum
                frameCounter++
            } catch (e: Exception) {
                // 忽略越界错误
            }
        }
    }
    
    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopRecording()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            SstvTopBar(
                isRecording = uiState.isRecording,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 状态栏
            StatusBar(
                isRecording = uiState.isRecording,
                currentLine = currentRow,
                signalStrength = uiState.signalStrength,
                isSynced = uiState.isSynced,
                frequency = uiState.currentFrequency
            )
            
            // 瀑布图显示区域 - 占据大部分空间
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            horizontalOffset = (horizontalOffset + dragAmount).coerceIn(-200f, 200f)
                        }
                    }
            ) {
                WaterfallDisplay(
                    bitmap = waterfallBitmap,
                    currentRow = currentRow,
                    waterfallHeight = waterfallHeight,
                    frameCounter = frameCounter,
                    horizontalOffset = horizontalOffset,
                    isRecording = uiState.isRecording
                )
                
                // 频率刻度
                FrequencyScale(
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            
            // 控制栏
            ControlBar(
                hasPermission = uiState.hasPermission,
                isRecording = uiState.isRecording,
                onToggleRecording = {
                    if (!uiState.hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleRecording()
                    }
                }
            )
        }
    }
}

// ==================== UI 组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SstvTopBar(
    isRecording: Boolean,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SSTV Decoder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isRecording) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF161B22)
        )
    )
}

@Composable
private fun StatusBar(
    isRecording: Boolean,
    currentLine: Int,
    signalStrength: Float,
    isSynced: Boolean,
    frequency: Float
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !isRecording -> Color.Gray
            isSynced -> Color(0xFF4CAF50)
            signalStrength > 0.3f -> Color(0xFFFF9800)
            else -> Color(0xFF2196F3)
        },
        label = "statusColor"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：状态指示
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isRecording -> "STANDBY"
                    isSynced -> "SYNCED"
                    signalStrength > 0.3f -> "SIGNAL"
                    else -> "SCANNING"
                },
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 中间：频率显示
        Text(
            text = "${frequency.roundToInt()} Hz",
            color = Color(0xFF58A6FF),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace
        )
        
        // 右侧：行号
        Text(
            text = "Line: $currentLine",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun WaterfallDisplay(
    bitmap: Bitmap,
    currentRow: Int,
    waterfallHeight: Int,
    frameCounter: Long,
    horizontalOffset: Float,
    isRecording: Boolean
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 触发重绘
        @Suppress("UNUSED_VARIABLE")
        val trigger = frameCounter
        
        // 绘制瀑布图
        val imageBitmap = bitmap.asImageBitmap()
        
        // 计算缩放以填充整个画布
        val scaleX = canvasWidth / bitmap.width
        val scaleY = canvasHeight / bitmap.height
        
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(
                horizontalOffset.toInt(),
                0
            ),
            dstSize = IntSize(
                (bitmap.width * scaleX).toInt(),
                (bitmap.height * scaleY).toInt()
            )
        )
        
        // 绘制网格线
        drawGrid(canvasWidth, canvasHeight)
        
        // 当前行指示线
        if (isRecording && currentRow > 0) {
            val lineY = (currentRow.toFloat() / waterfallHeight) * canvasHeight
            drawLine(
                color = Color(0xFF4CAF50),
                start = Offset(0f, lineY),
                end = Offset(canvasWidth, lineY),
                strokeWidth = 2f
            )
        }
    }
}

private fun DrawScope.drawGrid(canvasWidth: Float, canvasHeight: Float) {
    val gridColor = Color.White.copy(alpha = 0.1f)
    
    // 水平线
    val hLines = 8
    for (i in 1 until hLines) {
        val y = canvasHeight * i / hLines
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 1f
        )
    }
    
    // 垂直线
    val vLines = 8
    for (i in 1 until vLines) {
        val x = canvasWidth * i / vLines
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, canvasHeight),
            strokeWidth = 1f
        )
    }
}

@Composable
private fun FrequencyScale(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = "2300",
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(60.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color.Gray,
                            Color.Black
                        )
                    ),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "1500",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ControlBar(
    hasPermission: Boolean,
    isRecording: Boolean,
    onToggleRecording: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 开始/停止按钮
        FilledTonalButton(
            onClick = onToggleRecording,
            modifier = Modifier.height(56.dp)
        ) {
            Icon(
                imageVector = if (!hasPermission) {
                    Icons.Outlined.MicOff
                } else if (isRecording) {
                    Icons.Default.Stop
                } else {
                    Icons.Default.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when {
                    !hasPermission -> "需要麦克风权限"
                    isRecording -> "停止解码"
                    else -> "开始解码"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            // 录音指示灯
            if (isRecording) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
