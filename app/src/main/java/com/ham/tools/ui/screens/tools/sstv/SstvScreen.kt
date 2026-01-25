package com.ham.tools.ui.screens.tools.sstv

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * SSTV Receiver Screen - 60FPS 实时画布刷新版本
 * 
 * ## 核心特性
 * 
 * 1. **60FPS 实时刷新**: 使用 frameCounter 触发器强制 Canvas 重绘
 * 2. **自由运行扫描线**: 扫描线以固定速度移动，即使是噪音也会显示雪花点
 * 3. **流式数据收集**: 直接收集 SstvFlowDecoder 的 scanLineFlow
 * 4. **高效 Bitmap 更新**: 使用 setPixels() 直接写入 Bitmap 的对应行
 * 
 * ## 架构
 * 
 * ```
 * SstvFlowDecoder.scanLineFlow
 *         │
 *         ▼ LaunchedEffect 收集
 * ┌─────────────────────────────────────────┐
 * │ bitmap.setPixels(pixels, ..., lineY, 1)│
 * │ frameCounter.longValue++                │
 * └─────────────────────────────────────────┘
 *         │
 *         ▼ Compose 检测到 frameCounter 变化
 * ┌─────────────────────────────────────────┐
 * │ Canvas { drawImage(bitmap) }            │
 * │        ↳ 60FPS 强制重绘                 │
 * └─────────────────────────────────────────┘
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SstvScreen(
    onNavigateBack: () -> Unit,
    viewModel: SstvFlowViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val autoModeDetection by viewModel.autoModeDetection.collectAsState()
    val completedBitmap by viewModel.completedBitmap.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 在 Composable 初始化时检查权限状态
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(hasPermission)
    }
    
    // ==================== 状态管理 ====================
    
    // 1. Bitmap - 在 remember 中创建，随策略变化重建
    val bitmap = remember(selectedStrategy.width, selectedStrategy.height) {
        Bitmap.createBitmap(
            selectedStrategy.width,
            selectedStrategy.height,
            Bitmap.Config.ARGB_8888
        ).also {
            // 初始化为黑色
            it.eraseColor(AndroidColor.BLACK)
        }
    }
    
    // 2. 强制刷新触发器 - 核心！每次更新 Bitmap 后递增，触发 Canvas 重绘
    val frameCounter = remember { mutableLongStateOf(0L) }
    
    // 3. 当前扫描行号
    var currentScanLine by remember { mutableStateOf(0) }
    
    // ==================== 数据收集 (Collector) ====================
    
    // 收集 SstvFlowDecoder 的 scanLineFlow
    LaunchedEffect(Unit) {
        viewModel.scanLineFlow.collect { scanLine ->
            // 将像素写入 Bitmap 的对应行
            if (scanLine.lineNumber >= 0 && scanLine.lineNumber < bitmap.height) {
                bitmap.setPixels(
                    scanLine.pixels,           // 像素数组 (ARGB)
                    0,                          // offset
                    bitmap.width,               // stride
                    0,                          // x
                    scanLine.lineNumber,        // y (当前行)
                    bitmap.width,               // width
                    1                           // height (单行)
                )
                
                // 更新当前扫描行
                currentScanLine = scanLine.lineNumber
                
                // 关键操作：递增 frameCounter 触发 Canvas 重绘
                frameCounter.longValue++
            }
        }
    }
    
    // ==================== 权限管理 ====================
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
    }
    
    // 生命周期管理 - 页面不可见时停止录音
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopRecording()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ==================== UI ====================

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSTV 接收器") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isRecording) {
                        IconButton(onClick = { viewModel.resetDecoder() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重置")
                        }
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 状态提示栏
            FreeRunningStatusBanner(
                isRecording = uiState.isRecording,
                decoderState = uiState.decoderState,
                selectedStrategy = selectedStrategy,
                currentLine = uiState.decodedLines,
                totalLines = uiState.totalLines,
                isSynced = uiState.isSynced,
                signalStrength = uiState.signalStrength,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 模式选择器
            StrategySelector(
                selectedStrategy = selectedStrategy,
                availableStrategies = viewModel.availableStrategies,
                autoModeDetection = autoModeDetection,
                isRecording = uiState.isRecording,
                onStrategySelected = { viewModel.setStrategy(it) },
                onAutoModeChanged = { viewModel.setAutoModeDetection(it) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 调谐指示器
            TuningIndicator(
                frequency = uiState.currentFrequency,
                isActive = uiState.isRecording,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 权限状态
            if (!uiState.hasPermission) {
                PermissionStatusCard(
                    hasPermission = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 解码状态和进度
            FreeRunningDecoderStatusCard(
                isRecording = uiState.isRecording,
                decoderState = uiState.decoderState,
                decodedLines = uiState.decodedLines,
                totalLines = uiState.totalLines,
                modeName = selectedStrategy.modeName,
                debugInfo = debugInfo,
                isSynced = uiState.isSynced,
                signalStrength = uiState.signalStrength,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 图像输出标题
            Text(
                text = "实时预览 (${selectedStrategy.modeName}: ${selectedStrategy.width}×${selectedStrategy.height})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ==================== 核心: 60FPS 实时画布 ====================
            RealTimeCanvasOutput(
                bitmap = bitmap,
                frameCounter = frameCounter.longValue,
                currentScanLine = currentScanLine,
                totalLines = selectedStrategy.height,
                isRecording = uiState.isRecording,
                decoderState = uiState.decoderState,
                isSynced = uiState.isSynced,
                signalStrength = uiState.signalStrength,
                aspectRatio = selectedStrategy.width.toFloat() / selectedStrategy.height.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 最近完成图像（参考常见 SSTV 解码器的“RX 图像/历史”显示）
            Text(
                text = "最近完成",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            val completedAspectRatio = completedBitmap?.let {
                it.width.toFloat() / it.height.toFloat()
            } ?: (selectedStrategy.width.toFloat() / selectedStrategy.height.toFloat())
            CompletedImageOutput(
                bitmap = completedBitmap,
                aspectRatio = completedAspectRatio,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 频率显示
            FrequencyDisplayCompact(
                isRecording = uiState.isRecording,
                frequency = uiState.currentFrequency,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 控制按钮
            ControlButtons(
                hasPermission = uiState.hasPermission,
                isRecording = uiState.isRecording,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onToggleRecording = { viewModel.toggleRecording() },
                onReset = { viewModel.resetDecoder() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "自由运行模式: 扫描线持续移动，即使是噪音也会显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 60FPS 实时画布输出组件
 * 
 * 核心渲染组件，使用 frameCounter 触发强制重绘。
 * 
 * ## 自由运行模式
 * 
 * 扫描线永远以固定速度移动，即使没有有效信号也会显示雪花屏。
 * 红色扫描线指示当前解码位置。
 */
@Composable
private fun RealTimeCanvasOutput(
    bitmap: Bitmap,
    frameCounter: Long,
    currentScanLine: Int,
    totalLines: Int,
    isRecording: Boolean,
    decoderState: FreeRunningSstvDecoder.RunState,
    isSynced: Boolean,
    signalStrength: Float,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    // 扫描线发光动画
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanLineGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanGlow"
    )
    
    // 扫描线颜色：同步时为绿色，未同步时为红色
    val scanLineColor = if (isSynced) Color(0xFF00FF88) else Color.Red
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0A1A)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center
        ) {
            // ==================== 核心 Canvas ====================
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // 关键：读取 frameCounter，Compose 会在其变化时重绘此 Canvas
                @Suppress("UNUSED_VARIABLE")
                val trigger = frameCounter
                
                // 绘制背景扫描线效果（CRT 风格）
                for (y in 0 until canvasHeight.toInt() step 4) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.02f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(canvasWidth, y.toFloat()),
                        strokeWidth = 1f
                    )
                }
                
                // 绘制 Bitmap
                val imageBitmap = bitmap.asImageBitmap()
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                    filterQuality = FilterQuality.None  // 最近邻插值，保持像素锐利
                )
                
                // 绘制复古扫描线叠加
                for (y in 0 until canvasHeight.toInt() step 3) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.10f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(canvasWidth, y.toFloat()),
                        strokeWidth = 1f
                    )
                }
                
                // ==================== 绘制扫描线（永远显示！）====================
                // 关键：只要在录音状态，扫描线就要显示
                // 即使是噪音/雪花屏，红线也要持续移动
                if (isRecording && currentScanLine >= 0 && currentScanLine < totalLines) {
                    val scanY = (currentScanLine.toFloat() / totalLines) * canvasHeight
                    
                    // 发光效果（渐变光晕）
                    for (i in 1..4) {
                        drawLine(
                            color = scanLineColor.copy(alpha = 0.25f * scanLineGlow / i),
                            start = Offset(0f, scanY - i * 2),
                            end = Offset(canvasWidth, scanY - i * 2),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = scanLineColor.copy(alpha = 0.25f * scanLineGlow / i),
                            start = Offset(0f, scanY + i * 2),
                            end = Offset(canvasWidth, scanY + i * 2),
                            strokeWidth = 2f
                        )
                    }
                    
                    // 主扫描线
                    drawLine(
                        color = scanLineColor.copy(alpha = scanLineGlow),
                        start = Offset(0f, scanY),
                        end = Offset(canvasWidth, scanY),
                        strokeWidth = 2f
                    )
                }
                
                // 帧完成时绘制绿色边框
                if (decoderState == FreeRunningSstvDecoder.RunState.FRAME_COMPLETE) {
                    drawRect(
                        color = Color(0xFF00FF88),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                
                // 同步状态指示器（右上角）
                if (isRecording) {
                    val indicatorColor = when {
                        isSynced -> Color(0xFF00FF88)
                        signalStrength > 0.3f -> Color(0xFFFFAA00)
                        else -> Color(0xFF666666)
                    }
                    drawCircle(
                        color = indicatorColor,
                        radius = 6.dp.toPx(),
                        center = Offset(canvasWidth - 16.dp.toPx(), 16.dp.toPx())
                    )
                }
            }
            
            // 占位符文本（未开始录音时显示）
            if (!isRecording && decoderState == FreeRunningSstvDecoder.RunState.STOPPED) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📡",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击开始接收",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "自由运行模式：即使无信号也会显示雪花",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedImageOutput(
    bitmap: Bitmap?,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0A1A)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text(
                    text = "等待完整图像...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }
    }
}

/**
 * 自由运行状态横幅
 */
@Composable
private fun FreeRunningStatusBanner(
    isRecording: Boolean,
    decoderState: FreeRunningSstvDecoder.RunState,
    selectedStrategy: SstvModeStrategy,
    currentLine: Int,
    totalLines: Int,
    isSynced: Boolean,
    signalStrength: Float,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isRecording -> Color(0xFF2A2A3E)
            isSynced -> Color(0xFF1A4A2E)  // 绿色 - 已同步
            decoderState == FreeRunningSstvDecoder.RunState.RUNNING && signalStrength > 0.3f -> Color(0xFF4A3A1A)  // 黄色 - 有信号
            decoderState == FreeRunningSstvDecoder.RunState.FRAME_COMPLETE -> Color(0xFF00AA44)
            else -> Color(0xFF2A3A4E)  // 蓝色 - 运行中但无同步
        },
        label = "statusBgColor"
    )
    
    val syncIcon = if (isSynced) "✓" else "○"
    val signalPercent = (signalStrength * 100).toInt()
    
    val statusText = when {
        !isRecording -> "等待启动..."
        decoderState == FreeRunningSstvDecoder.RunState.STOPPED -> "已停止"
        decoderState == FreeRunningSstvDecoder.RunState.RUNNING || 
        decoderState == FreeRunningSstvDecoder.RunState.SYNCED -> {
            "$syncIcon ${selectedStrategy.modeName} | Line ${currentLine + 1}/$totalLines | Signal $signalPercent%"
        }
        decoderState == FreeRunningSstvDecoder.RunState.FRAME_COMPLETE -> {
            "✓ 帧完成: ${selectedStrategy.modeName}"
        }
        else -> "自由运行中..."
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isRecording && decoderState != FreeRunningSstvDecoder.RunState.STOPPED) {
                ScanningIndicator()
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 扫描指示器动画
 */
@Composable
private fun ScanningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanAlpha"
    )
    
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFF00FF88).copy(alpha = alpha))
    )
}

/**
 * 策略选择器
 */
@Composable
private fun StrategySelector(
    selectedStrategy: SstvModeStrategy,
    availableStrategies: List<SstvModeStrategy>,
    autoModeDetection: Boolean,
    isRecording: Boolean,
    onStrategySelected: (SstvModeStrategy) -> Unit,
    onAutoModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 自动检测开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "自动模式检测",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (autoModeDetection) "通过 VIS 码自动识别" else "手动选择模式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = autoModeDetection,
                    onCheckedChange = { onAutoModeChanged(it) },
                    enabled = !isRecording
                )
            }
            
            // 手动模式选择
            if (!autoModeDetection) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(enabled = !isRecording) { expanded = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedStrategy.modeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "VIS: ${selectedStrategy.visCode} | ${selectedStrategy.width}×${selectedStrategy.height} | ${String.format("%.0f", selectedStrategy.scanLineTimeMs)}ms/行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "选择模式",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableStrategies.forEach { strategy ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = strategy.modeName,
                                            fontWeight = if (strategy.visCode == selectedStrategy.visCode) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = "VIS: ${strategy.visCode} | ${strategy.width}×${strategy.height} | ${String.format("%.0f", strategy.scanLineTimeMs)}ms/行",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                },
                                onClick = {
                                    onStrategySelected(strategy)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (strategy.visCode == selectedStrategy.visCode) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 调谐指示器
 */
@Composable
private fun TuningIndicator(
    frequency: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val minFreq = 1100f
    val maxFreq = 2400f
    val freqRange = maxFreq - minFreq
    
    val position by remember(frequency) {
        derivedStateOf {
            if (frequency > minFreq && frequency < maxFreq) {
                (frequency - minFreq) / freqRange
            } else {
                0.5f
            }
        }
    }
    
    val animatedPosition by animateFloatAsState(
        targetValue = if (isActive && frequency > minFreq) position else 0.5f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tuningPosition"
    )
    
    val indicatorColor by animateColorAsState(
        targetValue = when {
            !isActive || frequency < minFreq -> Color.Gray
            frequency in 1100f..1300f -> Color(0xFF00FF88)
            frequency in 1400f..1600f -> Color(0xFF666666)
            frequency in 1800f..2000f -> Color(0xFFFFAA00)
            frequency in 2100f..2400f -> Color(0xFFFFFFFF)
            else -> Color(0xFF00AAFF)
        },
        label = "indicatorColor"
    )
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调谐指示器",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = if (isActive && frequency > minFreq) "${frequency.toInt()} Hz" else "- Hz",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = indicatorColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    val barHeight = size.height
                    val barWidth = size.width
                    
                    // 同步区 (1100-1300)
                    val syncEnd = (1300f - minFreq) / freqRange * barWidth
                    drawRect(
                        color = Color(0xFF00FF88).copy(alpha = 0.3f),
                        topLeft = Offset(0f, 0f),
                        size = Size(syncEnd, barHeight)
                    )
                    
                    // 黑色区 (1400-1600)
                    val blackStart = (1400f - minFreq) / freqRange * barWidth
                    val blackEnd = (1600f - minFreq) / freqRange * barWidth
                    drawRect(
                        color = Color(0xFF444444).copy(alpha = 0.5f),
                        topLeft = Offset(blackStart, 0f),
                        size = Size(blackEnd - blackStart, barHeight)
                    )
                    
                    // VIS 区 (1800-2000)
                    val visStart = (1800f - minFreq) / freqRange * barWidth
                    val visEnd = (2000f - minFreq) / freqRange * barWidth
                    drawRect(
                        color = Color(0xFFFFAA00).copy(alpha = 0.3f),
                        topLeft = Offset(visStart, 0f),
                        size = Size(visEnd - visStart, barHeight)
                    )
                    
                    // 白色区 (2100-2400)
                    val whiteStart = (2100f - minFreq) / freqRange * barWidth
                    drawRect(
                        color = Color(0xFFFFFFFF).copy(alpha = 0.2f),
                        topLeft = Offset(whiteStart, 0f),
                        size = Size(barWidth - whiteStart, barHeight)
                    )
                    
                    // 刻度线
                    listOf(1200, 1500, 1900, 2300).forEach { freq ->
                        val x = (freq - minFreq) / freqRange * barWidth
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, barHeight),
                            strokeWidth = 1f
                        )
                    }
                    
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.3f),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPosition)
                            .align(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(4.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(indicatorColor)
                                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FrequencyTickLabel("1200", "同步", Color(0xFF00FF88))
                FrequencyTickLabel("1500", "黑", Color(0xFF888888))
                FrequencyTickLabel("1900", "VIS", Color(0xFFFFAA00))
                FrequencyTickLabel("2300", "白", Color(0xFFFFFFFF))
            }
        }
    }
}

@Composable
private fun FrequencyTickLabel(frequency: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = frequency,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
            color = color.copy(alpha = 0.8f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun FrequencyDisplayCompact(
    isRecording: Boolean,
    frequency: Float,
    modifier: Modifier = Modifier
) {
    val signalType = when {
        frequency < 100f -> "无信号"
        frequency in 1100f..1300f -> "同步"
        frequency in 1400f..1600f -> "黑色"
        frequency in 1800f..2000f -> "VIS"
        frequency in 2200f..2400f -> "白色"
        frequency in 1000f..2500f -> "数据"
        else -> "噪音"
    }
    
    val signalColor = when {
        frequency < 100f -> Color.Gray
        frequency in 1100f..1300f -> Color(0xFF00FF88)
        frequency in 1400f..1600f -> Color(0xFF888888)
        frequency in 1800f..2000f -> Color(0xFFFFAA00)
        frequency in 2200f..2400f -> Color(0xFFFFFFFF)
        frequency in 1000f..2500f -> Color(0xFF00AAFF)
        else -> Color(0xFF444444)
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(signalColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = signalType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isRecording && frequency > 100) "${frequency.toInt()} Hz" else "- Hz",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = if (isRecording && frequency > 100) signalColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ControlButtons(
    hasPermission: Boolean,
    isRecording: Boolean,
    onRequestPermission: () -> Unit,
    onToggleRecording: () -> Unit,
    onReset: () -> Unit
) {
    if (!hasPermission) {
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("授权麦克风权限") }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onToggleRecording,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isRecording) {
                    RecordingIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = if (isRecording) "停止接收" else "开始接收", fontWeight = FontWeight.SemiBold)
            }
            if (isRecording) {
                OutlinedButton(onClick = onReset, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(hasPermission: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasPermission) Icons.Default.Check else Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (hasPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasPermission) "麦克风已授权" else "需要麦克风权限",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (hasPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun FreeRunningDecoderStatusCard(
    isRecording: Boolean,
    decoderState: FreeRunningSstvDecoder.RunState,
    decodedLines: Int,
    totalLines: Int,
    modeName: String,
    debugInfo: String,
    isSynced: Boolean,
    signalStrength: Float,
    modifier: Modifier = Modifier
) {
    val stateColor by animateColorAsState(
        targetValue = when {
            isSynced -> Color(0xFF00FF88)  // 绿色 - 已同步
            decoderState == FreeRunningSstvDecoder.RunState.RUNNING && signalStrength > 0.3f -> Color(0xFFFFAA00)  // 黄色 - 有信号
            decoderState == FreeRunningSstvDecoder.RunState.RUNNING -> Color(0xFF00AAFF)  // 蓝色 - 运行中
            decoderState == FreeRunningSstvDecoder.RunState.FRAME_COMPLETE -> Color(0xFF00FF88)
            else -> Color.Gray
        },
        label = "stateColor"
    )
    
    val stateDisplayName = when (decoderState) {
        FreeRunningSstvDecoder.RunState.STOPPED -> "已停止"
        FreeRunningSstvDecoder.RunState.RUNNING -> if (isSynced) "已同步" else "自由运行"
        FreeRunningSstvDecoder.RunState.SYNCED -> "已同步"
        FreeRunningSstvDecoder.RunState.FRAME_COMPLETE -> "帧完成"
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRecording && decoderState != FreeRunningSstvDecoder.RunState.STOPPED) {
                        PulsingDot(color = stateColor)
                    } else {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isRecording) stateDisplayName else "未启动",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = modeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${decodedLines + 1} / $totalLines",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isRecording) {
                        Text(
                            text = "Signal: ${(signalStrength * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = stateColor
                        )
                    }
                }
            }
            
            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (decodedLines + 1).toFloat() / totalLines },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = stateColor,
                )
            }
            
            if (debugInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = debugInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(modifier = Modifier.size((10 * scale).dp).clip(CircleShape).background(color))
}

@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "recordingAlpha"
    )
    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
}
