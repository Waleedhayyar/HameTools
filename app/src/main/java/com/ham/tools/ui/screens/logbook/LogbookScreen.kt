package com.ham.tools.ui.screens.logbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ham.tools.R
import com.ham.tools.data.model.Mode
import com.ham.tools.data.model.PropagationMode
import com.ham.tools.data.model.QslStatus
import com.ham.tools.data.model.QsoLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Logbook Screen - Display QSO (contact) records
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    viewModel: LogbookViewModel = hiltViewModel(),
    onGenerateQsl: (QsoLog) -> Unit = {}
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val showBottomSheet by viewModel.showBottomSheet.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.logbook_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddSheet() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = "记录通联") }
            )
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = logs.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            EmptyLogbookContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        AnimatedVisibility(
            visible = logs.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 4.dp, bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = logs, key = { it.id }) { log ->
                    QsoLogCard(
                        log = log,
                        onGenerateQsl = { onGenerateQsl(log) },
                        onDelete = { viewModel.deleteQso(log) }
                    )
                }
            }
        }
    }

    if (showBottomSheet) {
        AddQsoBottomSheet(
            sheetState = sheetState,
            formState = formState,
            onDismiss = { viewModel.hideSheet() },
            onToggleAdvanced = { viewModel.toggleAdvanced() },
            onCallsignChange = { viewModel.updateCallsign(it) },
            onFrequencyChange = { viewModel.updateFrequency(it) },
            onModeChange = { viewModel.updateMode(it) },
            onRstSentChange = { viewModel.updateRstSent(it) },
            onRstRcvdChange = { viewModel.updateRstRcvd(it) },
            onOpNameChange = { viewModel.updateOpName(it) },
            onQthChange = { viewModel.updateQth(it) },
            onGridLocatorChange = { viewModel.updateGridLocator(it) },
            onQslInfoChange = { viewModel.updateQslInfo(it) },
            onTxPowerChange = { viewModel.updateTxPower(it) },
            onRigChange = { viewModel.updateRig(it) },
            onPropagationChange = { viewModel.updatePropagation(it) },
            onQslStatusChange = { viewModel.updateQslStatus(it) },
            onRemarksChange = { viewModel.updateRemarks(it) },
            onSave = { viewModel.saveQso() }
        )
    }
}

