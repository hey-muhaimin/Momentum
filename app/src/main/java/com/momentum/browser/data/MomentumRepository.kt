package com.momentum.browser.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.momentum.browser.model.AutofillProfile
import com.momentum.browser.model.BookmarkCollection
import com.momentum.browser.model.BookmarkItem
import com.momentum.browser.model.BrowserExtension
import com.momentum.browser.model.ContentAgentNode
import com.momentum.browser.model.DownloadItem
import com.momentum.browser.model.DownloadStatus
import com.momentum.browser.model.HistoryEntry
import com.momentum.browser.model.SavedCredential
import com.momentum.browser.model.SearchEngine
import com.momentum.browser.model.ShortcutItem
import com.momentum.browser.model.Space
import com.momentum.browser.model.TabGroup
import com.momentum.browser.model.defaultAgents
import com.momentum.browser.model.defaultAutofillProfiles
import com.momentum.browser.model.defaultBookmarks
import com.momentum.browser.model.defaultCollections
import com.momentum.browser.model.defaultCredentials
import com.momentum.browser.model.defaultDownloads
import com.momentum.browser.model.defaultExtensions
import com.momentum.browser.model.defaultHistory
import com.momentum.browser.model.defaultShortcuts
import com.momentum.browser.model.defaultSpaces
import com.momentum.browser.model.defaultTabGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private val Context.momentumDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentum_browser_prefs"
)

data class PersistedBrowserData(
    val hasCompletedOnboarding: Boolean,
    val spaces: List<Space>,
    val activeSpaceId: String,
    val tabGroups: List<TabGroup>,
    val shortcuts: List<ShortcutItem>,
    val bookmarks: List<BookmarkItem>,
    val collections: List<BookmarkCollection>,
    val history: List<HistoryEntry>,
    val downloads: List<DownloadItem>,
    val credentials: List<SavedCredential>,
    val autofillProfiles: List<AutofillProfile>,
    val extensions: List<BrowserExtension>,
    val agents: List<ContentAgentNode>,
    val searchEngine: SearchEngine,
    val darkTheme: Boolean,
    val amoledBlack: Boolean,
    val textScale: Float,
    val reduceMotion: Boolean,
    val trackerShieldEnabled: Boolean,
    val safeBrowsingEnabled: Boolean,
    val javaScriptEnabled: Boolean,
    val askBeforeDownload: Boolean,
    val blockedTrackersCount: Int,
    val strippedParamsCount: Int
)

sealed class ContentAddressResolution {
    data class VerifiedContent(
        val urn: String,
        val agentLabel: String,
        val htmlBody: String,
        val digestHex: String
    ) : ContentAddressResolution()

    data class Failure(
        val urn: String,
        val message: String
    ) : ContentAddressResolution()
}

class MomentumRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SPACES = stringPreferencesKey("spaces_json")
        val ACTIVE_SPACE = stringPreferencesKey("active_space_id")
        val TAB_GROUPS = stringPreferencesKey("tab_groups_json")
        val SHORTCUTS = stringPreferencesKey("shortcuts_json")
        val BOOKMARKS = stringPreferencesKey("bookmarks_json")
        val COLLECTIONS = stringPreferencesKey("collections_json")
        val HISTORY = stringPreferencesKey("history_json")
        val DOWNLOADS = stringPreferencesKey("downloads_json")
        val CREDENTIALS = stringPreferencesKey("credentials_json")
        val AUTOFILL = stringPreferencesKey("autofill_json")
        val EXTENSIONS = stringPreferencesKey("extensions_json")
        val AGENTS = stringPreferencesKey("agents_json")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val TRACKER_SHIELD = booleanPreferencesKey("tracker_shield")
        val SAFE_BROWSING = booleanPreferencesKey("safe_browsing")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val ASK_BEFORE_DL = booleanPreferencesKey("ask_before_download")
        val BLOCKED_COUNT = intPreferencesKey("blocked_trackers_count")
        val STRIPPED_COUNT = intPreferencesKey("stripped_params_count")
    }

    val persistedData: Flow<PersistedBrowserData> = context.momentumDataStore.data.map { prefs ->
        PersistedBrowserData(
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
            spaces = prefs[Keys.SPACES]?.let(::decodeSpaces) ?: defaultSpaces(),
            activeSpaceId = prefs[Keys.ACTIVE_SPACE] ?: "space_work",
            tabGroups = prefs[Keys.TAB_GROUPS]?.let(::decodeTabGroups) ?: defaultTabGroups(),
            shortcuts = prefs[Keys.SHORTCUTS]?.let(::decodeShortcuts) ?: defaultShortcuts(),
            bookmarks = prefs[Keys.BOOKMARKS]?.let(::decodeBookmarks) ?: defaultBookmarks(),
            collections = prefs[Keys.COLLECTIONS]?.let(::decodeCollections) ?: defaultCollections(),
            history = prefs[Keys.HISTORY]?.let(::decodeHistory) ?: defaultHistory(),
            downloads = prefs[Keys.DOWNLOADS]?.let(::decodeDownloads) ?: defaultDownloads(),
            credentials = prefs[Keys.CREDENTIALS]?.let(::decodeCredentials) ?: defaultCredentials(),
            autofillProfiles = prefs[Keys.AUTOFILL]?.let(::decodeAutofill) ?: defaultAutofillProfiles(),
            extensions = prefs[Keys.EXTENSIONS]?.let(::mergeExtensionsWithDefaults) ?: defaultExtensions(),
            agents = prefs[Keys.AGENTS]?.let(::decodeAgents) ?: defaultAgents(),
            searchEngine = prefs[Keys.SEARCH_ENGINE]?.let { name ->
                SearchEngine.entries.firstOrNull { it.name == name }
            } ?: SearchEngine.GOOGLE,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: true,
            textScale = prefs[Keys.TEXT_SCALE] ?: 1.0f,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            trackerShieldEnabled = prefs[Keys.TRACKER_SHIELD] ?: true,
            safeBrowsingEnabled = prefs[Keys.SAFE_BROWSING] ?: true,
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            askBeforeDownload = prefs[Keys.ASK_BEFORE_DL] ?: false,
            blockedTrackersCount = prefs[Keys.BLOCKED_COUNT] ?: 14,
            strippedParamsCount = prefs[Keys.STRIPPED_COUNT] ?: 6
        )
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.momentumDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun saveSpaces(spaces: List<Space>, activeSpaceId: String) {
        context.momentumDataStore.edit {
            it[Keys.SPACES] = encodeSpaces(spaces)
            it[Keys.ACTIVE_SPACE] = activeSpaceId
        }
    }

    suspend fun saveTabGroups(groups: List<TabGroup>) {
        context.momentumDataStore.edit { it[Keys.TAB_GROUPS] = encodeTabGroups(groups) }
    }

    suspend fun saveShortcuts(items: List<ShortcutItem>) {
        context.momentumDataStore.edit { it[Keys.SHORTCUTS] = encodeShortcuts(items) }
    }

    suspend fun saveBookmarks(items: List<BookmarkItem>) {
        context.momentumDataStore.edit { it[Keys.BOOKMARKS] = encodeBookmarks(items) }
    }

    suspend fun saveCollections(items: List<BookmarkCollection>) {
        context.momentumDataStore.edit { it[Keys.COLLECTIONS] = encodeCollections(items) }
    }

    suspend fun saveHistory(items: List<HistoryEntry>) {
        context.momentumDataStore.edit { it[Keys.HISTORY] = encodeHistory(items.take(200)) }
    }

    suspend fun saveDownloads(items: List<DownloadItem>) {
        context.momentumDataStore.edit { it[Keys.DOWNLOADS] = encodeDownloads(items) }
    }

    suspend fun saveCredentials(items: List<SavedCredential>) {
        context.momentumDataStore.edit { it[Keys.CREDENTIALS] = encodeCredentials(items) }
    }

    suspend fun saveAutofillProfiles(items: List<AutofillProfile>) {
        context.momentumDataStore.edit { it[Keys.AUTOFILL] = encodeAutofill(items) }
    }

    suspend fun saveExtensions(items: List<BrowserExtension>) {
        context.momentumDataStore.edit { it[Keys.EXTENSIONS] = encodeExtensions(items) }
    }

    suspend fun saveAgents(items: List<ContentAgentNode>) {
        context.momentumDataStore.edit { it[Keys.AGENTS] = encodeAgents(items) }
    }

    suspend fun saveShieldCounters(blocked: Int, stripped: Int) {
        context.momentumDataStore.edit {
            it[Keys.BLOCKED_COUNT] = blocked
            it[Keys.STRIPPED_COUNT] = stripped
        }
    }

    suspend fun saveSettings(
        searchEngine: SearchEngine,
        darkTheme: Boolean,
        amoledBlack: Boolean,
        textScale: Float,
        reduceMotion: Boolean,
        trackerShieldEnabled: Boolean,
        safeBrowsingEnabled: Boolean,
        javaScriptEnabled: Boolean,
        askBeforeDownload: Boolean
    ) {
        context.momentumDataStore.edit { prefs ->
            prefs[Keys.SEARCH_ENGINE] = searchEngine.name
            prefs[Keys.DARK_THEME] = darkTheme
            prefs[Keys.AMOLED_BLACK] = amoledBlack
            prefs[Keys.TEXT_SCALE] = textScale
            prefs[Keys.REDUCE_MOTION] = reduceMotion
            prefs[Keys.TRACKER_SHIELD] = trackerShieldEnabled
            prefs[Keys.SAFE_BROWSING] = safeBrowsingEnabled
            prefs[Keys.JAVASCRIPT_ENABLED] = javaScriptEnabled
            prefs[Keys.ASK_BEFORE_DL] = askBeforeDownload
        }
    }

    suspend fun testAgentHealth(agent: ContentAgentNode): String = withContext(Dispatchers.IO) {
        val base = agent.baseUrl.trimEnd('/')
        runCatching {
            val conn = (URL(base).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "HEAD"
            }
            val code = conn.responseCode
            conn.disconnect()
            "Reachable (HTTP $code)"
        }.getOrElse { err ->
            "Unreachable (${err.javaClass.simpleName})"
        }
    }

    suspend fun resolveContentAddress(
        urn: String,
        agents: List<ContentAgentNode>
    ): ContentAddressResolution = withContext(Dispatchers.IO) {
        val trimmed = urn.trim()
        val enabledAgents = agents.filter { it.enabled }
        if (enabledAgents.isEmpty()) {
            return@withContext ContentAddressResolution.Failure(
                urn = trimmed,
                message = "No enabled #Net agent nodes configured. Add an agent in Settings -> #Net Nodes."
            )
        }

        if (trimmed.startsWith("hash://sha256/", ignoreCase = true)) {
            val expectedHash = trimmed.removePrefix("hash://sha256/")
                .substringBefore('/')
                .substringBefore('?')
                .lowercase()

            if (!expectedHash.matches(Regex("^[0-9a-f]{64}$"))) {
                return@withContext ContentAddressResolution.Failure(
                    urn = trimmed,
                    message = "Invalid SHA-256 URN digest. Expected 64 lowercase hex characters."
                )
            }

            val errors = mutableListOf<String>()
            for (agent in enabledAgents) {
                val base = agent.baseUrl.trimEnd('/')
                val endpoint = "$base/hash/sha256/$expectedHash"
                val attempt = runCatching {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 6000
                        requestMethod = "GET"
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        conn.disconnect()
                        error("HTTP $code")
                    }
                    val bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    val actualHash = sha256Hex(bytes)
                    if (actualHash != expectedHash) {
                        error("SHA-256 mismatch (got ${actualHash.take(12)}...)")
                    }
                    val text = bytes.toString(Charsets.UTF_8)
                    ContentAddressResolution.VerifiedContent(
                        urn = trimmed,
                        agentLabel = agent.label,
                        htmlBody = wrapResolvedContentHtml(trimmed, agent.label, actualHash, text),
                        digestHex = actualHash
                    )
                }
                attempt.onSuccess { return@withContext it }
                attempt.onFailure { errors += "${agent.label}: ${it.message}" }
            }

            return@withContext ContentAddressResolution.Failure(
                urn = trimmed,
                message = "404 / Unverified across ${enabledAgents.size} agent(s): ${errors.joinToString("; ")}"
            )
        }

        if (trimmed.startsWith("signed://", ignoreCase = true)) {
            return@withContext ContentAddressResolution.Failure(
                urn = trimmed,
                message = "signed:// URN requires secp256r1 verification from a live #Net agent (${enabledAgents.size} configured)."
            )
        }

        ContentAddressResolution.Failure(
            urn = trimmed,
            message = "Unsupported content-addressing scheme."
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun wrapResolvedContentHtml(
        urn: String,
        agentLabel: String,
        digestHex: String,
        rawPayload: String
    ): String {
        val looksHtml = rawPayload.contains("<html", ignoreCase = true) ||
            rawPayload.contains("<body", ignoreCase = true)
        if (looksHtml) return rawPayload
        val escaped = rawPayload.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                body { background: #0B0D0C; color: #F4F6F3; font-family: sans-serif; padding: 18px; }
                .badge { display: inline-block; padding: 4px 10px; border-radius: 999px; background: #253214; color: #C5FF45; font-size: 12px; font-weight: 600; }
                pre { background: #171B19; border: 1px solid #2E3632; padding: 14px; border-radius: 14px; overflow-x: auto; font-size: 13px; }
                .meta { color: #929B95; font-size: 12px; margin: 10px 0 16px; word-break: break-all; }
              </style>
            </head>
            <body>
              <span class="badge">SHA-256 Verified via $agentLabel</span>
              <div class="meta">URN: $urn<br/>Digest: $digestHex</div>
              <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun encodeSpaces(items: List<Space>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("colorHex", it.colorHex)
                put("iconKey", it.iconKey)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
    }.toString()

    private fun decodeSpaces(raw: String): List<Space> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Space(o.getString("id"), o.getString("name"), o.getLong("colorHex"), o.getString("iconKey"), o.optBoolean("isBuiltIn", false)))
            }
        }
    }.getOrElse { defaultSpaces() }

    private fun encodeTabGroups(items: List<TabGroup>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("colorHex", it.colorHex)
                put("spaceId", it.spaceId)
                put("isCollapsed", it.isCollapsed)
            })
        }
    }.toString()

    private fun decodeTabGroups(raw: String): List<TabGroup> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(TabGroup(o.getString("id"), o.getString("name"), o.getLong("colorHex"), o.getString("spaceId"), o.optBoolean("isCollapsed", false)))
            }
        }
    }.getOrElse { defaultTabGroups() }

    private fun encodeShortcuts(items: List<ShortcutItem>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("url", it.url)
                put("badgeText", it.badgeText)
                put("badgeColorHex", it.badgeColorHex)
            })
        }
    }.toString()

    private fun decodeShortcuts(raw: String): List<ShortcutItem> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ShortcutItem(o.getString("id"), o.getString("title"), o.getString("url"), o.getString("badgeText"), o.getLong("badgeColorHex")))
            }
        }
    }.getOrElse { defaultShortcuts() }

    private fun encodeBookmarks(items: List<BookmarkItem>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("url", it.url)
                put("collectionId", it.collectionId ?: "")
                put("createdAtMs", it.createdAtMs)
            })
        }
    }.toString()

    private fun decodeBookmarks(raw: String): List<BookmarkItem> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val col = o.optString("collectionId", "").ifBlank { null }
                add(BookmarkItem(o.getString("id"), o.getString("title"), o.getString("url"), col, o.optLong("createdAtMs", System.currentTimeMillis())))
            }
        }
    }.getOrElse { defaultBookmarks() }

    private fun encodeCollections(items: List<BookmarkCollection>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("accentHex", it.accentHex)
            })
        }
    }.toString()

    private fun decodeCollections(raw: String): List<BookmarkCollection> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(BookmarkCollection(o.getString("id"), o.getString("name"), o.optLong("accentHex", 0xFFC5FF45)))
            }
        }
    }.getOrElse { defaultCollections() }

    private fun encodeHistory(items: List<HistoryEntry>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("url", it.url)
                put("visitedAtMs", it.visitedAtMs)
            })
        }
    }.toString()

    private fun decodeHistory(raw: String): List<HistoryEntry> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(HistoryEntry(o.getString("id"), o.getString("title"), o.getString("url"), o.optLong("visitedAtMs", System.currentTimeMillis())))
            }
        }
    }.getOrElse { defaultHistory() }

    private fun encodeDownloads(items: List<DownloadItem>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("fileName", it.fileName)
                put("url", it.url)
                put("sizeLabel", it.sizeLabel)
                put("progressPercent", it.progressPercent)
                put("status", it.status.name)
                put("mimeType", it.mimeType)
                put("createdAtMs", it.createdAtMs)
            })
        }
    }.toString()

    private fun decodeDownloads(raw: String): List<DownloadItem> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val status = DownloadStatus.entries.firstOrNull { it.name == o.optString("status") } ?: DownloadStatus.COMPLETED
                add(DownloadItem(o.getString("id"), o.getString("fileName"), o.getString("url"), o.getString("sizeLabel"), o.getInt("progressPercent"), status, o.optString("mimeType", "application/octet-stream"), o.optLong("createdAtMs", System.currentTimeMillis())))
            }
        }
    }.getOrElse { defaultDownloads() }

    private fun encodeCredentials(items: List<SavedCredential>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("domain", it.domain)
                put("username", it.username)
                put("password", it.password)
                put("updatedAtMs", it.updatedAtMs)
            })
        }
    }.toString()

    private fun decodeCredentials(raw: String): List<SavedCredential> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(SavedCredential(o.getString("id"), o.getString("domain"), o.getString("username"), o.getString("password"), o.optLong("updatedAtMs", System.currentTimeMillis())))
            }
        }
    }.getOrElse { defaultCredentials() }

    private fun encodeAutofill(items: List<AutofillProfile>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("label", it.label)
                put("fullName", it.fullName)
                put("email", it.email)
                put("phone", it.phone)
                put("address", it.address)
                put("paymentHint", it.paymentHint)
            })
        }
    }.toString()

    private fun decodeAutofill(raw: String): List<AutofillProfile> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(AutofillProfile(o.getString("id"), o.getString("label"), o.getString("fullName"), o.getString("email"), o.getString("phone"), o.getString("address"), o.optString("paymentHint", "")))
            }
        }
    }.getOrElse { defaultAutofillProfiles() }

    private fun encodeExtensions(items: List<BrowserExtension>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("version", it.version)
                put("description", it.description)
                put("category", it.category)
                put("apis", JSONArray(it.apis))
                put("enabled", it.enabled)
                put("isBuiltIn", it.isBuiltIn)
                put("scriptBody", it.scriptBody)
            })
        }
    }.toString()

    private fun mergeExtensionsWithDefaults(raw: String): List<BrowserExtension> {
        val saved = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val apisJson = obj.optJSONArray("apis") ?: JSONArray()
                    val apis = buildList {
                        for (j in 0 until apisJson.length()) add(apisJson.getString(j))
                    }
                    add(BrowserExtension(obj.getString("id"), obj.getString("name"), obj.getString("version"), obj.getString("description"), obj.getString("category"), apis, obj.getBoolean("enabled"), obj.optBoolean("isBuiltIn", false), obj.optString("scriptBody", "")))
                }
            }
        }.getOrElse { return defaultExtensions() }

        val enabledById = saved.associateBy { it.id }
        val builtIns = defaultExtensions().map { def ->
            enabledById[def.id]?.let { def.copy(enabled = it.enabled) } ?: def
        }
        val custom = saved.filter { !it.isBuiltIn }
        return builtIns + custom
    }

    private fun encodeAgents(items: List<ContentAgentNode>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject().apply {
                put("id", it.id)
                put("label", it.label)
                put("baseUrl", it.baseUrl)
                put("enabled", it.enabled)
                put("lastStatus", it.lastStatus)
            })
        }
    }.toString()

    private fun decodeAgents(raw: String): List<ContentAgentNode> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ContentAgentNode(o.getString("id"), o.getString("label"), o.getString("baseUrl"), o.optBoolean("enabled", true), o.optString("lastStatus", "Idle")))
            }
        }
    }.getOrElse { defaultAgents() }
}

object BrowserFilterEngine {
    private val trackerHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "facebook.net",
        "connect.facebook.net",
        "scorecardresearch.com",
        "adservice.google.com",
        "analytics.twitter.com",
        "ads-twitter.com",
        "mixpanel.com",
        "segment.io"
    )

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "mc_cid", "mc_eid", "igshid"
    )

    fun isBlockedTrackerHost(urlString: String): Boolean {
        val host = runCatching { Uri.parse(urlString).host?.lowercase() }.getOrNull() ?: return false
        return trackerHosts.any { tracker -> host == tracker || host.endsWith(".$tracker") }
    }

    fun stripTrackingParams(urlString: String): Pair<String, Int> {
        val uri = runCatching { Uri.parse(urlString) }.getOrNull() ?: return urlString to 0
        val names = uri.queryParameterNames
        if (names.isEmpty()) return urlString to 0

        val strippedCount = names.count { it.lowercase() in trackingParams }
        if (strippedCount == 0) return urlString to 0

        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (name.lowercase() !in trackingParams) {
                uri.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return builder.build().toString() to strippedCount
    }
}
