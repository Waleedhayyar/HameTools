package com.ham.tools.ui.screens.tools.qcodes

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ham.tools.R

/**
 * Q Code data class
 */
data class QCode(
    val code: String,
    val question: String,
    val statement: String,
    val category: String
)

/**
 * Complete list of common Q codes
 */
private val qCodesList = listOf(
    // 通用
    QCode("QRA", "你的电台名称是什么？", "我的电台名称是...", "通用"),
    QCode("QRB", "你离我台有多远？", "我们相距约...公里", "通用"),
    QCode("QRG", "你能告诉我准确频率吗？", "你的准确频率是...kHz", "频率"),
    QCode("QRH", "我的频率有漂移吗？", "你的频率有漂移", "频率"),
    QCode("QRI", "我的音调如何？", "你的音调是...", "信号"),
    QCode("QRK", "我的信号可辨度如何？", "你的信号可辨度是...(1-5)", "信号"),
    QCode("QRL", "你忙吗？", "我正忙，请勿干扰", "通用"),
    QCode("QRM", "你受到他台干扰吗？", "我正受到他台干扰", "干扰"),
    QCode("QRN", "你受到天电干扰吗？", "我正受到天电干扰", "干扰"),
    QCode("QRO", "我需要增加功率吗？", "请增加发射功率", "功率"),
    QCode("QRP", "我需要减小功率吗？", "请减小发射功率", "功率"),
    QCode("QRQ", "我发送得太快吗？", "请发送得快一些", "速度"),
    QCode("QRS", "我发送得太慢吗？", "请发送得慢一些", "速度"),
    QCode("QRT", "我应该停止发送吗？", "请停止发送/我即将关机", "通用"),
    QCode("QRU", "你有事给我吗？", "我没有事给你", "通用"),
    QCode("QRV", "你准备好了吗？", "我准备好了", "通用"),
    QCode("QRX", "你何时再呼叫我？", "请在...时再呼叫我", "时间"),
    QCode("QRZ", "谁在呼叫我？", "你被...呼叫（在...kHz上）", "呼叫"),
    QCode("QSA", "我的信号强度如何？", "你的信号强度是...(1-5)", "信号"),
    QCode("QSB", "我的信号有衰落吗？", "你的信号有衰落", "信号"),
    QCode("QSD", "我的发报有缺点吗？", "你的发报有缺点", "信号"),
    QCode("QSK", "你能听到我在你信号之间吗？", "我能听到你在我信号之间，请插入", "技术"),
    QCode("QSL", "你能确认收妥吗？", "我确认收妥", "确认"),
    QCode("QSO", "你能与...直接通信吗？", "我能与...直接通信", "通联"),
    QCode("QSP", "你能转告...吗？", "我可以转告...", "转发"),
    QCode("QST", "这是给所有电台的通告", "这是给所有电台的通告", "广播"),
    QCode("QSX", "你在...kHz上守听吗？", "我在...kHz上守听", "频率"),
    QCode("QSY", "我应该改频吗？", "请改频到...kHz", "频率"),
    QCode("QTC", "你有多少份报要发？", "我有...份报要发", "报文"),
    QCode("QTH", "你的位置是？", "我的位置是...", "位置"),
    QCode("QTR", "现在准确时间是？", "准确时间是...", "时间"),
    // 业余无线电常用
    QCode("QRG", "请告诉我准确频率", "你的准确频率是...", "频率"),
    QCode("QSS", "你使用什么工作频率？", "我使用...kHz工作", "频率"),
)

/**
 * Q Codes Dictionary Screen
 * 
 * Searchable list of Q codes with their meanings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QCodesScreen(
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    val filteredCodes by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                qCodesList.distinctBy { it.code }
            } else {
                qCodesList.filter { code ->
                    code.code.contains(searchQuery, ignoreCase = true) ||
                    code.question.contains(searchQuery, ignoreCase = true) ||
                    code.statement.contains(searchQuery, ignoreCase = true) ||
                    code.category.contains(searchQuery, ignoreCase = true)
                }.distinctBy { it.code }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qcodes_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
        ) {
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { isSearchActive = false },
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSearchActive) 0.dp else 16.dp),
                placeholder = { Text(stringResource(R.string.qcodes_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cw_clear))
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                // Search suggestions
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCodes.take(5)) { code ->
                        QCodeListItem(
                            qCode = code,
                            onClick = {
                                searchQuery = code.code
                                isSearchActive = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Results count
            Text(
                text = stringResource(R.string.qcodes_count, filteredCodes.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Q codes list
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 8.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredCodes) { code ->
                    QCodeCard(qCode = code)
                }
            }
        }
    }
}

/**
 * Simple list item for search suggestions
 */
@Composable
private fun QCodeListItem(
    qCode: QCode,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = qCode.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = qCode.statement,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Detailed card for Q code
 */
@Composable
private fun QCodeCard(qCode: QCode) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = qCode.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = qCode.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Question
            Row {
                Text(
                    text = stringResource(R.string.qcodes_question),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = qCode.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Statement
            Row {
                Text(
                    text = stringResource(R.string.qcodes_answer),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = qCode.statement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
