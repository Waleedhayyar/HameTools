package com.ham.tools.ui.screens.tools.qsl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ham.tools.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ham.tools.data.model.QslPlaceholder
import com.ham.tools.data.model.QslTemplate
import com.ham.tools.data.model.QsoLog
import com.ham.tools.data.model.TextElement

/**
 * QSL Card Editor Screen
 * 
 * Material 3 风格的 WYSIWYG QSL 卡片编辑器
 * 支持多卡片管理、动态取色
 * 
 * @param qsoLogId 可选的通联记录ID，如果提供则进入卡片生成模式
 * @param onNavigateBack 返回导航回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QslEditorScreen(
    qsoLogId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: QslEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    // 获取 Material 3 主题颜色
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val secondaryColor = MaterialTheme.colorScheme.secondary.toArgb()
    val tertiaryColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    
    // 初始化 ViewModel 使用动态颜色
    LaunchedEffect(Unit) {
        viewModel.initializeWithColors(
            M3Colors(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                secondary = secondaryColor,
                tertiary = tertiaryColor,
                surfaceContainer = surfaceContainerColor
            )
        )
    }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setBackgroundImage(it) }
    }
    
    // Show snackbar when saved
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }
    
    // 显示用户呼号提示
    var showCallsignHint by remember { mutableStateOf(false) }
    LaunchedEffect(state.userCallsign) {
        showCallsignHint = state.userCallsign.isNotBlank()
    }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (state.isGenerateMode) "生成 QSL 卡片" else "QSL 卡片工坊",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isGenerateMode) {
                                state.qsoLog?.let { "发送至 ${it.callsign}" } ?: state.templateName
                            } else {
                                state.templateName
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // 导出按钮
                    IconButton(onClick = { viewModel.showExportDialog() }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.qsl_export)
                        )
                    }
                    // 保存按钮
                    FilledTonalButton(
                        onClick = { viewModel.saveTemplate() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.qsl_save))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddElementDialog() },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.qsl_add_element)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 模板选择器
            TemplateSelector(
                templates = templates,
                currentTemplateId = state.templateName,
                onTemplateSelect = { viewModel.loadTemplate(it) },
                onNewTemplate = { viewModel.createNewTemplate() },
                onDeleteTemplate = { viewModel.deleteTemplate(it.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 通联记录信息提示（生成模式）
            AnimatedVisibility(
                visible = state.isGenerateMode && state.qsoLog != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                state.qsoLog?.let { qso ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "正在为 ${qso.callsign} 生成 QSL 卡片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${qso.frequency} · ${qso.mode.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 用户呼号提示（模板编辑模式）
            AnimatedVisibility(
                visible = !state.isGenerateMode && showCallsignHint && state.userCallsign.isNotBlank(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "您的呼号 ${state.userCallsign} 将自动填入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Canvas preview area
            QslCanvasEditor(
                state = state,
                getTextForPlaceholder = { viewModel.getTextForPlaceholder(it) },
                onElementSelected = { viewModel.selectElement(it) },
                onElementMoved = { id, x, y -> viewModel.updateElementPosition(id, x, y) },
                onElementScaled = { id, scale -> viewModel.updateElementFontSize(id, scale) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Selected element controls
            AnimatedVisibility(
                visible = state.selectedElementId != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                state.selectedElementId?.let { selectedId ->
                    val selectedElement = state.textElements.find { it.id == selectedId }
                    selectedElement?.let { element ->
                        ElementControls(
                            element = element,
                            onColorChange = { viewModel.showColorPicker() },
                            onToggleBold = { viewModel.toggleElementBold(selectedId) },
                            onDelete = { viewModel.deleteSelectedElement() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.qsl_select_background))
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Template name
            OutlinedTextField(
                value = state.templateName,
                onValueChange = { viewModel.updateTemplateName(it) },
                label = { Text(stringResource(R.string.qsl_template_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Color palette for background
            ColorPaletteSection(
                title = stringResource(R.string.qsl_background_color),
                selectedColor = state.backgroundColor,
                onColorSelected = { viewModel.setBackgroundColor(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            // 底部空间，为 FAB 留出位置
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    
    // Add element dialog
    if (state.showAddElementDialog) {
        AddElementDialog(
            onDismiss = { viewModel.hideAddElementDialog() },
            onAddElement = { viewModel.addTextElement(it) }
        )
    }
    
    // Color picker dialog for element
    if (state.showColorPicker) {
        state.selectedElementId?.let { selectedId ->
            ColorPickerDialog(
                onDismiss = { viewModel.hideColorPicker() },
                onColorSelected = { viewModel.updateElementColor(selectedId, it) }
            )
        }
    }
    
    // Export dialog
    if (state.showExportDialog) {
        ExportDialog(
            isExporting = state.isExporting,
            onDismiss = { viewModel.hideExportDialog() },
            onSaveToGallery = { viewModel.saveToGallery(context) },
            onShare = { 
                viewModel.shareCard(context) { uri ->
                    // 创建分享 Intent
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, "分享 QSL 卡片")
                    )
                }
            }
        )
    }
}

/**
 * 模板选择器 - 支持多卡片管理
 */
