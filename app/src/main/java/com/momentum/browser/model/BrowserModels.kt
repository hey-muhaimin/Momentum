package com.momentum.browser.model

import java.util.UUID

enum class MomentumDestination {
    HOME,
    TABS,
    LIBRARY,
    AI,
    PRIVACY
}

enum class SubScreen {
    NONE,
    BROWSER,
    SPACES,
    SETTINGS,
    EXTENSIONS,
    NETWORK_NODES,
    PASSWORDS,
    AUTOFILL,
    ABOUT
}

enum class LibrarySection {
    BOOKMARKS,
    COLLECTIONS,
    HISTORY,
    DOWNLOADS
}

enum class SearchEngine(val label: String, val queryTemplate: String, val homeHint: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "google.com"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "search.brave.com")
}

data class Space(
    val id: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val isBuiltIn: Boolean = false
)

data class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long,
    val spaceId: String,
    val isCollapsed: Boolean = false
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val spaceId: String = "space_work",
    val groupId: String? = null,
    val url: String = MOMENTUM_HOME_URL,
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val extractedTitle: String = "",
    val extractedText: String = "",
    val extractedHeadings: List<String> = emptyList(),
    val resolvedHtml: String? = null,
    val contentAddressStatus: String? = null,
    val reloadNonce: Int = 0,
    val lastVisitedMs: Long = System.currentTimeMillis()
)

data class ShortcutItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val badgeText: String,
    val badgeColorHex: Long
)

data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val collectionId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BookmarkCollection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accentHex: Long = 0xFFC5FF45
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val visitedAtMs: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val url: String,
    val sizeLabel: String,
    val progressPercent: Int,
    val status: DownloadStatus,
    val mimeType: String = "application/octet-stream",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class SavedCredential(
    val id: String = UUID.randomUUID().toString(),
    val domain: String,
    val username: String,
    val password: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class AutofillProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val address: String,
    val paymentHint: String = ""
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val sourceHost: String = ""
)

data class AiPageAnalysis(
    val sourceUrl: String = "",
    val sourceHost: String = "",
    val keyPoints: List<String> = emptyList(),
    val wordCount: Int = 0,
    val readMinutes: Int = 1,
    val isAnalyzing: Boolean = false,
    val messages: List<AiChatMessage> = emptyList()
)

data class SitePermissionState(
    val allowJavaScript: Boolean = true,
    val allowCookies: Boolean = true,
    val allowLocation: Boolean = false,
    val allowCamera: Boolean = false,
    val allowMicrophone: Boolean = false
)

data class BrowserExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val category: String,
    val apis: List<String>,
    val enabled: Boolean,
    val isBuiltIn: Boolean = true,
    val scriptBody: String = ""
)

data class ContentAgentNode(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val lastStatus: String = "Idle"
)

data class MomentumUiState(
    val hasCompletedOnboarding: Boolean = false,
    val destination: MomentumDestination = MomentumDestination.HOME,
    val subScreen: SubScreen = SubScreen.NONE,
    val librarySection: LibrarySection = LibrarySection.BOOKMARKS,
    val spaces: List<Space> = defaultSpaces(),
    val activeSpaceId: String = "space_work",
    val tabGroups: List<TabGroup> = defaultTabGroups(),
    val tabs: List<BrowserTab> = defaultTabs(),
    val activeTabId: String = "tab_design",
    val isTabGridMode: Boolean = false,
    val shortcuts: List<ShortcutItem> = defaultShortcuts(),
    val bookmarks: List<BookmarkItem> = defaultBookmarks(),
    val collections: List<BookmarkCollection> = defaultCollections(),
    val history: List<HistoryEntry> = defaultHistory(),
    val downloads: List<DownloadItem> = defaultDownloads(),
    val credentials: List<SavedCredential> = defaultCredentials(),
    val autofillProfiles: List<AutofillProfile> = defaultAutofillProfiles(),
    val extensions: List<BrowserExtension> = defaultExtensions(),
    val agents: List<ContentAgentNode> = defaultAgents(),
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val darkTheme: Boolean = true,
    val amoledBlack: Boolean = true,
    val textScale: Float = 1.0f,
    val reduceMotion: Boolean = false,
    val trackerShieldEnabled: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val askBeforeDownload: Boolean = false,
    val sitePermissions: Map<String, SitePermissionState> = emptyMap(),
    val blockedTrackersCount: Int = 14,
    val strippedParamsCount: Int = 6,
    val aiAnalysis: AiPageAnalysis = AiPageAnalysis(),
    val findInPageActive: Boolean = false,
    val findQuery: String = "",
    val findMatchIndex: Int = 0,
    val findMatchTotal: Int = 0,
    val readerModeActive: Boolean = false,
    val readerFontSizeSp: Int = 17,
    val translationBannerVisible: Boolean = false,
    val translationTargetLang: String = "English",
    val translatedPreview: String? = null,
    val urnTestStatus: String? = null,
    val isTestingUrn: Boolean = false,
    val userDisplayName: String = "Muhaimin",
    val userEmail: String = "m.muhaimin@gmail.com"
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val activeSpace: Space
        get() = spaces.firstOrNull { it.id == activeSpaceId } ?: spaces.first()
}

