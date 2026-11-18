package com.momentum.browser.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.momentum.browser.R
import com.momentum.browser.ui.components.AtmosphericMountainBackdrop

@Composable
fun OnboardingScreen(
    onComplete: (importBookmarks: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var importBookmarks by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        AtmosphericMountainBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_momentum_mark),
                        contentDescription = "Momentum logo",
                        modifier = Modifier.size(34.dp)
                    )
                    Text("MOMENTUM", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(
                    onClick = { onComplete(importBookmarks) },
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append("Browse ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("beyond.")
                        }
                    },
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = "A focused, spatial Android browser built with Luminous Graphite clarity, on-device AI, and privacy-first controls.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val highlights = listOf(
                    Triple(Icons.Rounded.Layers, "Spaces & Visual Tab Groups", "Keep Work, Research, and Personal tabs cleanly separated."),
                    Triple(Icons.Rounded.AutoAwesome, "On-Device Momentum AI", "Summarize pages and ask questions locally without cloud tracking."),
                    Triple(Icons.Rounded.Shield, "Privacy & Extensions Built In", "Block trackers, strip tracking URLs, and resolve #Net URNs.")
                )

                highlights.forEachIndexed { index, (icon, title, body) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (step == index) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        },
                        tonalElevation = 2.dp,
                        onClick = { step = index },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    onClick = { importBookmarks = !importBookmarks },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = importBookmarks, onCheckedChange = { importBookmarks = it })
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text("Import starter developer bookmarks", style = MaterialTheme.typography.titleMedium)
                            Text("Includes MDN, Material 3, GitHub, and Wikipedia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onComplete(importBookmarks) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_start_button")
                ) {
                    Text("Start Browsing", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
