package com.momentum.browser.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momentum.browser.data.BrowserFilterEngine
import com.momentum.browser.data.ContentAddressResolution
import com.momentum.browser.data.MomentumRepository
import com.momentum.browser.data.OnDeviceAiEngine
import com.momentum.browser.model.AiChatMessage
import com.momentum.browser.model.AutofillProfile
import com.momentum.browser.model.BookmarkCollection
import com.momentum.browser.model.BookmarkItem
import com.momentum.browser.model.BrowserExtension
import com.momentum.browser.model.BrowserTab
import com.momentum.browser.model.ContentAgentNode
import com.momentum.browser.model.DownloadItem
import com.momentum.browser.model.DownloadStatus
import com.momentum.browser.model.HistoryEntry
import com.momentum.browser.model.LibrarySection
import com.momentum.browser.model.MOMENTUM_HOME_URL
import com.momentum.browser.model.MomentumDestination
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.model.SavedCredential
import com.momentum.browser.model.SearchEngine
import com.momentum.browser.model.ShortcutItem
import com.momentum.browser.model.SitePermissionState
import com.momentum.browser.model.Space
import com.momentum.browser.model.SubScreen
import com.momentum.browser.model.TabGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MomentumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MomentumRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(MomentumUiState())
    val uiState: StateFlow<MomentumUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.persistedData.collect { p ->
                _uiState.update { state ->
                    state.copy(
                        hasCompletedOnboarding = p.hasCompletedOnboarding,
                        spaces = p.spaces,
                        activeSpaceId = p.activeSpaceId,
                        tabGroups = p.tabGroups,
                        shortcuts = p.shortcuts,
                        bookmarks = p.bookmarks,
                        collections = p.collections,
                        history = p.history,
                        downloads = p.downloads,
                        credentials = p.credentials,
                        autofillProfiles = p.autofillProfiles,
                        extensions = p.extensions,
                        agents = p.agents,
                        searchEngine = p.searchEngine,
                        darkTheme = p.darkTheme,
                        amoledBlack = p.amoledBlack,
                        textScale = p.textScale,
                        reduceMotion = p.reduceMotion,
                        trackerShieldEnabled = p.trackerShieldEnabled,
                        safeBrowsingEnabled = p.safeBrowsingEnabled,
                        javaScriptEnabled = p.javaScriptEnabled,
                        askBeforeDownload = p.askBeforeDownload,
                        blockedTrackersCount = p.blockedTrackersCount,
                        strippedParamsCount = p.strippedParamsCount
                    )
                }
            }
        }
    }

    // --- Navigation & Onboarding ---

    fun completeOnboarding(importStarterBookmarks: Boolean) {
        if (importStarterBookmarks) {
            val current = _uiState.value.bookmarks
            if (current.none { it.url.contains("wikipedia.org") }) {
                val updated = current + BookmarkItem(
                    title = "Wikipedia",
                    url = "https://en.m.wikipedia.org",
                    collectionId = "col_chromium"
                )
                _uiState.update { it.copy(bookmarks = updated) }
                viewModelScope.launch { repository.saveBookmarks(updated) }
            }
        }
        _uiState.update { it.copy(hasCompletedOnboarding = true) }
        viewModelScope.launch { repository.setOnboardingDone(true) }
    }

    fun navigateToDestination(dest: MomentumDestination) {
        _uiState.update {
            it.copy(
                destination = dest,
                subScreen = SubScreen.NONE,
                findInPageActive = false,
                readerModeActive = false
            )
        }
    }

    fun openSubScreen(sub: SubScreen) {
        _uiState.update { it.copy(subScreen = sub) }
    }

    fun closeSubScreen() {
        _uiState.update {
            it.copy(
                subScreen = SubScreen.NONE,
                findInPageActive = false,
                readerModeActive = false,
                translationBannerVisible = false
            )
        }
    }

    fun selectLibrarySection(section: LibrarySection) {
        _uiState.update {
            it.copy(
                destination = MomentumDestination.LIBRARY,
                subScreen = SubScreen.NONE,
                librarySection = section
            )
        }
    }

    // --- Omnibox, WebView & Search Suggestions ---

    fun submitOmnibox(rawInput: String, openInBrowser: Boolean = true) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.equals(MOMENTUM_HOME_URL, ignoreCase = true) || trimmed.equals("about:home", ignoreCase = true)) {
            updateActiveTab {
                it.copy(
                    url = MOMENTUM_HOME_URL,
                    title = "New Tab",
                    isLoading = false,
                    progress = 0,
                    resolvedHtml = null,
                    contentAddressStatus = null
                )
            }
            if (openInBrowser) {
                _uiState.update { it.copy(destination = MomentumDestination.HOME, subScreen = SubScreen.NONE) }
            }
            return
        }

        if (trimmed.startsWith("hash://", ignoreCase = true) ||
            trimmed.startsWith("signed://", ignoreCase = true) ||
            trimmed.startsWith("related://", ignoreCase = true)
        ) {
            if (openInBrowser) {
                _uiState.update { it.copy(subScreen = SubScreen.BROWSER) }
            }
            resolveUrnInActiveTab(trimmed)
            return
        }

        val resolvedUrl = normalizeUrlOrSearch(trimmed, _uiState.value.searchEngine)
        val cleanResult = if (isCleanUrlsEnabled()) {
            BrowserFilterEngine.stripTrackingParams(resolvedUrl)
        } else {
            resolvedUrl to 0
        }

        if (cleanResult.second > 0) {
            val newStripped = _uiState.value.strippedParamsCount + cleanResult.second
            _uiState.update { it.copy(strippedParamsCount = newStripped) }
            viewModelScope.launch {
                repository.saveShieldCounters(_uiState.value.blockedTrackersCount, newStripped)
            }
        }

        updateActiveTab {
            it.copy(
                url = cleanResult.first,
                isLoading = true,
                progress = 10,
                resolvedHtml = null,
                contentAddressStatus = null,
                lastVisitedMs = System.currentTimeMillis()
            )
        }
        if (openInBrowser) {
            _uiState.update { it.copy(subScreen = SubScreen.BROWSER) }
        }
    }

    fun openUrlInNewTab(url: String, isIncognito: Boolean = false) {
        val spaceId = _uiState.value.activeSpaceId
        val resolved = normalizeUrlOrSearch(url.trim(), _uiState.value.searchEngine)
        val tab = BrowserTab(
            spaceId = spaceId,
            url = resolved,
            title = if (isIncognito) "Incognito Tab" else resolved,
            isIncognito = isIncognito,
            isLoading = resolved != MOMENTUM_HOME_URL
        )
        _uiState.update {
            it.copy(
                tabs = it.tabs + tab,
                activeTabId = tab.id,
                subScreen = if (resolved == MOMENTUM_HOME_URL) SubScreen.NONE else SubScreen.BROWSER,
                destination = if (resolved == MOMENTUM_HOME_URL) MomentumDestination.HOME else it.destination
            )
        }
    }

    fun searchSuggestionsFor(query: String): List<Pair<String, String>> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val results = linkedMapOf<String, String>()

        _uiState.value.bookmarks
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(3)
            .forEach { results[it.url] = "Bookmark • ${it.title}" }

        _uiState.value.history
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(3)
            .forEach { results.putIfAbsent(it.url, "History • ${it.title}") }

        _uiState.value.tabs
            .filter { it.url != MOMENTUM_HOME_URL && (it.title.lowercase().contains(q) || it.url.lowercase().contains(q)) }
            .take(2)
            .forEach { results.putIfAbsent(it.url, "Open tab • ${it.title}") }

        return results.entries.map { it.value to it.key }
    }

    private fun resolveUrnInActiveTab(urn: String) {
        val targetTabId = _uiState.value.activeTab.id
        updateTabById(targetTabId) {
            it.copy(
                url = urn,
                title = urn.take(38),
                isLoading = true,
                progress = 30,
                resolvedHtml = null,
                contentAddressStatus = "Polling #Net agents..."
            )
        }

        viewModelScope.launch {
            when (val res = repository.resolveContentAddress(urn, _uiState.value.agents)) {
                is ContentAddressResolution.VerifiedContent -> {
                    updateTabById(targetTabId) {
                        it.copy(
                            url = urn,
                            title = "Verified • ${res.agentLabel}",
                            isLoading = false,
                            progress = 100,
                            resolvedHtml = res.htmlBody,
                            extractedTitle = urn,
                            extractedText = res.htmlBody.replace(Regex("<[^>]+>"), " ").trim(),
                            contentAddressStatus = "Verified via ${res.agentLabel}"
                        )
                    }
                }
                is ContentAddressResolution.Failure -> {
                    val html = buildUrnFailureHtml(res.urn, res.message)
                    updateTabById(targetTabId) {
                        it.copy(
                            url = urn,
                            title = "#Net Resolution Failed",
                            isLoading = false,
                            progress = 100,
                            resolvedHtml = html,
                            extractedText = res.message,
                            contentAddressStatus = res.message
                        )
                    }
                }
            }
        }
    }

    private fun buildUrnFailureHtml(urn: String, reason: String): String {
        val safeUrn = urn.replace("<", "&lt;").replace(">", "&gt;")
        val safeReason = reason.replace("<", "&lt;").replace(">", "&gt;")
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                body { background: #0B0D0C; color: #F4F6F3; font-family: sans-serif; padding: 20px; }
                .card { background: #171B19; border: 1px solid #2E3632; border-radius: 18px; padding: 18px; }
                .tag { display: inline-block; background: #272244; color: #8B7CFF; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
                .urn { color: #C5FF45; font-family: monospace; font-size: 12px; word-break: break-all; margin: 12px 0; }
                p { color: #929B95; font-size: 14px; line-height: 1.5; }
              </style>
            </head>
            <body>
              <div class="card">
                <span class="tag">#Net Content Addressing</span>
                <div class="urn">$safeUrn</div>
                <p>$safeReason</p>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun triggerReloadActiveTab() {
        val active = _uiState.value.activeTab
        if (active.url.startsWith("hash://") || active.url.startsWith("signed://")) {
            resolveUrnInActiveTab(active.url)
        } else {
            updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1, isLoading = true) }
        }
    }

    fun toggleDesktopSiteForActiveTab() {
        updateActiveTab { it.copy(isDesktopMode = !it.isDesktopMode, reloadNonce = it.reloadNonce + 1) }
    }

    fun onWebPageStarted(tabId: String, url: String) {
        updateTabById(tabId) {
            it.copy(url = url, isLoading = true, progress = 15)
        }
    }

    fun onWebPageProgress(tabId: String, progress: Int) {
        updateTabById(tabId) {
            it.copy(progress = progress, isLoading = progress < 100)
        }
    }

    fun onWebPageFinished(
        tabId: String,
        url: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: url
        updateTabById(tabId) {
            it.copy(
                url = url,
                title = resolvedTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                lastVisitedMs = System.currentTimeMillis()
            )
        }

        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab != null && !tab.isIncognito && !url.startsWith("momentum://") && !url.startsWith("about:")) {
            recordHistory(resolvedTitle, url)
        }
    }

    fun onPageDomExtracted(tabId: String, rawJson: String) {
        val parsed = OnDeviceAiEngine.parseDomJson(rawJson)
        updateTabById(tabId) {
            it.copy(
                extractedTitle = parsed.title.ifBlank { it.title },
                extractedText = parsed.fullText,
                extractedHeadings = parsed.headings
            )
        }
    }

    fun onTrackerBlocked() {
        val updated = _uiState.value.blockedTrackersCount + 1
        _uiState.update { it.copy(blockedTrackersCount = updated) }
        viewModelScope.launch {
            repository.saveShieldCounters(updated, _uiState.value.strippedParamsCount)
        }
    }

    // --- Spaces, Tabs & Tab Groups ---

    fun selectSpace(spaceId: String) {
        val tabsInSpace = _uiState.value.tabs.filter { it.spaceId == spaceId }
        val nextActive = tabsInSpace.firstOrNull()?.id ?: _uiState.value.activeTabId
        _uiState.update {
            it.copy(activeSpaceId = spaceId, activeTabId = nextActive)
        }
        viewModelScope.launch { repository.saveSpaces(_uiState.value.spaces, spaceId) }
    }

    fun createSpace(name: String, colorHex: Long) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val newSpace = Space(
            id = "space_${System.currentTimeMillis()}",
            name = clean,
            colorHex = colorHex,
            iconKey = "custom"
        )
        val updated = _uiState.value.spaces + newSpace
        _uiState.update { it.copy(spaces = updated, activeSpaceId = newSpace.id) }
        viewModelScope.launch { repository.saveSpaces(updated, newSpace.id) }
        createNewTab(isIncognito = false, initialUrl = MOMENTUM_HOME_URL, openBrowser = false)
    }

    fun createNewTab(
        isIncognito: Boolean = false,
        initialUrl: String = MOMENTUM_HOME_URL,
        groupId: String? = null,
        openBrowser: Boolean = initialUrl != MOMENTUM_HOME_URL
    ) {
        val newTab = BrowserTab(
            spaceId = _uiState.value.activeSpaceId,
            groupId = groupId,
            url = initialUrl,
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            isIncognito = isIncognito,
            isLoading = initialUrl != MOMENTUM_HOME_URL
        )
        _uiState.update {
            it.copy(
                tabs = it.tabs + newTab,
                activeTabId = newTab.id,
                subScreen = if (openBrowser) SubScreen.BROWSER else SubScreen.NONE,
                destination = if (!openBrowser && initialUrl == MOMENTUM_HOME_URL) MomentumDestination.HOME else it.destination
            )
        }
    }

    fun selectTabAndOpen(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                activeTabId = tab.id,
                activeSpaceId = tab.spaceId,
                subScreen = if (tab.url == MOMENTUM_HOME_URL) SubScreen.NONE else SubScreen.BROWSER,
                destination = if (tab.url == MOMENTUM_HOME_URL) MomentumDestination.HOME else it.destination
            )
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(spaceId = state.activeSpaceId)
                state.copy(tabs = listOf(fresh), activeTabId = fresh.id)
            } else {
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.lastOrNull { it.spaceId == state.activeSpaceId }?.id ?: remaining.last().id
                } else {
                    state.activeTabId
                }
                state.copy(tabs = remaining, activeTabId = nextActive)
            }
        }
    }

    fun toggleTabGridMode() {
        _uiState.update { it.copy(isTabGridMode = !it.isTabGridMode) }
    }

    fun createTabGroup(name: String, colorHex: Long) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val group = TabGroup(
            name = clean,
            colorHex = colorHex,
            spaceId = _uiState.value.activeSpaceId
        )
        val updated = _uiState.value.tabGroups + group
        _uiState.update { it.copy(tabGroups = updated) }
        viewModelScope.launch { repository.saveTabGroups(updated) }
    }

    fun renameTabGroup(groupId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val updated = _uiState.value.tabGroups.map {
            if (it.id == groupId) it.copy(name = clean) else it
        }
        _uiState.update { it.copy(tabGroups = updated) }
        viewModelScope.launch { repository.saveTabGroups(updated) }
    }

    fun assignTabToGroup(tabId: String, groupId: String?) {
        updateTabById(tabId) { it.copy(groupId = groupId) }
    }

    fun toggleTabGroupCollapsed(groupId: String) {
        val updated = _uiState.value.tabGroups.map {
            if (it.id == groupId) it.copy(isCollapsed = !it.isCollapsed) else it
        }
        _uiState.update { it.copy(tabGroups = updated) }
        viewModelScope.launch { repository.saveTabGroups(updated) }
    }

    // --- Shortcuts, Bookmarks, Collections & History ---

    fun addShortcut(title: String, url: String) {
        val cleanTitle = title.trim()
        val normalizedUrl = normalizeUrlOrSearch(url.trim(), _uiState.value.searchEngine)
        if (cleanTitle.isEmpty()) return
        val badge = cleanTitle.take(2).uppercase()
        val palette = listOf(0xFFC5FF45, 0xFF8B7CFF, 0xFF38BDF8, 0xFFF97316, 0xFF22C55E)
        val color = palette[_uiState.value.shortcuts.size % palette.size]
        val updated = _uiState.value.shortcuts + ShortcutItem(
            title = cleanTitle,
            url = normalizedUrl,
            badgeText = badge,
            badgeColorHex = color
        )
        _uiState.update { it.copy(shortcuts = updated) }
        viewModelScope.launch { repository.saveShortcuts(updated) }
    }

    fun removeShortcut(id: String) {
        val updated = _uiState.value.shortcuts.filterNot { it.id == id }
        _uiState.update { it.copy(shortcuts = updated) }
        viewModelScope.launch { repository.saveShortcuts(updated) }
    }

    fun toggleBookmarkForActiveTab() {
        val active = _uiState.value.activeTab
        if (active.url == MOMENTUM_HOME_URL) return
        val current = _uiState.value.bookmarks
        val existing = current.firstOrNull { it.url == active.url }
        val updated = if (existing != null) {
            current.filterNot { it.id == existing.id }
        } else {
            listOf(BookmarkItem(title = active.title.ifBlank { active.url }, url = active.url)) + current
        }
        _uiState.update { it.copy(bookmarks = updated) }
        viewModelScope.launch { repository.saveBookmarks(updated) }
    }

    fun addBookmark(title: String, url: String, collectionId: String? = null) {
        val cleanTitle = title.trim()
        val cleanUrl = normalizeUrlOrSearch(url.trim(), _uiState.value.searchEngine)
        if (cleanTitle.isEmpty()) return
        val updated = listOf(BookmarkItem(title = cleanTitle, url = cleanUrl, collectionId = collectionId)) + _uiState.value.bookmarks
        _uiState.update { it.copy(bookmarks = updated) }
        viewModelScope.launch { repository.saveBookmarks(updated) }
    }

    fun removeBookmark(id: String) {
        val updated = _uiState.value.bookmarks.filterNot { it.id == id }
        _uiState.update { it.copy(bookmarks = updated) }
        viewModelScope.launch { repository.saveBookmarks(updated) }
    }

    fun createCollection(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val updated = _uiState.value.collections + BookmarkCollection(name = clean)
        _uiState.update { it.copy(collections = updated) }
        viewModelScope.launch { repository.saveCollections(updated) }
    }

    private fun recordHistory(title: String, url: String) {
        val current = _uiState.value.history
        val deduped = current.filterNot { it.url == url }
        val updated = (listOf(HistoryEntry(title = title, url = url)) + deduped).take(200)
        _uiState.update { it.copy(history = updated) }
        viewModelScope.launch { repository.saveHistory(updated) }
    }

    fun removeHistoryEntry(id: String) {
        val updated = _uiState.value.history.filterNot { it.id == id }
        _uiState.update { it.copy(history = updated) }
        viewModelScope.launch { repository.saveHistory(updated) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
        viewModelScope.launch { repository.saveHistory(emptyList()) }
    }

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearAutofill: Boolean,
        webView: WebView?
    ) {
        if (clearHistory) {
            _uiState.update { it.copy(history = emptyList()) }
            viewModelScope.launch { repository.saveHistory(emptyList()) }
        }
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (clearCache) {
            webView?.clearCache(true)
        }
        if (clearAutofill) {
            _uiState.update { it.copy(autofillProfiles = emptyList()) }
            viewModelScope.launch { repository.saveAutofillProfiles(emptyList()) }
        }
    }

    // --- Downloads ---

    fun enqueueDownload(url: String, fileName: String, mimeType: String) {
        val cleanName = fileName.ifBlank { "download-${System.currentTimeMillis()}" }
        runCatching {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(cleanName)
                setDescription("Momentum download")
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
            }
            dm.enqueue(req)
        }

        val item = DownloadItem(
            fileName = cleanName,
            url = url,
            sizeLabel = "Queued",
            progressPercent = 100,
            status = DownloadStatus.COMPLETED,
            mimeType = mimeType
        )
        val updated = listOf(item) + _uiState.value.downloads
        _uiState.update { it.copy(downloads = updated) }
        viewModelScope.launch { repository.saveDownloads(updated) }
    }

    fun toggleDownloadPause(id: String) {
        val updated = _uiState.value.downloads.map { dl ->
            if (dl.id == id) {
                when (dl.status) {
                    DownloadStatus.IN_PROGRESS -> dl.copy(status = DownloadStatus.PAUSED)
                    DownloadStatus.PAUSED -> dl.copy(status = DownloadStatus.IN_PROGRESS, progressPercent = 100, sizeLabel = "Completed")
                    else -> dl
                }
            } else {
                dl
            }
        }.map { dl ->
            if (dl.id == id && dl.status == DownloadStatus.IN_PROGRESS && dl.progressPercent == 100) {
                dl.copy(status = DownloadStatus.COMPLETED)
            } else dl
        }
        _uiState.update { it.copy(downloads = updated) }
        viewModelScope.launch { repository.saveDownloads(updated) }
    }

    fun removeDownload(id: String) {
        val updated = _uiState.value.downloads.filterNot { it.id == id }
        _uiState.update { it.copy(downloads = updated) }
        viewModelScope.launch { repository.saveDownloads(updated) }
    }

    fun clearDownloads() {
        _uiState.update { it.copy(downloads = emptyList()) }
        viewModelScope.launch { repository.saveDownloads(emptyList()) }
    }

    // --- On-Device Momentum AI ---

    fun analyzeActivePage(webView: WebView?) {
        val tab = _uiState.value.activeTab
        _uiState.update {
            it.copy(aiAnalysis = it.aiAnalysis.copy(isAnalyzing = true))
        }

        if (webView != null && tab.url != MOMENTUM_HOME_URL) {
            webView.evaluateJavascript(OnDeviceAiEngine.EXTRACT_PAGE_DOM_JS) { raw ->
                val dom = OnDeviceAiEngine.parseDomJson(raw ?: "")
                val text = dom.fullText.ifBlank { tab.extractedText }
                val headings = dom.headings.ifEmpty { tab.extractedHeadings }
                val title = dom.title.ifBlank { tab.title }
                updateTabById(tab.id) {
                    it.copy(extractedTitle = title, extractedText = text, extractedHeadings = headings)
                }
                publishSummary(tab.url, title, text, headings)
            }
        } else {
            val fallbackText = tab.extractedText.ifBlank {
                "Momentum is focused on fast, private browsing with Spaces, Visual Tab Groups, WebExtensions, and decentralized #Net content addressing."
            }
            publishSummary(tab.url, tab.title, fallbackText, tab.extractedHeadings)
        }
    }

    private fun publishSummary(url: String, title: String, text: String, headings: List<String>) {
        val (points, words, mins) = OnDeviceAiEngine.summarizeKeyPoints(title, url, text, headings)
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { "momentum.browser" }
        _uiState.update {
            it.copy(
                aiAnalysis = it.aiAnalysis.copy(
                    sourceUrl = url,
                    sourceHost = host,
                    keyPoints = points,
                    wordCount = words,
                    readMinutes = mins,
                    isAnalyzing = false
                )
            )
        }
    }

    fun askMomentumAi(question: String) {
        val clean = question.trim()
        if (clean.isEmpty()) return
        val tab = _uiState.value.activeTab
        val host = runCatching { Uri.parse(tab.url).host }.getOrNull().orEmpty().ifBlank { "momentum.browser" }
        val answer = OnDeviceAiEngine.answerQuestionFromPage(
            question = clean,
            pageTitle = tab.extractedTitle.ifBlank { tab.title },
            pageUrl = tab.url,
            extractedText = tab.extractedText,
            headings = tab.extractedHeadings
        )
        _uiState.update {
            it.copy(
                aiAnalysis = it.aiAnalysis.copy(
                    messages = it.aiAnalysis.messages +
                        AiChatMessage(isUser = true, text = clean) +
                        AiChatMessage(isUser = false, text = answer, sourceHost = host)
                )
            )
        }
    }

    // --- Find in Page, Reader Mode, Translation & Site Permissions ---

    fun setFindInPageActive(active: Boolean, webView: WebView?) {
        if (!active) {
            webView?.clearMatches()
        }
        _uiState.update {
            it.copy(
                findInPageActive = active,
                findQuery = if (active) it.findQuery else "",
                findMatchIndex = 0,
                findMatchTotal = 0
            )
        }
    }

    fun updateFindQuery(query: String, webView: WebView?) {
        _uiState.update { it.copy(findQuery = query) }
        if (query.isBlank()) {
            webView?.clearMatches()
            _uiState.update { it.copy(findMatchIndex = 0, findMatchTotal = 0) }
        } else {
            webView?.findAllAsync(query)
        }
    }

    fun onFindResults(activeMatchOrdinal: Int, numberOfMatches: Int) {
        _uiState.update {
            it.copy(
                findMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                findMatchTotal = numberOfMatches
            )
        }
    }

    fun toggleReaderMode(active: Boolean, webView: WebView?) {
        if (active && webView != null) {
            webView.evaluateJavascript(OnDeviceAiEngine.EXTRACT_PAGE_DOM_JS) { raw ->
                val dom = OnDeviceAiEngine.parseDomJson(raw ?: "")
                if (dom.fullText.isNotBlank()) {
                    updateActiveTab {
                        it.copy(
                            extractedTitle = dom.title.ifBlank { it.title },
                            extractedText = dom.fullText,
                            extractedHeadings = dom.headings
                        )
                    }
                }
            }
        }
        _uiState.update { it.copy(readerModeActive = active) }
    }

    fun adjustReaderFontSize(delta: Int) {
        _uiState.update {
            it.copy(readerFontSizeSp = (it.readerFontSizeSp + delta).coerceIn(13, 26))
        }
    }

    fun openTranslation(targetLang: String = _uiState.value.translationTargetLang, webView: WebView?) {
        if (webView != null) {
            webView.evaluateJavascript(OnDeviceAiEngine.EXTRACT_PAGE_DOM_JS) { raw ->
                val dom = OnDeviceAiEngine.parseDomJson(raw ?: "")
                val text = dom.fullText.ifBlank { _uiState.value.activeTab.extractedText }
                val preview = OnDeviceAiEngine.buildTranslatedPreview(text, targetLang)
                _uiState.update {
                    it.copy(
                        translationBannerVisible = true,
                        translationTargetLang = targetLang,
                        translatedPreview = preview
                    )
                }
            }
        } else {
            val preview = OnDeviceAiEngine.buildTranslatedPreview(_uiState.value.activeTab.extractedText, targetLang)
            _uiState.update {
                it.copy(
                    translationBannerVisible = true,
                    translationTargetLang = targetLang,
                    translatedPreview = preview
                )
            }
        }
    }

    fun dismissTranslation() {
        _uiState.update { it.copy(translationBannerVisible = false) }
    }

    fun permissionsForHost(host: String): SitePermissionState =
        _uiState.value.sitePermissions[host] ?: SitePermissionState(
            allowJavaScript = _uiState.value.javaScriptEnabled,
            allowCookies = !_uiState.value.activeTab.isIncognito
        )

    fun updateHostPermissions(host: String, state: SitePermissionState) {
        _uiState.update {
            it.copy(sitePermissions = it.sitePermissions + (host to state))
        }
    }

    // --- Passwords & Autofill ---

    fun addCredential(domain: String, username: String, password: String) {
        if (domain.isBlank() || username.isBlank() || password.isBlank()) return
        val updated = listOf(SavedCredential(domain = domain.trim(), username = username.trim(), password = password)) + _uiState.value.credentials
        _uiState.update { it.copy(credentials = updated) }
        viewModelScope.launch { repository.saveCredentials(updated) }
    }

    fun removeCredential(id: String) {
        val updated = _uiState.value.credentials.filterNot { it.id == id }
        _uiState.update { it.copy(credentials = updated) }
        viewModelScope.launch { repository.saveCredentials(updated) }
    }

    fun addAutofillProfile(label: String, fullName: String, email: String, phone: String, address: String, paymentHint: String) {
        if (label.isBlank() || fullName.isBlank()) return
        val updated = _uiState.value.autofillProfiles + AutofillProfile(
            label = label.trim(),
            fullName = fullName.trim(),
            email = email.trim(),
            phone = phone.trim(),
            address = address.trim(),
            paymentHint = paymentHint.trim()
        )
        _uiState.update { it.copy(autofillProfiles = updated) }
        viewModelScope.launch { repository.saveAutofillProfiles(updated) }
    }

    fun removeAutofillProfile(id: String) {
        val updated = _uiState.value.autofillProfiles.filterNot { it.id == id }
        _uiState.update { it.copy(autofillProfiles = updated) }
        viewModelScope.launch { repository.saveAutofillProfiles(updated) }
    }

    fun injectAutofillIntoWebView(profile: AutofillProfile, webView: WebView?) {
        val js = OnDeviceAiEngine.buildAutofillInjectionJs(
            profile.fullName,
            profile.email,
            profile.phone,
            profile.address
        )
        webView?.evaluateJavascript(js, null)
    }

    // --- Extensions & #Net ---

    fun toggleExtension(id: String) {
        val updated = _uiState.value.extensions.map { ext ->
            if (ext.id == id) ext.copy(enabled = !ext.enabled) else ext
        }
        _uiState.update { it.copy(extensions = updated) }
        viewModelScope.launch { repository.saveExtensions(updated) }
    }

    fun addCustomUserscript(name: String, description: String, scriptBody: String) {
        val cleanName = name.trim()
        val cleanScript = scriptBody.trim()
        if (cleanName.isEmpty() || cleanScript.isEmpty()) return

        val newExt = BrowserExtension(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            version = "1.0.0",
            description = description.trim().ifEmpty { "Custom user-installed Momentum userscript." },
            category = "Userscript",
            apis = listOf("chrome.tabs.executeScript", "chrome.runtime"),
            enabled = true,
            isBuiltIn = false,
            scriptBody = cleanScript
        )
        val updated = _uiState.value.extensions + newExt
        _uiState.update { it.copy(extensions = updated) }
        viewModelScope.launch { repository.saveExtensions(updated) }
    }

    fun removeCustomExtension(id: String) {
        val updated = _uiState.value.extensions.filterNot { it.id == id && !it.isBuiltIn }
        _uiState.update { it.copy(extensions = updated) }
        viewModelScope.launch { repository.saveExtensions(updated) }
    }

    fun addAgentNode(label: String, baseUrl: String) {
        val cleanLabel = label.trim()
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanLabel.isEmpty() || cleanUrl.isEmpty()) return
        val normalizedUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) cleanUrl else "http://$cleanUrl"
        val updated = _uiState.value.agents + ContentAgentNode(label = cleanLabel, baseUrl = normalizedUrl, enabled = true, lastStatus = "Added")
        _uiState.update { it.copy(agents = updated) }
        viewModelScope.launch { repository.saveAgents(updated) }
    }

    fun toggleAgentNode(id: String) {
        val updated = _uiState.value.agents.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        _uiState.update { it.copy(agents = updated) }
        viewModelScope.launch { repository.saveAgents(updated) }
    }

    fun removeAgentNode(id: String) {
        val updated = _uiState.value.agents.filterNot { it.id == id }
        _uiState.update { it.copy(agents = updated) }
        viewModelScope.launch { repository.saveAgents(updated) }
    }

    fun testAgentNode(id: String) {
        val agent = _uiState.value.agents.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(agents = state.agents.map { if (it.id == id) it.copy(lastStatus = "Testing...") else it })
        }
        viewModelScope.launch {
            val result = repository.testAgentHealth(agent)
            val updated = _uiState.value.agents.map { if (it.id == id) it.copy(lastStatus = result) else it }
            _uiState.update { it.copy(agents = updated) }
            repository.saveAgents(updated)
        }
    }

    fun testContentUrn(urn: String) {
        val trimmed = urn.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(isTestingUrn = true, urnTestStatus = "Polling #Net agents...") }
        viewModelScope.launch {
            val status = when (val res = repository.resolveContentAddress(trimmed, _uiState.value.agents)) {
                is ContentAddressResolution.VerifiedContent ->
                    "Verified SHA-256 (${res.digestHex.take(12)}...) via ${res.agentLabel}"
                is ContentAddressResolution.Failure -> res.message
            }
            _uiState.update { it.copy(isTestingUrn = false, urnTestStatus = status) }
        }
    }

    // --- Settings Toggles ---

    fun updateSearchEngine(engine: SearchEngine) {
        _uiState.update { it.copy(searchEngine = engine) }
        persistSettings()
    }

    fun toggleDarkTheme(enabled: Boolean) {
        _uiState.update { it.copy(darkTheme = enabled) }
        persistSettings()
    }

    fun toggleAmoledBlack(enabled: Boolean) {
        _uiState.update { it.copy(amoledBlack = enabled) }
        persistSettings()
    }

    fun updateTextScale(scale: Float) {
        _uiState.update { it.copy(textScale = scale.coerceIn(0.85f, 1.3f)) }
        persistSettings()
    }

    fun toggleReduceMotion(enabled: Boolean) {
        _uiState.update { it.copy(reduceMotion = enabled) }
        persistSettings()
    }

    fun toggleTrackerShield(enabled: Boolean) {
        _uiState.update { it.copy(trackerShieldEnabled = enabled) }
        persistSettings()
    }

    fun toggleSafeBrowsing(enabled: Boolean) {
        _uiState.update { it.copy(safeBrowsingEnabled = enabled) }
        persistSettings()
    }

    fun toggleJavaScript(enabled: Boolean) {
        _uiState.update { it.copy(javaScriptEnabled = enabled) }
        persistSettings()
    }

    fun toggleAskBeforeDownload(enabled: Boolean) {
        _uiState.update { it.copy(askBeforeDownload = enabled) }
        persistSettings()
    }

    fun isTrackerShieldExtensionActive(): Boolean {
        val state = _uiState.value
        val shieldExt = state.extensions.firstOrNull { it.id == "ext_tracker_shield" }?.enabled ?: true
        return state.trackerShieldEnabled && shieldExt
    }

    fun isCleanUrlsEnabled(): Boolean =
        _uiState.value.extensions.firstOrNull { it.id == "ext_clean_urls" }?.enabled ?: true

    fun activeInjectionScripts(): List<String> =
        _uiState.value.extensions.filter { it.enabled && it.scriptBody.isNotBlank() }.map { it.scriptBody }

    private fun persistSettings() {
        val s = _uiState.value
        viewModelScope.launch {
            repository.saveSettings(
                searchEngine = s.searchEngine,
                darkTheme = s.darkTheme,
                amoledBlack = s.amoledBlack,
                textScale = s.textScale,
                reduceMotion = s.reduceMotion,
                trackerShieldEnabled = s.trackerShieldEnabled,
                safeBrowsingEnabled = s.safeBrowsingEnabled,
                javaScriptEnabled = s.javaScriptEnabled,
                askBeforeDownload = s.askBeforeDownload
            )
        }
    }

    private fun updateActiveTab(transform: (BrowserTab) -> BrowserTab) {
        updateTabById(_uiState.value.activeTab.id, transform)
    }

    private fun updateTabById(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun normalizeUrlOrSearch(input: String, engine: SearchEngine): String {
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) ||
            input.startsWith("about:", ignoreCase = true)
        ) {
            return input
        }
        val looksLikeDomain = !input.contains(' ') && input.contains('.') && !input.startsWith(".")
        return if (looksLikeDomain) "https://$input" else engine.queryTemplate.replace("%s", Uri.encode(input))
    }
}