const val MOMENTUM_HOME_URL = "momentum://home"

fun defaultSpaces(): List<Space> = listOf(
    Space(id = "space_work", name = "Work", colorHex = 0xFF4ADE80, iconKey = "work", isBuiltIn = true),
    Space(id = "space_research", name = "Research", colorHex = 0xFF8B7CFF, iconKey = "research", isBuiltIn = true),
    Space(id = "space_personal", name = "Personal", colorHex = 0xFFFB923C, iconKey = "personal", isBuiltIn = true)
)

fun defaultTabGroups(): List<TabGroup> = listOf(
    TabGroup(id = "grp_dev", name = "Momentum Dev", colorHex = 0xFFC5FF45, spaceId = "space_work"),
    TabGroup(id = "grp_reading", name = "2026 Reading", colorHex = 0xFF8B7CFF, spaceId = "space_research")
)

fun defaultTabs(): List<BrowserTab> = listOf(
    BrowserTab(
        id = "tab_design",
        spaceId = "space_work",
        groupId = "grp_dev",
        url = "https://developer.android.com/design/ui/mobile",
        title = "DesignSystems — Designing for the next web"
    ),
    BrowserTab(
        id = "tab_github",
        spaceId = "space_work",
        groupId = "grp_dev",
        url = "https://github.com/hey-muhaimin/Momentum",
        title = "hey-muhaimin/Momentum"
    ),
    BrowserTab(
        id = "tab_wiki",
        spaceId = "space_research",
        groupId = "grp_reading",
        url = "https://en.m.wikipedia.org/wiki/Web_browser",
        title = "Web browser - Wikipedia"
    ),
    BrowserTab(
        id = "tab_home_personal",
        spaceId = "space_personal",
        url = MOMENTUM_HOME_URL,
        title = "New Tab"
    )
)

fun defaultShortcuts(): List<ShortcutItem> = listOf(
    ShortcutItem("sc_yt", "YouTube", "https://m.youtube.com", "YT", 0xFFEF4444),
    ShortcutItem("sc_gh", "GitHub", "https://github.com", "GH", 0xFF334155),
    ShortcutItem("sc_x", "X", "https://x.com", "X", 0xFF1F2937),
    ShortcutItem("sc_google", "Google", "https://www.google.com", "G", 0xFF3B82F6),
    ShortcutItem("sc_reddit", "Reddit", "https://www.reddit.com", "R", 0xFFF97316),
    ShortcutItem("sc_wiki", "Wikipedia", "https://en.m.wikipedia.org", "W", 0xFF475569),
    ShortcutItem("sc_mdn", "MDN Docs", "https://developer.mozilla.org", "M", 0xFF0EA5E9)
)

fun defaultCollections(): List<BookmarkCollection> = listOf(
    BookmarkCollection("col_design", "Design inspiration", 0xFFC5FF45),
    BookmarkCollection("col_chromium", "Chromium & Android", 0xFF8B7CFF)
)

fun defaultBookmarks(): List<BookmarkItem> = listOf(
    BookmarkItem("bm_yt", "YouTube", "https://m.youtube.com"),
    BookmarkItem("bm_gh", "GitHub", "https://github.com", collectionId = "col_chromium"),
    BookmarkItem("bm_mdn", "MDN Web Docs", "https://developer.mozilla.org", collectionId = "col_design"),
    BookmarkItem("bm_a11y", "Material Design 3", "https://m3.material.io", collectionId = "col_design")
)