@Composable
private fun EmptyLogbookContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.logbook_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击下方按钮添加您的第一条通联记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Card displaying a single QSO log entry - clean and concise
 * Supports long press for context menu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsoLogCard(
    log: QsoLog,
    onGenerateQsl: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* TODO: View details */ },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("生成 QSL 卡片") },
                onClick = {
                    showMenu = false
                    onGenerateQsl()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Email, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("删除记录", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: Callsign + Mode chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.callsign,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Show name if available
                    log.opName?.let { name ->
                        Text(
                            text = " ($name)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text(log.mode.displayName, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row 2: Frequency + RST + Power
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = log.frequency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "RST: ${log.rstSent}/${log.rstRcvd}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                log.txPower?.let { power ->
                    Text(
                        text = power,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            // Row 3: QTH/Grid (if available)
            val locationInfo = listOfNotNull(log.qth, log.gridLocator).joinToString(" • ")
            if (locationInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = locationInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Row 4: Time + QSL Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dateFormat.format(Date(log.timestamp))} UTC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = log.qslStatus.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = when (log.qslStatus) {
                        QslStatus.NOT_SENT -> MaterialTheme.colorScheme.onSurfaceVariant
                        QslStatus.SENT, QslStatus.LOTW_UPLOADED, QslStatus.EQSL_SENT, QslStatus.CLUBLOG_UPLOADED -> 
                            MaterialTheme.colorScheme.tertiary
                        QslStatus.RECEIVED, QslStatus.LOTW_CONFIRMED, QslStatus.EQSL_CONFIRMED, QslStatus.CLUBLOG_CONFIRMED -> 
                            MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

/**
 * Bottom sheet for adding a new QSO log entry
 * With expandable advanced options section
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddQsoBottomSheet(
    sheetState: SheetState,
    formState: QsoFormState,
    onDismiss: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onCallsignChange: (String) -> Unit,
    onFrequencyChange: (String) -> Unit,
    onModeChange: (Mode) -> Unit,
    onRstSentChange: (String) -> Unit,
    onRstRcvdChange: (String) -> Unit,
    onOpNameChange: (String) -> Unit,
    onQthChange: (String) -> Unit,
    onGridLocatorChange: (String) -> Unit,
    onQslInfoChange: (String) -> Unit,
    onTxPowerChange: (String) -> Unit,
    onRigChange: (String) -> Unit,
    onPropagationChange: (PropagationMode) -> Unit,
    onQslStatusChange: (QslStatus) -> Unit,
    onRemarksChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "记录通联",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ===== 基本信息 (必填) =====
            SectionHeader(title = "基本信息")
            Spacer(modifier = Modifier.height(12.dp))
            
            // Callsign
            OutlinedTextField(
                value = formState.callsign,
                onValueChange = onCallsignChange,
                label = { Text("对方呼号 *") },
                placeholder = { Text("例如: BV2AAA") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = formState.callsignError != null,
                supportingText = formState.callsignError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                colors = defaultTextFieldColors()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Frequency + Mode in row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = formState.frequency,
                    onValueChange = onFrequencyChange,
                    label = { Text("频率 *") },
                    placeholder = { Text("14.200") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = formState.frequencyError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    colors = defaultTextFieldColors()
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mode chips
            Text(
                text = "模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Mode.asList().forEach { mode ->
                    FilterChip(
                        selected = formState.mode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // RST
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = formState.rstSent,
                    onValueChange = onRstSentChange,
                    label = { Text("RST发送") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = defaultTextFieldColors()
                )
                OutlinedTextField(
                    value = formState.rstRcvd,
                    onValueChange = onRstRcvdChange,
                    label = { Text("RST接收") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = defaultTextFieldColors()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ===== 展开/折叠 高级选项 =====
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            TextButton(
                onClick = onToggleAdvanced,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (formState.showAdvanced) 
                        Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (formState.showAdvanced) "收起详细信息" else "展开详细信息（可选）",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // ===== 高级选项区域 =====
            AnimatedVisibility(
                visible = formState.showAdvanced,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 对方信息
                    SectionHeader(title = "对方信息", icon = Icons.Outlined.Person)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.opName,
                            onValueChange = onOpNameChange,
                            label = { Text("姓名") },
                            placeholder = { Text("OP Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = defaultTextFieldColors()
                        )
                        OutlinedTextField(
                            value = formState.qth,
                            onValueChange = onQthChange,
                            label = { Text("QTH") },
                            placeholder = { Text("地点") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = defaultTextFieldColors()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.gridLocator,
                            onValueChange = onGridLocatorChange,
                            label = { Text("网格定位") },
                            placeholder = { Text("OM89cd") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            colors = defaultTextFieldColors()
                        )
                        OutlinedTextField(
                            value = formState.qslInfo,
                            onValueChange = onQslInfoChange,
                            label = { Text("QSL信息") },
                            placeholder = { Text("via bureau") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = defaultTextFieldColors()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 我方信息
                    SectionHeader(title = "我方信息")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.txPower,
                            onValueChange = onTxPowerChange,
                            label = { Text("发射功率") },
                            placeholder = { Text("100W") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = defaultTextFieldColors()
                        )
                        OutlinedTextField(
                            value = formState.rig,
                            onValueChange = onRigChange,
                            label = { Text("设备/天线") },
                            placeholder = { Text("IC-7300") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = defaultTextFieldColors()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 传播与QSL
                    SectionHeader(title = "传播与确认")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Propagation dropdown
                    PropagationDropdown(
                        selected = formState.propagation,
                        onSelect = onPropagationChange
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // QSL Status chips
                    Text(
                        text = "QSL状态",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Show common statuses
                        listOf(
                            QslStatus.NOT_SENT,
                            QslStatus.SENT,
                            QslStatus.RECEIVED,
                            QslStatus.LOTW_CONFIRMED,
                            QslStatus.EQSL_CONFIRMED
                        ).forEach { status ->
                            FilterChip(
                                selected = formState.qslStatus == status,
                                onClick = { onQslStatusChange(status) },
                                label = { Text(status.shortName, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 备注
                    SectionHeader(title = "备注")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = formState.remarks,
                        onValueChange = onRemarksChange,
                        label = { Text("备注") },
                        placeholder = { Text("天气、事件、聊天内容等...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = defaultTextFieldColors()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = formState.isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropagationDropdown(
    selected: PropagationMode,
    onSelect: (PropagationMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = { },
            readOnly = true,
            label = { Text("传播模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = defaultTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PropagationMode.asList().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun defaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary
)