@Composable
private fun TemplateSelector(
    templates: List<QslTemplate>,
    currentTemplateId: String,
    onTemplateSelect: (QslTemplate) -> Unit,
    onNewTemplate: () -> Unit,
    onDeleteTemplate: (QslTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf<QslTemplate?>(null) }
    
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.qsl_my_templates),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            // 新建模板按钮
            item {
                Surface(
                    onClick = onNewTemplate,
                    modifier = Modifier
                        .size(width = 100.dp, height = 70.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.qsl_new_template),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.qsl_new),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // 现有模板列表
            items(templates) { template ->
                val isSelected = template.name == currentTemplateId
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.02f else 1f,
                    animationSpec = tween(200),
                    label = "scale"
                )
                
                Surface(
                    onClick = { onTemplateSelect(template) },
                    modifier = Modifier
                        .size(width = 120.dp, height = 70.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary
                    ) else null,
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 模板预览背景色
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(template.backgroundColor).copy(alpha = 0.3f),
                                            Color(template.backgroundColor).copy(alpha = 0.1f)
                                        )
                                    )
                                )
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                
                                if (template.isDefault) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.qsl_default),
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .size(12.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            
                            // 删除按钮 (仅非默认模板可删除)
                            if (!template.isDefault) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        onClick = { showDeleteDialog = template },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.qsl_delete),
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
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
    
    // 删除确认对话框
    showDeleteDialog?.let { template ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.qsl_delete_template)) },
            text = { Text(stringResource(R.string.qsl_delete_confirm, template.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTemplate(template)
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.qsl_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * Canvas editor with interactive text elements
 */
@Composable
private fun QslCanvasEditor(
    state: QslEditorState,
    getTextForPlaceholder: (QslPlaceholder) -> String,
    onElementSelected: (String?) -> Unit,
    onElementMoved: (String, Float, Float) -> Unit,
    onElementScaled: (String, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    
    // Load background image
    LaunchedEffect(state.backgroundUri) {
        backgroundBitmap = state.backgroundUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // 动态边框颜色
    val borderColor by animateColorAsState(
        targetValue = if (state.selectedElementId != null)
            MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f) // Standard QSL card ratio
                    // 单指点击 - 选中元素
                    .pointerInput(state.textElements) {
                        detectTapGestures { offset ->
                            val tappedElement = findElementAtPosition(
                                state.textElements,
                                offset,
                                canvasSize
                            )
                            onElementSelected(tappedElement?.id)
                        }
                    }
                    // 双指手势 - 同时处理移动和缩放（使用增量更新）
                    .pointerInput(state.selectedElementId) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val id = state.selectedElementId ?: return@detectTransformGestures
                            
                            // 处理平移（使用增量）
                            if (pan != Offset.Zero && canvasSize.width > 0 && canvasSize.height > 0) {
                                val deltaX = pan.x / canvasSize.width
                                val deltaY = pan.y / canvasSize.height
                                onElementMoved(id, deltaX, deltaY)
                            }
                            
                            // 处理缩放
                            if (zoom != 1f) {
                                onElementScaled(id, zoom)
                            }
                        }
                    }
            ) {
                canvasSize = size
                
                // Draw background
                backgroundBitmap?.let { bitmap ->
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap, size.width.toInt(), size.height.toInt(), true
                    )
                    drawImage(scaledBitmap.asImageBitmap())
                } ?: run {
                    drawRect(Color(state.backgroundColor))
                }
                
                // Draw text elements
                state.textElements.forEach { element ->
                    drawTextElement(
                        element = element,
                        displayText = getTextForPlaceholder(element.placeholder),
                        isSelected = element.id == state.selectedElementId,
                        canvasSize = size
                    )
                }
            }
            
            // 空状态提示
            if (state.textElements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 2f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "点击下方「添加元素」开始设计",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw a text element on the canvas
 */
private fun DrawScope.drawTextElement(
    element: TextElement,
    displayText: String,
    isSelected: Boolean,
    canvasSize: Size
) {
    val x = element.x * canvasSize.width
    val y = element.y * canvasSize.height
    val scaledFontSize = element.fontSize * (canvasSize.width / 400f)
    
    val paint = android.graphics.Paint().apply {
        color = element.color
        textSize = scaledFontSize
        isAntiAlias = true
        typeface = if (element.fontWeight >= 700) {
            android.graphics.Typeface.DEFAULT_BOLD
        } else {
            android.graphics.Typeface.DEFAULT
        }
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.argb(128, 0, 0, 0))
    }
    
    drawContext.canvas.nativeCanvas.drawText(displayText, x, y, paint)
    
    // Draw selection indicator
    if (isSelected) {
        val textWidth = paint.measureText(displayText)
        val textHeight = paint.textSize
        
        drawRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(x - 6, y - textHeight),
            size = Size(textWidth + 12, textHeight + 12)
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(x - 6, y - textHeight),
            size = Size(textWidth + 12, textHeight + 12),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}

/**
 * Find element at given position
 * 检测触摸点是否在文字元素的范围内
 */
private fun findElementAtPosition(
    elements: List<TextElement>,
    position: Offset,
    canvasSize: Size
): TextElement? {
    if (canvasSize == Size.Zero) return null
    
    // 从后往前查找（后添加的元素在上层）
    return elements.lastOrNull { element ->
        val x = element.x * canvasSize.width
        val y = element.y * canvasSize.height
        val fontSize = element.fontSize * (canvasSize.width / 400f)
        
        // 文字绘制时，y 是基线位置，文字在 y 上方
        // 扩大检测范围，使其更容易选中
        val hitBoxLeft = x - 30
        val hitBoxRight = x + 250  // 假设文字宽度约为 250 像素
        val hitBoxTop = y - fontSize - 20
        val hitBoxBottom = y + 20
        
        position.x in hitBoxLeft..hitBoxRight &&
        position.y in hitBoxTop..hitBoxBottom
    }
}

/**
 * Controls for selected element
 */
@Composable
private fun ElementControls(
    element: TextElement,
    onColorChange: () -> Unit,
    onToggleBold: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = element.placeholder.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "双指拖动移动，双指捏合缩放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Color button
                FilledTonalIconButton(
                    onClick = onColorChange,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(element.color))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
                
                // Bold toggle
                FilledTonalIconButton(
                    onClick = onToggleBold,
                    modifier = Modifier.size(40.dp),
                    colors = if (element.fontWeight >= 700) 
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    else IconButtonDefaults.filledTonalIconButtonColors()
                ) {
                    Text(
                        text = "B",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (element.fontWeight >= 700) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Delete button
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.qsl_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Color palette section with Material 3 styling
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteSection(
    title: String,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Material 3 风格的背景颜色
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest.toArgb(),
        MaterialTheme.colorScheme.primaryContainer.toArgb(),
        MaterialTheme.colorScheme.secondaryContainer.toArgb(),
        MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
        0xFF1C1B1F.toInt(), // Dark surface
        0xFF1A237E.toInt(), // Indigo
        0xFF004D40.toInt(), // Teal
        0xFF3E2723.toInt(), // Brown
    )
    
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { color ->
                val isSelected = color == selectedColor
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1f,
                    animationSpec = tween(150),
                    label = "colorScale"
                )
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(scale)
                        .shadow(if (isSelected) 4.dp else 0.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { onColorSelected(color) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (Color(color).luminance() > 0.5f)
                                Color.Black.copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculate luminance of a color
 */
private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}

/**
 * Dialog for adding new text element
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddElementDialog(
    onDismiss: () -> Unit,
    onAddElement: (QslPlaceholder) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                stringResource(R.string.qsl_add_text_element),
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QslPlaceholder.asList().forEach { placeholder ->
                    FilterChip(
                        onClick = { 
                            onAddElement(placeholder)
                            onDismiss()
                        },
                        label = { 
                            Text(
                                placeholder.displayName, 
                                style = MaterialTheme.typography.labelMedium
                            ) 
                        },
                        selected = false
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * Color picker dialog with Material 3 styling
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val textColors = listOf(
        MaterialTheme.colorScheme.onPrimary.toArgb(),
        MaterialTheme.colorScheme.primary.toArgb(),
        MaterialTheme.colorScheme.secondary.toArgb(),
        MaterialTheme.colorScheme.tertiary.toArgb(),
        0xFFFFFFFF.toInt(), // White
        0xFFFFEB3B.toInt(), // Yellow
        0xFF00E5FF.toInt(), // Cyan
        0xFF76FF03.toInt(), // Light green
        0xFFFF6D00.toInt(), // Orange
        0xFFFF4081.toInt(), // Pink
        0xFFE040FB.toInt(), // Purple
        0xFF448AFF.toInt(), // Blue
        0xFFFF5252.toInt(), // Red
        0xFF000000.toInt(), // Black
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                stringResource(R.string.qsl_select_color),
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                textColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures { 
                                    onColorSelected(color)
                                    onDismiss()
                                }
                            }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * Export dialog with options to save to gallery or share
 */
@Composable
private fun ExportDialog(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { 
            Text(
                "导出 QSL 卡片",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.qsl_export_choose),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 保存到相册按钮
                Surface(
                    onClick = { if (!isExporting) onSaveToGallery() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.qsl_save_to_gallery),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "保存为 PNG 图片到本地相册",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (isExporting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                
                // 分享按钮
                Surface(
                    onClick = { if (!isExporting) onShare() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.qsl_share),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "发送到微信、QQ、邮件等",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (isExporting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
