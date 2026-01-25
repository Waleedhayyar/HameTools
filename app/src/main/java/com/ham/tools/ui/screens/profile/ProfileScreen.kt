package com.ham.tools.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ham.tools.R
import com.ham.tools.data.model.FrequencyStat
import com.ham.tools.data.model.LicenseClass
import com.ham.tools.data.model.QsoStatistics
import com.ham.tools.data.model.UserProfile
import com.ham.tools.util.AppLanguage
import com.ham.tools.util.DataExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Android 16 风格大圆角
private val LargeCornerRadius = 28.dp
private val MediumCornerRadius = 24.dp

/**
 * Profile Screen - 我的
 * 
 * 包含执照卡片、通联统计、设置入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 处理导出结果
    val exportSuccessText = stringResource(R.string.export_success, uiState.exportResult?.let { 
        (it as? ExportResult.Success)?.recordCount 
    } ?: 0)
    val exportErrorText = stringResource(R.string.export_error, uiState.exportResult?.let { 
        (it as? ExportResult.Error)?.message 
    } ?: "")
    
    LaunchedEffect(uiState.exportResult) {
        uiState.exportResult?.let { result ->
            when (result) {
                is ExportResult.Success -> {
                    snackbarHostState.showSnackbar(exportSuccessText)
                }
                is ExportResult.Error -> {
                    snackbarHostState.showSnackbar(exportErrorText)
                }
            }
            viewModel.clearExportResult()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSettingsSheet(true) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.profile_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 执照卡片
            item {
                LicenseCard(
                    profile = userProfile,
                    isFlipped = uiState.isLicenseFlipped,
                    onFlip = { viewModel.toggleLicenseFlip() },
                    onEdit = { viewModel.toggleEditProfileDialog(true) }
                )
            }
            
            // 通联统计
            item {
                StatisticsSection(statistics = statistics)
            }
            
            // 波段统计图表
            if (statistics.frequencyStats.isNotEmpty()) {
                item {
                    BandStatsCard(stats = statistics.frequencyStats)
                }
            }
            
            // 快捷操作
            item {
                QuickActionsCard(
                    onSettingsClick = { viewModel.toggleSettingsSheet(true) }
                )
            }
        }
    }
    
    // 编辑配置对话框
    if (uiState.showEditProfileDialog) {
        EditProfileSheet(
            profile = userProfile,
            onDismiss = { viewModel.toggleEditProfileDialog(false) },
            onSave = { callsign, name, licenseClass, expiryDate, grid, qth ->
                viewModel.updateProfile(callsign, name, licenseClass, expiryDate, grid, qth)
            },
            onPhotoSelected = { uri -> viewModel.updateLicensePhoto(uri) }
        )
    }
    
    // 设置面板
    if (uiState.showSettingsSheet) {
        SettingsSheet(
            lastBackupTime = appSettings.lastBackupTime,
            currentLanguage = AppLanguage.fromCode(appSettings.languageCode),
            onDismiss = { viewModel.toggleSettingsSheet(false) },
            onExportJson = { uri -> viewModel.exportToJson(uri) },
            onExportCsv = { uri -> viewModel.exportToCsv(uri) },
            onLanguageChange = { language -> viewModel.updateLanguage(language, activity) }
        )
    }
}

/**
 * 执照卡片（带翻转动画）
 * 
 * 正面：呼号、姓名、执照等级
 * 背面：到期日、执照照片（如有）
 */
@Composable
private fun LicenseCard(
    profile: UserProfile,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onEdit: () -> Unit
) {
    // 翻转动画
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "card_flip"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)  // 信用卡比例
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { onFlip() }
    ) {
        if (rotation <= 90f) {
            // 正面
            LicenseCardFront(
                profile = profile,
                onEdit = onEdit
            )
        } else {
            // 背面（需要再翻转180度才能正常显示）
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f }
            ) {
                LicenseCardBack(profile = profile)
            }
        }
    }
}

/**
 * 执照卡片正面
 */
