package com.momentum.browser.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.momentum.browser.data.BrowserFilterEngine
import com.momentum.browser.data.OnDeviceAiEngine
import com.momentum.browser.model.BrowserTab
import com.momentum.browser.model.SitePermissionState
import java.io.ByteArrayInputStream

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MomentumWebView(
    tab: BrowserTab,
    globalJavaScriptEnabled: Boolean,
    sitePermissionsProvider: (String) -> SitePermissionState,
    isTrackerShieldActive: () -> Boolean,
    injectionScriptsProvider: () -> List<String>,
    onWebViewReady: (WebView) -> Unit,
    onPageStarted: (String, String) -> Unit,
    onProgressChanged: (String, Int) -> Unit,
    onPageFinished: (String, String, String?, Boolean, Boolean) -> Unit,
    onDomExtracted: (String, String) -> Unit,
    onFindResults: (Int, Int) -> Unit,
    onDownloadRequested: (String, String, String) -> Unit,
    onTrackerBlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember(tab.id) {
        WebView(context).apply {
            val defaultUa = settings.userAgentString
            setTag(com.momentum.browser.R.string.app_name, defaultUa)
            settings.apply {
                javaScriptEnabled = globalJavaScriptEnabled
                domStorageEnabled = !tab.isIncognito
                databaseEnabled = !tab.isIncognito
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            CookieManager.getInstance().setAcceptCookie(!tab.isIncognito)
        }
    }

    DisposableEffect(tab.id) {
        onWebViewReady(webView)
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = {
            webView.apply {
                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    if (isDoneCounting) {
                        onFindResults(activeMatchOrdinal, numberOfMatches)
                    }
                }
                setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    onDownloadRequested(url, name, mimeType ?: "application/octet-stream")
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(tab.id, newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (isTrackerShieldActive() && BrowserFilterEngine.isBlockedTrackerHost(reqUrl)) {
                            onTrackerBlocked()
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank()) {
                            onPageStarted(tab.id, url)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val currentUrl = url ?: tab.url
                        onPageFinished(
                            tab.id,
                            currentUrl,
                            view?.title,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                        val host = runCatching { Uri.parse(currentUrl).host }.getOrNull().orEmpty()
                        val perms = sitePermissionsProvider(host)
                        if (globalJavaScriptEnabled && perms.allowJavaScript) {
                            injectionScriptsProvider().forEach { script ->
                                view?.evaluateJavascript(script, null)
                            }
                            view?.evaluateJavascript(OnDeviceAiEngine.EXTRACT_PAGE_DOM_JS) { raw ->
                                if (!raw.isNullOrBlank()) {
                                    onDomExtracted(tab.id, raw)
                                }
                            }
                        }
                    }
                }
            }
        },
        update = { view ->
            val host = runCatching { Uri.parse(tab.url).host }.getOrNull().orEmpty()
            val perms = sitePermissionsProvider(host)
            view.settings.javaScriptEnabled = globalJavaScriptEnabled && perms.allowJavaScript
            CookieManager.getInstance().setAcceptCookie(!tab.isIncognito && perms.allowCookies)

            val defaultUa = (view.getTag(com.momentum.browser.R.string.app_name) as? String) ?: view.settings.userAgentString
            val targetUa = if (tab.isDesktopMode) DESKTOP_USER_AGENT else defaultUa
            if (view.settings.userAgentString != targetUa) {
                view.settings.userAgentString = targetUa
            }

            val resolvedHtml = tab.resolvedHtml
            if (resolvedHtml != null) {
                val htmlKey = "html:${tab.url}:${tab.reloadNonce}"
                if (view.tag != htmlKey) {
                    view.tag = htmlKey
                    view.loadDataWithBaseURL(tab.url, resolvedHtml, "text/html", "utf-8", null)
                }
            } else {
                val targetKey = "url:${tab.url}:${tab.reloadNonce}:${tab.isDesktopMode}"
                if (view.tag != targetKey && !tab.url.startsWith("momentum://")) {
                    view.tag = targetKey
                    view.loadUrl(tab.url)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
