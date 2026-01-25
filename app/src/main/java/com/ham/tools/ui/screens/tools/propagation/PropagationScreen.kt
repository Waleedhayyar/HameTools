package com.ham.tools.ui.screens.tools.propagation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ham.tools.R
import com.ham.tools.data.model.BandCondition
import com.ham.tools.data.model.PropagationUiState
import com.ham.tools.data.model.SolarData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 传播预测屏幕
 * 
 * 显示当前太阳/地磁数据和 HF 波段传播条件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropagationScreen(
    onNavigateBack: () -> Unit,
    viewModel: PropagationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.propagation_title),
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
                actions = {
                    // 刷新按钮
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.propagation_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PropagationUiState.Loading -> {
                    LoadingContent()
                }
                is PropagationUiState.Success -> {
                    PropagationContent(
                        data = state.data,
                        isFromCache = state.isFromCache
                    )
                }
                is PropagationUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        cachedData = state.cachedData,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }
}

/**
 * 加载中内容
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在获取传播数据...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误内容
 */
@Composable
private fun ErrorContent(
    message: String,
    cachedData: SolarData?,
    onRetry: () -> Unit
) {
    if (cachedData != null) {
        // 有缓存数据时显示缓存，顶部显示错误提示
        Column(modifier = Modifier.fillMaxSize()) {
            // 错误提示条
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "网络错误，显示缓存数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.propagation_retry), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            // 缓存数据内容
            PropagationContent(
                data = cachedData,
                isFromCache = true
            )
        }
    } else {
        // 无缓存数据时显示错误页面
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.propagation_failed),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.propagation_retry))
                }
            }
        }
    }
}

/**
 * 传播数据内容
 */
