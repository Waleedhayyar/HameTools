package com.ham.tools.ui.screens.tools.gridlocator

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ham.tools.R
import com.ham.tools.util.GridLocatorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 网格定位主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridLocatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: GridLocatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }
    
    // 每次进入屏幕时重新获取定位
    LaunchedEffect(Unit) {
        // 重置状态以触发重新定位
        viewModel.resetLocationState()
        
        if (viewModel.hasLocationPermission()) {
            viewModel.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    // 清理
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.grid_locator_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 背景装饰 - 世界地图网格
            WorldMapBackground(
                modifier = Modifier.fillMaxSize()
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // 模式切换
                var selectedIndex by remember { mutableIntStateOf(if (uiState.isManualMode) 1 else 0) }
                
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedIndex == 0,
                        onClick = { 
                            selectedIndex = 0
                            if (uiState.isManualMode) viewModel.toggleMode()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.grid_locator_live))
                    }
                    SegmentedButton(
                        selected = selectedIndex == 1,
                        onClick = { 
                            selectedIndex = 1
                            if (!uiState.isManualMode) viewModel.toggleMode()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.grid_locator_manual))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                AnimatedContent(
                    targetState = selectedIndex == 0,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                    },
                    label = "mode_content"
                ) { isLiveMode ->
                    if (isLiveMode) {
                        LiveLocationContent(
                            locationState = uiState.locationState,
                            onRequestPermission = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onRetry = { viewModel.startLocationUpdates() },
                            context = context
                        )
                    } else {
                        ManualCalculatorContent(
                            state = uiState.calculatorState,
                            onLatitudeChange = viewModel::updateLatitude,
                            onLongitudeChange = viewModel::updateLongitude,
                            onGridInputChange = viewModel::updateGridInput,
                            onCalculateGrid = viewModel::calculateGrid,
                            onCalculateLatLng = viewModel::calculateLatLng,
                            onClear = viewModel::clearCalculator,
                            context = context
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 世界地图网格背景装饰
 */
@Composable
private fun WorldMapBackground(modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // 绘制经纬线网格
        val gridSpacingX = width / 18f
        val gridSpacingY = height / 18f
        
        // 经线
        for (i in 0..18) {
            val x = i * gridSpacingX
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }
        
        // 纬线
        for (i in 0..18) {
            val y = i * gridSpacingY
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }
        
        // 绘制简化的大陆轮廓（点缀）
        // 简化的圆形代表大陆位置
        val continents = listOf(
            Offset(width * 0.25f, height * 0.35f) to width * 0.08f, // 北美
            Offset(width * 0.3f, height * 0.55f) to width * 0.06f, // 南美
            Offset(width * 0.52f, height * 0.35f) to width * 0.1f, // 欧洲/非洲
            Offset(width * 0.75f, height * 0.4f) to width * 0.12f, // 亚洲
            Offset(width * 0.85f, height * 0.65f) to width * 0.05f, // 澳大利亚
        )
        
        continents.forEach { (center, radius) ->
            drawCircle(
                color = gridColor,
                radius = radius,
                center = center
            )
        }
    }
}

/**
 * 实时定位内容
 */
@Composable
private fun LiveLocationContent(
    locationState: LocationState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (locationState) {
            is LocationState.Initial,
            is LocationState.Loading -> {
                ShimmerGridDisplay()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulsingGpsIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.grid_locator_acquiring),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            is LocationState.PermissionDenied -> {
                PermissionDeniedContent(onRequestPermission)
            }
            
            is LocationState.Success -> {
                GridDisplayCard(
                    grid = locationState.location.grid,
                    latitude = locationState.location.latitude,
                    longitude = locationState.location.longitude,
                    altitude = locationState.location.altitude,
                    accuracy = locationState.location.accuracy,
                    context = context
                )
            }
            
            is LocationState.Error -> {
                ErrorContent(
                    message = locationState.message,
                    onRetry = onRetry
                )
            }
        }
    }
}

/**
 * Shimmer 加载动画 - 网格显示占位
 */
@Composable
private fun ShimmerGridDisplay() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerOffset * 500f, 0f),
        end = Offset((shimmerOffset + 1) * 500f, 0f)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 网格占位符
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 坐标信息占位符
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 按钮占位符
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