fun defaultHistory(): List<HistoryEntry> {
    val now = System.currentTimeMillis()
    val yesterday = now - 26L * 60L * 60L * 1000L
    return listOf(
        HistoryEntry("h_1", "hey-muhaimin/Momentum", "https://github.com/hey-muhaimin/Momentum", now - 25 * 60_000L),
        HistoryEntry("h_2", "Material Design 3", "https://m3.material.io", now - 90 * 60_000L),
        HistoryEntry("h_3", "Android Developers — Mobile UI", "https://developer.android.com/design/ui/mobile", now - 160 * 60_000L),
        HistoryEntry("h_4", "Web browser - Wikipedia", "https://en.m.wikipedia.org/wiki/Web_browser", yesterday)
    )
}

fun defaultDownloads(): List<DownloadItem> = listOf(
    DownloadItem("dl_1", "momentum-ui-v1.0.apk", "https://github.com/hey-muhaimin/Momentum/releases", "12.4 MB", 100, DownloadStatus.COMPLETED, "application/vnd.android.package-archive"),
    DownloadItem("dl_2", "design-systems-guide.pdf", "https://m3.material.io/guide.pdf", "3.8 MB", 72, DownloadStatus.IN_PROGRESS, "application/pdf"),
    DownloadItem("dl_3", "wallpapers.zip", "https://momentum.browser/assets/wallpapers.zip", "5.1 MB", 100, DownloadStatus.COMPLETED, "application/zip")
)

fun defaultCredentials(): List<SavedCredential> = listOf(
    SavedCredential("cred_gh", "github.com", "hey-muhaimin", "momentum-token-2026")
)

fun defaultAutofillProfiles(): List<AutofillProfile> = listOf(
    AutofillProfile(
        id = "af_primary",
        label = "Primary Profile",
        fullName = "Muhaimin",
        email = "m.muhaimin@gmail.com",
        phone = "+1 (555) 014-2026",
        address = "410 Momentum Way, Suite 200",
        paymentHint = "Visa •••• 4242"
    )
)

fun defaultAgents(): List<ContentAgentNode> = listOf(
    ContentAgentNode(
        id = "agent_local",
        label = "Local #Net Agent",
        baseUrl = "http://127.0.0.1:8080",
        enabled = true,
        lastStatus = "Configured"
    )
)

fun defaultExtensions(): List<BrowserExtension> = listOf(
    BrowserExtension(
        id = "ext_tracker_shield",
        name = "Momentum Tracker Shield",
        version = "1.4.0",
        description = "Blocks common analytics, ad beacons, and third-party fingerprinting scripts at request time.",
        category = "Privacy",
        apis = listOf("chrome.webRequest", "chrome.declarativeNetRequest"),
        enabled = true,
        isBuiltIn = true
    ),
    BrowserExtension(
        id = "ext_clean_urls",
        name = "CleanURLs Stripper",
        version = "1.2.1",
        description = "Removes tracking query parameters (utm_*, fbclid, gclid, mc_cid) before navigating.",
        category = "Privacy",
        apis = listOf("chrome.webRequest", "chrome.tabs"),
        enabled = true,
        isBuiltIn = true
    ),
    BrowserExtension(
        id = "ext_dark_reader",
        name = "Adaptive Page Dimmer",
        version = "1.0.8",
        description = "Injects gentle dark-mode contrast rules into bright web pages using content scripts.",
        category = "Appearance",
        apis = listOf("chrome.scripting", "chrome.tabs.insertCSS"),
        enabled = false,
        isBuiltIn = true,
        scriptBody = """
            (function() {
              if (document.getElementById('momentum-dark-style')) return;
              var style = document.createElement('style');
              style.id = 'momentum-dark-style';
              style.textContent = 'html { filter: invert(0.9) hue-rotate(180deg); background: #111 !important; } img, video, picture { filter: invert(1) hue-rotate(180deg); }';
              (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
    ),
    BrowserExtension(
        id = "ext_userscript_bridge",
        name = "Momentum Userscript Bridge",
        version = "2.0.0",
        description = "Exposes Momentum chrome.runtime metadata and injects enabled userscripts after DOM ready.",
        category = "Developer",
        apis = listOf("chrome.runtime", "chrome.storage", "chrome.tabs.executeScript"),
        enabled = true,
        isBuiltIn = true,
        scriptBody = """
            (function() {
              window.__momentumExtensionBridge = { runtime: 'Momentum', webExtensions: true };
            })();
        """.trimIndent()
    )
)