@Composable
private fun PropagationContent(
    data: SolarData,
    isFromCache: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 更新时间信息
        item {
            UpdateInfoCard(
                updated = data.updated,
                isFromCache = isFromCache
            )
        }
        
        // 太阳/地磁指数卡片
        item {
            SolarIndicesCard(data = data)
        }
        
        // 传播总结
        item {
            SummaryCard(summary = data.generateSummary())
        }
        
        // 白天波段条件
        item {
            BandConditionsSection(
                title = "☀️ 白天传播条件",
                conditions = data.dayConditions
            )
        }
        
        // 夜间波段条件
        item {
            BandConditionsSection(
                title = "🌙 夜间传播条件",
                conditions = data.nightConditions
            )
        }
        
        // VHF 条件（如果有）
        if (data.vhfConditions.isNotEmpty()) {
            item {
                VhfConditionsCard(conditions = data.vhfConditions)
            }
        }
        
        // 底部间距
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 更新信息卡片
 */
@Composable
private fun UpdateInfoCard(
    updated: String,
    isFromCache: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.propagation_data_updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatGmtToLocal(updated),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        // 缓存提示
        AnimatedVisibility(
            visible = isFromCache,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "📦 显示缓存数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 太阳/地磁指数卡片
 */
@Composable
private fun SolarIndicesCard(data: SolarData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📡 太阳/地磁指数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主要指数 - 横向滚动
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    IndexChip(
                        label = "SFI",
                        value = data.solarFlux.toString(),
                        description = stringResource(R.string.propagation_solar_flux),
                        color = getSfiColor(data.solarFlux)
                    )
                }
                item {
                    IndexChip(
                        label = "K",
                        value = data.kIndex.toString(),
                        description = "K指数",
                        color = getKIndexColor(data.kIndex)
                    )
                }
                item {
                    IndexChip(
                        label = "A",
                        value = data.aIndex.toString(),
                        description = "A指数",
                        color = getAIndexColor(data.aIndex)
                    )
                }
                item {
                    IndexChip(
                        label = stringResource(R.string.propagation_geomag),
                        value = data.geomagField,
                        description = stringResource(R.string.propagation_geomag_status),
                        color = getGeomagColor(data.geomagField)
                    )
                }
                item {
                    IndexChip(
                        label = stringResource(R.string.propagation_noise),
                        value = data.signalNoise,
                        description = stringResource(R.string.propagation_signal_noise),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            
            // 附加信息（如果有）
            if (data.sunspots != null || data.solarWind != null || data.xRay != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    data.sunspots?.let {
                        ExtraInfo(label = stringResource(R.string.propagation_sunspots), value = it.toString())
                    }
                    data.solarWind?.let {
                        ExtraInfo(label = stringResource(R.string.propagation_solar_wind), value = "${it} km/s")
                    }
                    data.xRay?.let {
                        ExtraInfo(label = "X射线", value = it)
                    }
                }
            }
        }
    }
}

/**
 * 指数芯片
 */
@Composable
private fun IndexChip(
    label: String,
    value: String,
    description: String,
    color: Color
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 附加信息
 */
@Composable
private fun ExtraInfo(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 传播总结卡片
 */
@Composable
private fun SummaryCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 波段条件区域
 */
@Composable
private fun BandConditionsSection(
    title: String,
    conditions: List<BandCondition>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (conditions.isEmpty()) {
            Text(
                text = stringResource(R.string.propagation_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                conditions.forEach { condition ->
                    BandConditionChip(
                        condition = condition,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 波段条件芯片
 * 
 * 使用 Material 3 语义化颜色，Good/Fair/Poor 分别映射到:
 * - Good: tertiary (积极/成功)
 * - Fair: secondary (中性/警告)
 * - Poor: error (消极/危险)
 * 这样可以自动适配动态取色主题
 */
@Composable
private fun BandConditionChip(
    condition: BandCondition,
    modifier: Modifier = Modifier
) {
    // 使用 Material 3 语义化颜色，自动适配动态主题
    val backgroundColor = when (condition.condition.lowercase()) {
        "good" -> MaterialTheme.colorScheme.tertiaryContainer
        "fair" -> MaterialTheme.colorScheme.secondaryContainer
        "poor" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val indicatorColor = when (condition.condition.lowercase()) {
        "good" -> MaterialTheme.colorScheme.tertiary
        "fair" -> MaterialTheme.colorScheme.secondary
        "poor" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = condition.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 条件指示器
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = condition.condition,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * VHF 条件卡片
 */
@Composable
private fun VhfConditionsCard(
    conditions: List<com.ham.tools.data.model.VhfCondition>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "🌌 VHF 传播现象",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            conditions.forEach { condition ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = condition.phenomenon,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    condition.location?.let { location ->
                        Text(
                            text = " ($location)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ===== 辅助函数 =====

/**
 * 将 GMT 时间字符串转换为本地时间显示
 * 输入格式: "22 Jan 2026 0352 GMT"
 * 输出格式: "2026-01-22 11:52 (PST)"
 */
private fun formatGmtToLocal(gmtString: String): String {
    return try {
        // 解析 GMT 时间
        val inputFormat = SimpleDateFormat("dd MMM yyyy HHmm 'GMT'", Locale.ENGLISH)
        inputFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = inputFormat.parse(gmtString)
        
        // 转换为本地时间
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        
        val localTime = outputFormat.format(date ?: Date())
        val tzName = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)
        
        "$localTime ($tzName)"
    } catch (e: Exception) {
        gmtString  // 解析失败则返回原字符串
    }
}

/**
 * 获取 SFI 对应的颜色
 * 使用 Material 3 语义化颜色，自动适配动态主题
 */
@Composable
private fun getSfiColor(sfi: Int): Color {
    return when {
        sfi >= 150 -> MaterialTheme.colorScheme.tertiaryContainer   // 高，积极
        sfi >= 100 -> MaterialTheme.colorScheme.secondaryContainer  // 中，中性
        else -> MaterialTheme.colorScheme.errorContainer            // 低，警告
    }
}

/**
 * 获取 K 指数对应的颜色
 * K 越低越好，所以颜色逻辑与 SFI 相反
 */
@Composable
private fun getKIndexColor(k: Int): Color {
    return when {
        k <= 2 -> MaterialTheme.colorScheme.tertiaryContainer   // 平静，积极
        k <= 4 -> MaterialTheme.colorScheme.secondaryContainer  // 扰动，中性
        else -> MaterialTheme.colorScheme.errorContainer        // 风暴，警告
    }
}

/**
 * 获取 A 指数对应的颜色
 * A 越低越好
 */
@Composable
private fun getAIndexColor(a: Int): Color {
    return when {
        a <= 15 -> MaterialTheme.colorScheme.tertiaryContainer   // 平静
        a <= 50 -> MaterialTheme.colorScheme.secondaryContainer  // 扰动
        else -> MaterialTheme.colorScheme.errorContainer         // 风暴
    }
}

/**
 * 获取地磁状态对应的颜色
 */
@Composable
private fun getGeomagColor(geomag: String): Color {
    return when (geomag.uppercase()) {
        "QUIET" -> MaterialTheme.colorScheme.tertiaryContainer
        "UNSETTLED" -> MaterialTheme.colorScheme.secondaryContainer
        "ACTIVE" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.errorContainer  // STORM 等
    }
}
