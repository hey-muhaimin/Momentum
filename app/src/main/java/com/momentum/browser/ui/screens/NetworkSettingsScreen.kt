package com.momentum.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.momentum.browser.BuildConfig
import com.momentum.browser.R
import com.momentum.browser.model.ContentAgentNode
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.model.SearchEngine
import com.momentum.browser.viewmodel.MomentumViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkSettingsScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    onOpenUrnInBrowser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddAgentDialog by remember { mutableStateOf(false) }
    var urnInput by remember {
        mutableStateOf("hash://sha256/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "#Net Content Addressing",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Configure content-addressing agent nodes and verify hash:// URNs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Content-Addressing Agents Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Content-Addressing Agents",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddAgentDialog = true },
                        modifier = Modifier.testTag("add_agent_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Node")
                    }
                }
            }

            items(uiState.agents, key = { it.id }) { agent ->
                AgentNodeCard(
                    agent = agent,
                    onToggle = { viewModel.toggleAgentNode(agent.id) },
                    onTest = { viewModel.testAgentNode(agent.id) },
                    onDelete = { viewModel.removeAgentNode(agent.id) }
                )
            }

            // Live URN Resolver Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Verify hash:// / signed:// URN",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = urnInput,
                            onValueChange = { urnInput = it },
                            label = { Text("Content URN") },
                            textStyle = MaterialTheme.typography.labelMedium,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("urn_test_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.testContentUrn(urnInput) },
                                enabled = !uiState.isTestingUrn && urnInput.isNotBlank(),
                                modifier = Modifier.testTag("verify_urn_button")
                            ) {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (uiState.isTestingUrn) "Checking..." else "Verify Digest")
                            }
                            OutlinedButton(
                                onClick = { onOpenUrnInBrowser(urnInput) },
                                enabled = urnInput.isNotBlank(),
                                modifier = Modifier.testTag("open_urn_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Open Tab")
                            }
                        }
                        if (!uiState.urnTestStatus.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = uiState.urnTestStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search & Privacy Controls
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Search & Privacy",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Default Search Engine",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchEngine.entries.forEach { engine ->
                                FilterChip(
                                    selected = uiState.searchEngine == engine,
                                    onClick = { viewModel.updateSearchEngine(engine) },
                                    label = { Text(engine.label) },
                                    modifier = Modifier.testTag("search_engine_${engine.name}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tracker Shield", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Block analytics & ad network requests in WebView",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.trackerShieldEnabled,
                                onCheckedChange = viewModel::toggleTrackerShield,
                                modifier = Modifier.testTag("tracker_shield_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("JavaScript Execution", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Required for dynamic web apps & extension scripts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.javaScriptEnabled,
                                onCheckedChange = viewModel::toggleJavaScript,
                                modifier = Modifier.testTag("javascript_switch")
                            )
                        }
                    }
                }
            }

            // Build Info Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Momentum Android", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) • ${BuildConfig.APPLICATION_ID}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddAgentDialog) {
        AddAgentDialog(
            onDismiss = { showAddAgentDialog = false },
            onAdd = { label, url ->
                viewModel.addAgentNode(label, url)
                showAddAgentDialog = false
            }
        )
    }
}

@Composable
private fun AgentNodeCard(
    agent: ContentAgentNode,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = agent.baseUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = agent.enabled,
                    onCheckedChange = { onToggle() }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status: ${agent.lastStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = onTest) {
                        Text("Ping")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAgentDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("http://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add #Net Agent Node") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Node Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_label_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Agent Base URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(label, url) },
                enabled = label.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.testTag("save_agent_button")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
