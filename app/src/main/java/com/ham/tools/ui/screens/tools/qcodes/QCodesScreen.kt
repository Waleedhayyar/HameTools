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
 * Get localized Q codes list based on current locale
 */
@Composable
private fun getLocalizedQCodes(): List<QCode> {
    val catGeneral = stringResource(R.string.qcodes_cat_general)
    val catFrequency = stringResource(R.string.qcodes_cat_frequency)
    val catSignal = stringResource(R.string.qcodes_cat_signal)
    val catInterference = stringResource(R.string.qcodes_cat_interference)
    val catPower = stringResource(R.string.qcodes_cat_power)
    val catSpeed = stringResource(R.string.qcodes_cat_speed)
    val catTime = stringResource(R.string.qcodes_cat_time)
    val catCall = stringResource(R.string.qcodes_cat_call)
    val catTechnical = stringResource(R.string.qcodes_cat_technical)
    val catConfirm = stringResource(R.string.qcodes_cat_confirm)
    val catQso = stringResource(R.string.qcodes_cat_qso)
    val catRelay = stringResource(R.string.qcodes_cat_relay)
    val catBroadcast = stringResource(R.string.qcodes_cat_broadcast)
    val catMessage = stringResource(R.string.qcodes_cat_message)
    val catLocation = stringResource(R.string.qcodes_cat_location)
    
    return listOf(
        QCode("QRA", stringResource(R.string.qcode_qra_q), stringResource(R.string.qcode_qra_a), catGeneral),
        QCode("QRB", stringResource(R.string.qcode_qrb_q), stringResource(R.string.qcode_qrb_a), catGeneral),
        QCode("QRG", stringResource(R.string.qcode_qrg_q), stringResource(R.string.qcode_qrg_a), catFrequency),
        QCode("QRH", stringResource(R.string.qcode_qrh_q), stringResource(R.string.qcode_qrh_a), catFrequency),
        QCode("QRI", stringResource(R.string.qcode_qri_q), stringResource(R.string.qcode_qri_a), catSignal),
        QCode("QRK", stringResource(R.string.qcode_qrk_q), stringResource(R.string.qcode_qrk_a), catSignal),
        QCode("QRL", stringResource(R.string.qcode_qrl_q), stringResource(R.string.qcode_qrl_a), catGeneral),
        QCode("QRM", stringResource(R.string.qcode_qrm_q), stringResource(R.string.qcode_qrm_a), catInterference),
        QCode("QRN", stringResource(R.string.qcode_qrn_q), stringResource(R.string.qcode_qrn_a), catInterference),
        QCode("QRO", stringResource(R.string.qcode_qro_q), stringResource(R.string.qcode_qro_a), catPower),
        QCode("QRP", stringResource(R.string.qcode_qrp_q), stringResource(R.string.qcode_qrp_a), catPower),
        QCode("QRQ", stringResource(R.string.qcode_qrq_q), stringResource(R.string.qcode_qrq_a), catSpeed),
        QCode("QRS", stringResource(R.string.qcode_qrs_q), stringResource(R.string.qcode_qrs_a), catSpeed),
        QCode("QRT", stringResource(R.string.qcode_qrt_q), stringResource(R.string.qcode_qrt_a), catGeneral),
        QCode("QRU", stringResource(R.string.qcode_qru_q), stringResource(R.string.qcode_qru_a), catGeneral),
        QCode("QRV", stringResource(R.string.qcode_qrv_q), stringResource(R.string.qcode_qrv_a), catGeneral),
        QCode("QRX", stringResource(R.string.qcode_qrx_q), stringResource(R.string.qcode_qrx_a), catTime),
        QCode("QRZ", stringResource(R.string.qcode_qrz_q), stringResource(R.string.qcode_qrz_a), catCall),
        QCode("QSA", stringResource(R.string.qcode_qsa_q), stringResource(R.string.qcode_qsa_a), catSignal),
        QCode("QSB", stringResource(R.string.qcode_qsb_q), stringResource(R.string.qcode_qsb_a), catSignal),
        QCode("QSD", stringResource(R.string.qcode_qsd_q), stringResource(R.string.qcode_qsd_a), catSignal),
        QCode("QSK", stringResource(R.string.qcode_qsk_q), stringResource(R.string.qcode_qsk_a), catTechnical),
        QCode("QSL", stringResource(R.string.qcode_qsl_q), stringResource(R.string.qcode_qsl_a), catConfirm),
        QCode("QSO", stringResource(R.string.qcode_qso_q), stringResource(R.string.qcode_qso_a), catQso),
        QCode("QSP", stringResource(R.string.qcode_qsp_q), stringResource(R.string.qcode_qsp_a), catRelay),
        QCode("QST", stringResource(R.string.qcode_qst_q), stringResource(R.string.qcode_qst_a), catBroadcast),
        QCode("QSX", stringResource(R.string.qcode_qsx_q), stringResource(R.string.qcode_qsx_a), catFrequency),
        QCode("QSY", stringResource(R.string.qcode_qsy_q), stringResource(R.string.qcode_qsy_a), catFrequency),
        QCode("QTC", stringResource(R.string.qcode_qtc_q), stringResource(R.string.qcode_qtc_a), catMessage),
        QCode("QTH", stringResource(R.string.qcode_qth_q), stringResource(R.string.qcode_qth_a), catLocation),
        QCode("QTR", stringResource(R.string.qcode_qtr_q), stringResource(R.string.qcode_qtr_a), catTime),
    )
}

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
    val qCodesList = getLocalizedQCodes()
    
    val filteredCodes by remember(searchQuery, qCodesList) {
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