/**
 * 脉冲 GPS 图标动画
 */
@Composable
private fun PulsingGpsIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Icon(
        imageVector = Icons.Default.GpsFixed,
        contentDescription = null,
        modifier = Modifier.size((24 * scale).dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    )
}

/**
 * 网格显示卡片
 */
@Composable
private fun GridDisplayCard(
    grid: String,
    latitude: Double,
    longitude: Double,
    altitude: Double?,
    accuracy: Float?,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 超大号网格显示
            Text(
                text = grid,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 经纬度信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CoordinateChip(
                    label = stringResource(R.string.grid_locator_latitude),
                    value = String.format("%.6f°", latitude)
                )
                CoordinateChip(
                    label = stringResource(R.string.grid_locator_longitude),
                    value = String.format("%.6f°", longitude)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 海拔和精度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                altitude?.let {
                    CoordinateChip(
                        label = stringResource(R.string.grid_locator_altitude),
                        value = String.format("%.1f m", it)
                    )
                }
                accuracy?.let {
                    CoordinateChip(
                        label = stringResource(R.string.grid_locator_accuracy),
                        value = String.format("±%.1f m", it)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 复制按钮
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Grid Locator", grid))
                        Toast.makeText(context, "已复制网格: $grid", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.grid_locator_copy))
                }
                
                // 分享按钮
                Button(
                    onClick = {
                        val shareText = "My QTH is $grid"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.grid_locator_share_title)))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.grid_locator_share))
                }
            }
        }
    }
}

/**
 * 坐标信息小标签
 */
@Composable
private fun CoordinateChip(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 权限被拒绝内容
 */
@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.grid_locator_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.grid_locator_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.grid_locator_grant_permission))
            }
        }
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.grid_locator_retry))
            }
        }
    }
}

/**
 * 手动计算内容
 */
@Composable
private fun ManualCalculatorContent(
    state: CalculatorState,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onGridInputChange: (String) -> Unit,
    onCalculateGrid: () -> Unit,
    onCalculateLatLng: () -> Unit,
    onClear: () -> Unit,
    context: Context
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 正向计算卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.grid_locator_forward_calc),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.latitude,
                        onValueChange = onLatitudeChange,
                        label = { Text("纬度 (-90 ~ 90)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = state.longitude,
                        onValueChange = onLongitudeChange,
                        label = { Text("经度 (-180 ~ 180)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onCalculateGrid()
                            }
                        ),
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onCalculateGrid()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.grid_locator_calculate))
                }
                
                // 计算结果
                AnimatedVisibility(
                    visible = state.calculatedGrid != null
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.calculatedGrid ?: "",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                IconButton(
                                    onClick = {
                                        state.calculatedGrid?.let { grid ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Grid", grid))
                                            Toast.makeText(context, context.getString(R.string.grid_locator_copied), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.grid_locator_copy)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 反向计算卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.grid_locator_reverse_calc),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = state.gridInput,
                    onValueChange = { 
                        if (it.length <= 6) onGridInputChange(it.uppercase())
                    },
                    label = { Text("网格代码 (如 OM88hf)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onCalculateLatLng()
                        }
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onCalculateLatLng()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.grid_locator_calculate))
                }
                
                // 计算结果
                AnimatedVisibility(
                    visible = state.calculatedLatLng != null
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                state.calculatedLatLng?.let { (lat, lon) ->
                                    Text(
                                        text = "纬度: ${String.format("%.6f", lat)}°",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    Text(
                                        text = "经度: ${String.format("%.6f", lon)}°",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // 在地图中查看按钮
                                    FilledTonalButton(
                                        onClick = {
                                            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${state.gridInput})")
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // 如果没有地图应用，尝试用浏览器打开
                                                val webUri = Uri.parse("https://www.google.com/maps?q=$lat,$lon")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.grid_locator_open_map))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 错误提示
        AnimatedVisibility(visible = state.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = state.error ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // 清除按钮
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.grid_locator_clear))
        }
    }
}
