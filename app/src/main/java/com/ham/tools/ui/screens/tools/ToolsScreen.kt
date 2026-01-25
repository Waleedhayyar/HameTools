package com.ham.tools.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ham.tools.R

/**
 * Data class representing a tool item
 */
data class ToolItem(
    val id: String,
    val titleResId: Int,
    val icon: ImageVector
)

/**
 * List of available tools
 */
private val toolsList = listOf(
    ToolItem(
        id = "qsl",
        titleResId = R.string.tools_qsl,
        icon = Icons.Outlined.Email
    ),
    ToolItem(
        id = "cw",
        titleResId = R.string.tools_cw,
        icon = Icons.Outlined.Create
    ),
    ToolItem(
        id = "q_codes",
        titleResId = R.string.tools_q_codes,
        icon = Icons.Outlined.Info
    ),
    ToolItem(
        id = "sstv",
        titleResId = R.string.tools_sstv,
        icon = Icons.Outlined.Notifications
    ),
    ToolItem(
        id = "locator",
        titleResId = R.string.tools_locator,
        icon = Icons.Outlined.LocationOn
    ),
    ToolItem(
        id = "propagation",
        titleResId = R.string.tools_propagation,
        icon = Icons.Outlined.Call
    )
)

/**
 * Tools Screen - Collection of amateur radio utilities
 * 
 * Uses a grid layout for better visual organization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToQslEditor: () -> Unit = {},
    onNavigateToCwPractice: () -> Unit = {},
    onNavigateToQCodes: () -> Unit = {},
    onNavigateToSstv: () -> Unit = {},
    onNavigateToPropagation: () -> Unit = {},
    onNavigateToGridLocator: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tools_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(toolsList) { tool ->
                ToolGridCard(
                    tool = tool,
                    onClick = {
                        when (tool.id) {
                            "qsl" -> onNavigateToQslEditor()
                            "cw" -> onNavigateToCwPractice()
                            "q_codes" -> onNavigateToQCodes()
                            "sstv" -> onNavigateToSstv()
                            "propagation" -> onNavigateToPropagation()
                            "locator" -> onNavigateToGridLocator()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Grid card component for displaying a single tool
 */
@Composable
private fun ToolGridCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(tool.titleResId),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
