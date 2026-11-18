package com.momentum.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momentum.browser.model.LibrarySection
import com.momentum.browser.model.MomentumDestination
import com.momentum.browser.model.SubScreen
import com.momentum.browser.ui.screens.AboutMomentumScreen
import com.momentum.browser.ui.screens.AutofillManagerScreen
import com.momentum.browser.ui.screens.BrowserScreen
import com.momentum.browser.ui.screens.ExtensionsScreen
import com.momentum.browser.ui.screens.HomeScreen
import com.momentum.browser.ui.screens.LibraryScreen
import com.momentum.browser.ui.screens.MomentumAiScreen
import com.momentum.browser.ui.screens.NetworkSettingsScreen
import com.momentum.browser.ui.screens.OnboardingScreen
import com.momentum.browser.ui.screens.PasswordManagerScreen
import com.momentum.browser.ui.screens.PrivacyHubScreen
import com.momentum.browser.ui.screens.SettingsScreen
import com.momentum.browser.ui.screens.SpacesScreen
import com.momentum.browser.ui.screens.TabOverviewScreen
import com.momentum.browser.ui.theme.MomentumTheme
import com.momentum.browser.viewmodel.MomentumViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialExternalUrl = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

        setContent {
            MomentumApp(initialExternalUrl = initialExternalUrl)
        }
    }
}

private data class NavDestSpec(
    val destination: MomentumDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MomentumApp(
    initialExternalUrl: String? = null,
    viewModel: MomentumViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewRegistry = remember { mutableStateMapOf<String, WebView>() }
    val activeWebView = webViewRegistry[uiState.activeTab.id]

    LaunchedEffect(initialExternalUrl) {
        if (!initialExternalUrl.isNullOrBlank()) {
            viewModel.completeOnboarding(importStarterBookmarks = false)
            viewModel.submitOmnibox(initialExternalUrl)
        }
    }

    MomentumTheme(
        darkTheme = uiState.darkTheme,
        amoledBlack = uiState.amoledBlack,
        textScale = uiState.textScale
    ) {
        if (!uiState.hasCompletedOnboarding) {
            OnboardingScreen(
                onComplete = { importBookmarks ->
                    viewModel.completeOnboarding(importBookmarks)
                }
            )
            return@MomentumTheme
        }

        // Back handling for secondary sub-screens (BrowserScreen handles its own BackHandler)
        BackHandler(enabled = uiState.subScreen != SubScreen.NONE && uiState.subScreen != SubScreen.BROWSER) {
            viewModel.closeSubScreen()
        }

        BackHandler(enabled = uiState.subScreen == SubScreen.NONE && uiState.destination != MomentumDestination.HOME) {
            viewModel.navigateToDestination(MomentumDestination.HOME)
        }

        val showPrimaryChrome = uiState.subScreen == SubScreen.NONE

        val navItems = remember {
            listOf(
                NavDestSpec(MomentumDestination.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
                NavDestSpec(MomentumDestination.TABS, R.string.nav_tabs, Icons.Rounded.Layers, Icons.Outlined.Layers, "nav_tabs"),
                NavDestSpec(MomentumDestination.LIBRARY, R.string.nav_library, Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks, "nav_library"),
                NavDestSpec(MomentumDestination.AI, R.string.nav_ai, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai"),
                NavDestSpec(MomentumDestination.PRIVACY, R.string.nav_privacy, Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_privacy")
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showPrimaryChrome && !useRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ) {
                            navItems.forEach { item ->
                                val selected = uiState.destination == item.destination
                                val label = stringResource(item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.navigateToDestination(item.destination) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    icon = {
                                        if (item.destination == MomentumDestination.TABS) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ) {
                                                        Text(uiState.tabs.size.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                ) {
                    if (showPrimaryChrome && useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            navItems.forEach { item ->
                                val selected = uiState.destination == item.destination
                                val label = stringResource(item.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.navigateToDestination(item.destination) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("rail_${item.testTag}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (uiState.subScreen) {
                            SubScreen.BROWSER -> BrowserScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                webViewRegistry = webViewRegistry
                            )
                            SubScreen.SPACES -> SpacesScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.SETTINGS -> SettingsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                activeWebView = activeWebView,
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.EXTENSIONS -> ExtensionsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.NETWORK_NODES -> NetworkSettingsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenUrnInBrowser = { urn -> viewModel.submitOmnibox(urn) },
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.PASSWORDS -> PasswordManagerScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.AUTOFILL -> AutofillManagerScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.ABOUT -> AboutMomentumScreen(
                                onBack = { viewModel.closeSubScreen() }
                            )
                            SubScreen.NONE -> {
                                when (uiState.destination) {
                                    MomentumDestination.HOME -> HomeScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onOpenSpaces = { viewModel.openSubScreen(SubScreen.SPACES) },
                                        onOpenSettings = { viewModel.openSubScreen(SubScreen.SETTINGS) },
                                        onOpenDownloads = { viewModel.selectLibrarySection(LibrarySection.DOWNLOADS) }
                                    )
                                    MomentumDestination.TABS -> TabOverviewScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onOpenSpaces = { viewModel.openSubScreen(SubScreen.SPACES) }
                                    )
                                    MomentumDestination.LIBRARY -> LibraryScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    MomentumDestination.AI -> MomentumAiScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        activeWebView = activeWebView
                                    )
                                    MomentumDestination.PRIVACY -> PrivacyHubScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        activeWebView = activeWebView
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
