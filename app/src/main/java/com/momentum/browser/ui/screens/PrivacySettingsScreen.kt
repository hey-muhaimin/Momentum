package com.momentum.browser.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momentum.browser.BuildConfig
import com.momentum.browser.R
import com.momentum.browser.model.LibrarySection
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.model.SearchEngine
import com.momentum.browser.model.SubScreen
import com.momentum.browser.ui.components.ClearBrowsingDataDialog
import com.momentum.browser.viewmodel.MomentumViewModel
import kotlin.math.roundToInt

@Composable
fun PrivacyHubScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    activeWebView: WebView?,
    modifier: Modifier = Modifier
) {
    var showClearDataDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Privacy Hub", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Tracking protection, credentials & browser internals",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openSubScreen(SubScreen.SETTINGS) },
                        modifier = Modifier.testTag("privacy_open_settings_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Open Settings")
                    }
                }
            }

            // Shield Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Momentum Shield Active", style = MaterialTheme.typography.titleLarge)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ShieldStatTile(
                                value = uiState.blockedTrackersCount.toString(),
                                label = "Trackers Blocked",
                                modifier = Modifier.weight(1f)
                            )
                            ShieldStatTile(
                                value = uiState.strippedParamsCount.toString(),
                                label = "URLs Cleaned",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tracker Shield", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Block analytics & ad network requests",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.trackerShieldEnabled,
                                onCheckedChange = viewModel::toggleTrackerShield,
                                modifier = Modifier.testTag("privacy_tracker_shield_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Safe Browsing", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Warn before navigating to suspicious domains",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.safeBrowsingEnabled,
                                onCheckedChange = viewModel::toggleSafeBrowsing,
                                modifier = Modifier.testTag("privacy_safe_browsing_switch")
                            )
                        }

                        OutlinedButton(
                            onClick = { showClearDataDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_clear_data_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Browsing Data")
                        }
                    }
                }
            }

            // Tools & Sub-screens
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsNavTile(
                        icon = Icons.Rounded.Password,
                        title = "Password Manager",
                        subtitle = "${uiState.credentials.size} saved credentials",
                        onClick = { viewModel.openSubScreen(SubScreen.PASSWORDS) },
                        testTag = "privacy_passwords_tile"
                    )
                    SettingsNavTile(
                        icon = Icons.Rounded.Person,
                        title = "Autofill & Payments",
                        subtitle = "${uiState.autofillProfiles.size} saved profiles",
                        onClick = { viewModel.openSubScreen(SubScreen.AUTOFILL) },
                        testTag = "privacy_autofill_tile"
                    )
                    SettingsNavTile(
                        icon = Icons.Rounded.Extension,
                        title = "Extensions & Userscripts",
                        subtitle = "${uiState.extensions.count { it.enabled }} active extensions",
                        onClick = { viewModel.openSubScreen(SubScreen.EXTENSIONS) },
                        testTag = "privacy_extensions_tile"
                    )
                    SettingsNavTile(
                        icon = Icons.Rounded.Hub,
                        title = "#Net Content Addressing",
                        subtitle = "${uiState.agents.count { it.enabled }} configured agent nodes",
                        onClick = { viewModel.openSubScreen(SubScreen.NETWORK_NODES) },
                        testTag = "privacy_network_tile"
                    )
                    SettingsNavTile(
                        icon = Icons.Rounded.Settings,
                        title = "All Browser Settings",
                        subtitle = "Appearance, search engine, downloads & accessibility",
                        onClick = { viewModel.openSubScreen(SubScreen.SETTINGS) },
                        testTag = "privacy_settings_tile"
                    )
                }
            }
        }
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = { history, cookies, cache, autofill ->
                viewModel.clearBrowsingData(history, cookies, cache, autofill, activeWebView)
                showClearDataDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    activeWebView: WebView?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }
    var showClearDataDialog by remember { mutableStateOf(false) }

    val webViewPkgVersion = remember {
        WebView.getCurrentWebViewPackage()?.versionName ?: "System WebView"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                }
            }

            item {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Profile Card (matching mockup 09. Settings)
            if (searchFilter.isBlank() || uiState.userDisplayName.contains(searchFilter, ignoreCase = true)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.userDisplayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(uiState.userDisplayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = uiState.userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "Local",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Appearance
            if (matchesFilter(searchFilter, "appearance dark amoled theme")) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dark theme", style = MaterialTheme.typography.bodyLarge)
                                    Text("Luminous Graphite surface palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = uiState.darkTheme,
                                    onCheckedChange = viewModel::toggleDarkTheme,
                                    modifier = Modifier.testTag("settings_dark_theme_switch")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AMOLED black", style = MaterialTheme.typography.bodyLarge)
                                    Text("Pure #000000 canvas in dark mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = uiState.amoledBlack,
                                    onCheckedChange = viewModel::toggleAmoledBlack,
                                    enabled = uiState.darkTheme,
                                    modifier = Modifier.testTag("settings_amoled_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Privacy & Security
            if (matchesFilter(searchFilter, "privacy security safe browsing javascript clear data")) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Privacy & security", style = MaterialTheme.typography.titleMedium)
                            }
                            SettingSwitchRow(
                                title = "Tracker Shield",
                                subtitle = "Block trackers & analytics requests",
                                checked = uiState.trackerShieldEnabled,
                                onCheckedChange = viewModel::toggleTrackerShield
                            )
                            SettingSwitchRow(
                                title = "Safe Browsing",
                                subtitle = "Enhanced URL protection",
                                checked = uiState.safeBrowsingEnabled,
                                onCheckedChange = viewModel::toggleSafeBrowsing
                            )
                            SettingSwitchRow(
                                title = "JavaScript Execution",
                                subtitle = "Enable dynamic web apps & scripts",
                                checked = uiState.javaScriptEnabled,
                                onCheckedChange = viewModel::toggleJavaScript
                            )
                            Button(
                                onClick = { showClearDataDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear browsing data")
                            }
                        }
                    }
                }
            }

            // Search Engine
            if (matchesFilter(searchFilter, "search engine google duckduckgo brave")) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Search engine", style = MaterialTheme.typography.titleMedium)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SearchEngine.entries.forEach { engine ->
                                    FilterChip(
                                        selected = uiState.searchEngine == engine,
                                        onClick = { viewModel.updateSearchEngine(engine) },
                                        label = { Text(engine.label) },
                                        modifier = Modifier.testTag("settings_engine_${engine.name}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Downloads & Accessibility
            if (matchesFilter(searchFilter, "downloads accessibility text size motion")) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Downloads", style = MaterialTheme.typography.titleMedium)
                            }
                            SettingSwitchRow(
                                title = "Ask where to save files",
                                subtitle = "Show prompt before starting downloads",
                                checked = uiState.askBeforeDownload,
                                onCheckedChange = viewModel::toggleAskBeforeDownload
                            )
                            OutlinedButton(
                                onClick = { viewModel.selectLibrarySection(LibrarySection.DOWNLOADS) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Download Queue")
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Accessibility", style = MaterialTheme.typography.titleMedium)
                            }
                            val pct = (uiState.textScale * 100).roundToInt()
                            Text("Text scaling ($pct%)", style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = uiState.textScale,
                                onValueChange = viewModel::updateTextScale,
                                valueRange = 0.85f..1.30f
                            )
                            SettingSwitchRow(
                                title = "Reduce motion",
                                subtitle = "Minimize transition animations",
                                checked = uiState.reduceMotion,
                                onCheckedChange = viewModel::toggleReduceMotion
                            )
                        }
                    }
                }
            }

            // Navigation Links to Extensions, #Net, Passwords, Autofill, About
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (matchesFilter(searchFilter, "passwords credentials autofill")) {
                        SettingsNavTile(
                            icon = Icons.Rounded.Password,
                            title = "Password Manager",
                            subtitle = "${uiState.credentials.size} saved logins",
                            onClick = { viewModel.openSubScreen(SubScreen.PASSWORDS) }
                        )
                        SettingsNavTile(
                            icon = Icons.Rounded.Person,
                            title = "Autofill & addresses",
                            subtitle = "${uiState.autofillProfiles.size} profiles",
                            onClick = { viewModel.openSubScreen(SubScreen.AUTOFILL) }
                        )
                    }
                    if (matchesFilter(searchFilter, "extensions userscripts")) {
                        SettingsNavTile(
                            icon = Icons.Rounded.Extension,
                            title = "WebExtensions",
                            subtitle = "${uiState.extensions.count { it.enabled }} active scripts & filters",
                            onClick = { viewModel.openSubScreen(SubScreen.EXTENSIONS) }
                        )
                    }
                    if (matchesFilter(searchFilter, "net content addressing hash signed nodes")) {
                        SettingsNavTile(
                            icon = Icons.Rounded.Hub,
                            title = "#Net Content Addressing",
                            subtitle = "Agent nodes & URN verification",
                            onClick = { viewModel.openSubScreen(SubScreen.NETWORK_NODES) }
                        )
                    }
                    if (matchesFilter(searchFilter, "about momentum version chromium")) {
                        SettingsNavTile(
                            icon = Icons.Rounded.Info,
                            title = "About Momentum",
                            subtitle = "v${BuildConfig.VERSION_NAME} • Engine $webViewPkgVersion",
                            onClick = { viewModel.openSubScreen(SubScreen.ABOUT) },
                            testTag = "settings_about_tile"
                        )
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = { history, cookies, cache, autofill ->
                viewModel.clearBrowsingData(history, cookies, cache, autofill, activeWebView)
                showClearDataDialog = false
            }
        )
    }
}

@Composable
private fun ShieldStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsNavTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun matchesFilter(query: String, keywords: String): Boolean {
    val q = query.trim().lowercase()
    return q.isEmpty() || keywords.contains(q)
}