@Composable
private fun LicenseCardFront(
    profile: UserProfile,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(LargeCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            // 编辑按钮
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部：标题
                Column {
                    Text(
                        text = stringResource(R.string.license_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    if (profile.licenseClass != LicenseClass.NONE) {
                        Text(
                            text = stringResource(
                                when (profile.licenseClass) {
                                    LicenseClass.A -> R.string.license_class_a
                                    LicenseClass.B -> R.string.license_class_b
                                    LicenseClass.C -> R.string.license_class_c
                                    LicenseClass.NONE -> R.string.license_class_none
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // 中间：呼号
                Column {
                    if (profile.hasCallsign) {
                        Text(
                            text = profile.callsign,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (profile.name.isNotBlank()) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.license_tap_to_set_callsign),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // 底部：网格定位
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (profile.gridLocator.isNotBlank()) {
                            Text(
                                text = "Grid: ${profile.gridLocator}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        if (profile.qth.isNotBlank()) {
                            Text(
                                text = profile.qth,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // 翻转提示
                    Text(
                        text = stringResource(R.string.license_tap_to_flip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * 执照卡片背面
 */
@Composable
private fun LicenseCardBack(profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(LargeCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部：执照照片或占位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.licensePhotoUri != null) {
                        AsyncImage(
                            model = profile.licensePhotoUri,
                            contentDescription = stringResource(R.string.license_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.license_add_photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部：到期日信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.license_valid_until),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val notSetText = stringResource(R.string.license_not_set)
                        val expiryText = profile.licenseExpiryDate?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                        } ?: notSetText
                        
                        Text(
                            text = expiryText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                profile.isLicenseExpired -> MaterialTheme.colorScheme.error
                                profile.isLicenseExpiringSoon -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    
                    // 状态指示
                    if (profile.licenseExpiryDate != null) {
                        when {
                            profile.isLicenseExpired -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Warning,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.license_expired),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            profile.isLicenseExpiringSoon -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.license_expiring_soon),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通联统计区域
 */
@Composable
private fun StatisticsSection(statistics: QsoStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MediumCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📊 ${stringResource(R.string.profile_stats)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 主要统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.profile_stats_total),
                    value = statistics.totalCount.toString(),
                    highlight = true
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_year),
                    value = statistics.thisYearCount.toString()
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_month),
                    value = statistics.thisMonthCount.toString()
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_today),
                    value = statistics.todayCount.toString()
                )
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.headlineMedium 
                    else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 波段统计卡片（简单条形图）
 */
@Composable
private fun BandStatsCard(stats: List<FrequencyStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MediumCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "📻 ${stringResource(R.string.profile_band_stats)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val maxCount = stats.maxOfOrNull { it.count } ?: 1
            
            stats.take(5).forEach { stat ->
                BandStatBar(
                    bandName = stat.bandName,
                    count = stat.count,
                    maxCount = maxCount
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 波段统计条形
 */
@Composable
private fun BandStatBar(
    bandName: String,
    count: Int,
    maxCount: Int
) {
    val progress = count.toFloat() / maxCount
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bandName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(48.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 快捷操作卡片
 */
@Composable
private fun QuickActionsCard(
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MediumCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            ActionItem(
                icon = Icons.Outlined.Settings,
                title = stringResource(R.string.quick_action_settings),
                subtitle = stringResource(R.string.quick_action_settings_subtitle),
                onClick = onSettingsClick
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            ActionItem(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.quick_action_about),
                subtitle = stringResource(R.string.quick_action_about_subtitle),
                onClick = { }
            )
        }
    }
}

/**
 * 操作项
 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 编辑配置底部面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, LicenseClass, Long?, String, String) -> Unit,
    onPhotoSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var callsign by remember { mutableStateOf(profile.callsign) }
    var name by remember { mutableStateOf(profile.name) }
    var licenseClass by remember { mutableStateOf(profile.licenseClass) }
    var expiryDate by remember { mutableLongStateOf(profile.licenseExpiryDate ?: 0L) }
    var gridLocator by remember { mutableStateOf(profile.gridLocator) }
    var qth by remember { mutableStateOf(profile.qth) }
    var licenseClassExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // 日期格式化
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE) }
    
    // 日期选择器状态
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (expiryDate > 0) expiryDate else System.currentTimeMillis()
    )
    
    // 图片选择器
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it.toString()) }
    }
    
    // 日期选择器对话框
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            expiryDate = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = LargeCornerRadius, topEnd = LargeCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 呼号
            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it.uppercase() },
                label = { Text(stringResource(R.string.profile_callsign_label)) },
                placeholder = { Text(stringResource(R.string.profile_callsign_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 姓名
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 执照等级下拉框
            ExposedDropdownMenuBox(
                expanded = licenseClassExpanded,
                onExpandedChange = { licenseClassExpanded = it }
            ) {
                OutlinedTextField(
                    value = stringResource(
                        when (licenseClass) {
                            LicenseClass.A -> R.string.license_class_a
                            LicenseClass.B -> R.string.license_class_b
                            LicenseClass.C -> R.string.license_class_c
                            LicenseClass.NONE -> R.string.license_class_none
                        }
                    ),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.profile_license_class)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = licenseClassExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(16.dp)
                )
                
                ExposedDropdownMenu(
                    expanded = licenseClassExpanded,
                    onDismissRequest = { licenseClassExpanded = false }
                ) {
                    LicenseClass.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(stringResource(
                                    when (option) {
                                        LicenseClass.A -> R.string.license_class_a
                                        LicenseClass.B -> R.string.license_class_b
                                        LicenseClass.C -> R.string.license_class_c
                                        LicenseClass.NONE -> R.string.license_class_none
                                    }
                                ))
                            },
                            onClick = {
                                licenseClass = option
                                licenseClassExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 执照到期日期
            OutlinedTextField(
                value = if (expiryDate > 0) dateFormat.format(Date(expiryDate)) else "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.license_expiry_date)) },
                placeholder = { Text(stringResource(R.string.license_select_date_hint)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Outlined.DateRange,
                            contentDescription = stringResource(R.string.license_select_date)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 网格定位
            OutlinedTextField(
                value = gridLocator,
                onValueChange = { gridLocator = it.uppercase() },
                label = { Text(stringResource(R.string.profile_grid)) },
                placeholder = { Text(stringResource(R.string.profile_grid_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // QTH
            OutlinedTextField(
                value = qth,
                onValueChange = { qth = it },
                label = { Text(stringResource(R.string.profile_qth_label)) },
                placeholder = { Text(stringResource(R.string.profile_qth_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 执照照片
            OutlinedButton(
                onClick = { photoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.license_add_photo_button))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 保存按钮
            Button(
                onClick = {
                    onSave(
                        callsign,
                        name,
                        licenseClass,
                        if (expiryDate > 0) expiryDate else null,
                        gridLocator,
                        qth
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = callsign.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * 设置底部面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    lastBackupTime: Long?,
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onExportJson: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var languageExpanded by remember { mutableStateOf(false) }
    
    // JSON 导出器
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { onExportJson(it) }
    }
    
    // CSV 导出器
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { onExportCsv(it) }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = LargeCornerRadius, topEnd = LargeCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 语言设置
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 语言选择下拉框
            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (currentLanguage) {
                        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                        AppLanguage.CHINESE -> stringResource(R.string.settings_language_chinese)
                        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                        AppLanguage.JAPANESE -> stringResource(R.string.settings_language_japanese)
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(16.dp)
                )
                
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_language_system)) },
                        onClick = {
                            onLanguageChange(AppLanguage.SYSTEM)
                            languageExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_language_chinese)) },
                        onClick = {
                            onLanguageChange(AppLanguage.CHINESE)
                            languageExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_language_english)) },
                        onClick = {
                            onLanguageChange(AppLanguage.ENGLISH)
                            languageExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_language_japanese)) },
                        onClick = {
                            onLanguageChange(AppLanguage.JAPANESE)
                            languageExpanded = false
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 数据备份
            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (lastBackupTime != null) {
                Text(
                    text = "${stringResource(R.string.settings_last_backup)}: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackupTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { jsonExportLauncher.launch(DataExporter.generateFileName("json")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.settings_export_json))
                }
                
                FilledTonalButton(
                    onClick = { csvExportLauncher.launch(DataExporter.generateFileName("csv")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.settings_export_csv))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.settings_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
