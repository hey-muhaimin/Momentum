package com.momentum.browser.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momentum.browser.R
import com.momentum.browser.model.MOMENTUM_HOME_URL
import com.momentum.browser.model.MomentumDestination
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.model.SubScreen
import com.momentum.browser.ui.components.AutofillSheet
import com.momentum.browser.ui.components.FindInPageBar
import com.momentum.browser.ui.components.MomentumWebView
import com.momentum.browser.ui.components.ReaderModeSheet
import com.momentum.browser.ui.components.SearchSuggestionsCard
import com.momentum.browser.ui.components.SiteInfoSheet
import com.momentum.browser.ui.components.TranslationBanner
import com.momentum.browser.viewmodel.MomentumViewModel

@Composable
fun BrowserScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    webViewRegistry: MutableMap<String, WebView>,
    modifier: Modifier = Modifier
) {
    val activeTab = uiState.activeTab
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var omniboxText by remember(activeTab.id) { mutableStateOf(activeTab.url) }
    var isEditingOmnibox by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab.url, activeTab.id) {
        if (!isEditingOmnibox) {
            omniboxText = if (activeTab.url == MOMENTUM_HOME_URL) "" else activeTab.url
        }
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSiteInfoSheet by remember { mutableStateOf(false) }
    var showAutofillSheet by remember { mutableStateOf(false) }

    val activeWebView = webViewRegistry[activeTab.id]
    val isBookmarked = uiState.bookmarks.any { it.url == activeTab.url }
    val currentHost = remember(activeTab.url) {
        runCatching { Uri.parse(activeTab.url).host }.getOrNull().orEmpty()
    }
    val suggestions = remember(omniboxText, isEditingOmnibox) {
        if (isEditingOmnibox) viewModel.searchSuggestionsFor(omniboxText) else emptyList()
    }

    BackHandler {
        when {
            uiState.findInPageActive -> viewModel.setFindInPageActive(false, activeWebView)
            uiState.readerModeActive -> viewModel.toggleReaderMode(false, activeWebView)
            activeWebView != null && activeWebView.canGoBack() -> activeWebView.goBack()
            else -> viewModel.navigateToDestination(MomentumDestination.HOME)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal Top Address Bar
            Surface(
                color = if (activeTab.isIncognito) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.background
                },
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Address Pill
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                    RoundedCornerShape(22.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val schemeIcon = when {
                                    activeTab.isIncognito -> Icons.Rounded.VisibilityOff
                                    activeTab.url.startsWith("hash://") || activeTab.url.startsWith("signed://") -> Icons.Rounded.Hub
                                    activeTab.url.startsWith("https://") -> Icons.Rounded.Lock
                                    else -> Icons.Rounded.Public
                                }
                                Icon(
                                    imageVector = schemeIcon,
                                    contentDescription = stringResource(R.string.cd_security),
                                    tint = if (activeTab.url.startsWith("https://") || activeTab.url.startsWith("hash://")) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { showSiteInfoSheet = true }
                                        .testTag("site_info_button")
                                )

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (omniboxText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.omnibox_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = omniboxText,
                                        onValueChange = {
                                            isEditingOmnibox = true
                                            omniboxText = it
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Go
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onGo = {
                                                isEditingOmnibox = false
                                                focusManager.clearFocus()
                                                viewModel.submitOmnibox(omniboxText)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("browser_omnibox_input")
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (activeTab.isLoading) activeWebView?.stopLoading()
                                        else viewModel.triggerReloadActiveTab()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                        contentDescription = if (activeTab.isLoading) stringResource(R.string.cd_stop) else stringResource(R.string.cd_reload),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Compact Tab Count Badge
                        IconButton(
                            onClick = { viewModel.navigateToDestination(MomentumDestination.TABS) },
                            modifier = Modifier.testTag("browser_top_tabs_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.tabs.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Top Overflow Menu
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("browser_menu_button")
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                            }
                            BrowserOverflowMenu(
                                expanded = showOverflowMenu,
                                isBookmarked = isBookmarked,
                                isDesktopMode = activeTab.isDesktopMode,
                                onDismiss = { showOverflowMenu = false },
                                onHome = { viewModel.navigateToDestination(MomentumDestination.HOME) },
                                onToggleBookmark = { viewModel.toggleBookmarkForActiveTab() },
                                onOpenMomentumAi = {
                                    viewModel.analyzeActivePage(activeWebView)
                                    viewModel.navigateToDestination(MomentumDestination.AI)
                                },
                                onFindInPage = { viewModel.setFindInPageActive(true, activeWebView) },
                                onReaderMode = { viewModel.toggleReaderMode(true, activeWebView) },
                                onTranslate = { viewModel.openTranslation(webView = activeWebView) },
                                onToggleDesktop = { viewModel.toggleDesktopSiteForActiveTab() },
                                onAutofill = { showAutofillSheet = true },
                                onSiteInfo = { showSiteInfoSheet = true },
                                onExtensions = { viewModel.openSubScreen(SubScreen.EXTENSIONS) },
                                onSettings = { viewModel.openSubScreen(SubScreen.SETTINGS) }
                            )
                        }
                    }

                    if (activeTab.isLoading) {
                        LinearProgressIndicator(
                            progress = { (activeTab.progress.coerceIn(5, 100)) / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                        )
                    }
                }
            }

            if (isEditingOmnibox && omniboxText.isNotBlank()) {
                SearchSuggestionsCard(
                    query = omniboxText,
                    suggestions = suggestions,
                    searchEngineLabel = uiState.searchEngine.label,
                    onSelectUrl = { url ->
                        isEditingOmnibox = false
                        focusManager.clearFocus()
                        viewModel.submitOmnibox(url)
                    },
                    onSearchQuery = { q ->
                        isEditingOmnibox = false
                        focusManager.clearFocus()
                        viewModel.submitOmnibox(q)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (uiState.findInPageActive) {
                FindInPageBar(
                    query = uiState.findQuery,
                    matchIndex = uiState.findMatchIndex,
                    matchTotal = uiState.findMatchTotal,
                    onQueryChange = { viewModel.updateFindQuery(it, activeWebView) },
                    onNext = { activeWebView?.findNext(true) },
                    onPrev = { activeWebView?.findNext(false) },
                    onClose = { viewModel.setFindInPageActive(false, activeWebView) }
                )
            }

            if (uiState.translationBannerVisible) {
                TranslationBanner(
                    targetLang = uiState.translationTargetLang,
                    previewText = uiState.translatedPreview,
                    onSelectLang = { lang -> viewModel.openTranslation(lang, activeWebView) },
                    onDismiss = { viewModel.dismissTranslation() }
                )
            }

            // Full-bleed WebView
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MomentumWebView(
                    tab = activeTab,
                    globalJavaScriptEnabled = uiState.javaScriptEnabled,
                    sitePermissionsProvider = viewModel::permissionsForHost,
                    isTrackerShieldActive = { viewModel.isTrackerShieldExtensionActive() },
                    injectionScriptsProvider = { viewModel.activeInjectionScripts() },
                    onWebViewReady = { wv -> webViewRegistry[activeTab.id] = wv },
                    onPageStarted = viewModel::onWebPageStarted,
                    onProgressChanged = viewModel::onWebPageProgress,
                    onPageFinished = viewModel::onWebPageFinished,
                    onDomExtracted = viewModel::onPageDomExtracted,
                    onFindResults = viewModel::onFindResults,
                    onDownloadRequested = viewModel::enqueueDownload,
                    onTrackerBlocked = viewModel::onTrackerBlocked
                )

                // Floating Liquid Glass Bottom Controls (matching mockup 02. Browser)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (activeWebView?.canGoBack() == true) activeWebView.goBack()
                                else viewModel.navigateToDestination(MomentumDestination.HOME)
                            },
                            modifier = Modifier.testTag("browser_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }

                        IconButton(
                            onClick = { activeWebView?.goForward() },
                            enabled = activeTab.canGoForward,
                            modifier = Modifier.testTag("browser_forward_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.cd_forward))
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                                    putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share via Momentum"))
                            },
                            modifier = Modifier.testTag("browser_share_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.cd_share))
                        }

                        IconButton(
                            onClick = { viewModel.navigateToDestination(MomentumDestination.TABS) },
                            modifier = Modifier.testTag("browser_bottom_tabs_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.tabs.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.analyzeActivePage(activeWebView)
                                viewModel.navigateToDestination(MomentumDestination.AI)
                            },
                            modifier = Modifier.testTag("browser_bottom_ai_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.nav_ai),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.readerModeActive) {
        ReaderModeSheet(
            title = activeTab.extractedTitle.ifBlank { activeTab.title },
            url = activeTab.url,
            bodyText = activeTab.extractedText,
            fontSizeSp = uiState.readerFontSizeSp,
            onAdjustFontSize = viewModel::adjustReaderFontSize,
            onDismiss = { viewModel.toggleReaderMode(false, activeWebView) }
        )
    }

    if (showSiteInfoSheet) {
        SiteInfoSheet(
            host = currentHost,
            url = activeTab.url,
            permissions = viewModel.permissionsForHost(currentHost),
            onUpdatePermissions = { viewModel.updateHostPermissions(currentHost, it) },
            onDismiss = { showSiteInfoSheet = false }
        )
    }

    if (showAutofillSheet) {
        AutofillSheet(
            profiles = uiState.autofillProfiles,
            onSelectProfile = { profile ->
                viewModel.injectAutofillIntoWebView(profile, activeWebView)
                showAutofillSheet = false
            },
            onDismiss = { showAutofillSheet = false }
        )
    }
}

@Composable
private fun BrowserOverflowMenu(
    expanded: Boolean,
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    onDismiss: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenMomentumAi: () -> Unit,
    onFindInPage: () -> Unit,
    onReaderMode: () -> Unit,
    onTranslate: () -> Unit,
    onToggleDesktop: () -> Unit,
    onAutofill: () -> Unit,
    onSiteInfo: () -> Unit,
    onExtensions: () -> Unit,
    onSettings: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Home") },
            leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            onClick = { onDismiss(); onHome() }
        )
        DropdownMenuItem(
            text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
            leadingIcon = { Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null) },
            onClick = { onDismiss(); onToggleBookmark() }
        )
        DropdownMenuItem(
            text = { Text("Ask Momentum AI") },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismiss(); onOpenMomentumAi() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Find in page") },
            leadingIcon = { Icon(Icons.Rounded.FindInPage, contentDescription = null) },
            onClick = { onDismiss(); onFindInPage() }
        )
        DropdownMenuItem(
            text = { Text("Reader mode") },
            leadingIcon = { Icon(Icons.Rounded.DesktopWindows, contentDescription = null) },
            onClick = { onDismiss(); onReaderMode() }
        )
        DropdownMenuItem(
            text = { Text("Translate page") },
            leadingIcon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
            onClick = { onDismiss(); onTranslate() }
        )
        DropdownMenuItem(
            text = { Text(if (isDesktopMode) "Mobile site" else "Desktop site") },
            leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null) },
            onClick = { onDismiss(); onToggleDesktop() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Autofill form") },
            leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
            onClick = { onDismiss(); onAutofill() }
        )
        DropdownMenuItem(
            text = { Text("Site info & permissions") },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            onClick = { onDismiss(); onSiteInfo() }
        )
        DropdownMenuItem(
            text = { Text("Extensions & Userscripts") },
            leadingIcon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
            onClick = { onDismiss(); onExtensions() }
        )
        DropdownMenuItem(
            text = { Text("Settings & #Net") },
            leadingIcon = { Icon(Icons.Rounded.Hub, contentDescription = null) },
            onClick = { onDismiss(); onSettings() }
        )
    }
}
