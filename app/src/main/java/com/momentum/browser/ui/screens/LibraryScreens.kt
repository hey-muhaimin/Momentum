package com.momentum.browser.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momentum.browser.R
import com.momentum.browser.model.DownloadStatus
import com.momentum.browser.model.LibrarySection
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.viewmodel.MomentumViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (uiState.librarySection) {
                        LibrarySection.BOOKMARKS, LibrarySection.COLLECTIONS -> "Library"
                        LibrarySection.HISTORY -> "History"
                        LibrarySection.DOWNLOADS -> "Downloads"
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("library_add_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add item")
                }
            }

            // Section Selector Chips (Bookmarks / Collections / History / Downloads)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sections = listOf(
                    LibrarySection.BOOKMARKS to "Bookmarks",
                    LibrarySection.COLLECTIONS to "Collections",
                    LibrarySection.HISTORY to "History",
                    LibrarySection.DOWNLOADS to "Downloads"
                )
                sections.forEach { (section, label) ->
                    FilterChip(
                        selected = uiState.librarySection == section,
                        onClick = { viewModel.selectLibrarySection(section) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("library_chip_${section.name}")
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${uiState.librarySection.name.lowercase()}...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState.librarySection) {
                    LibrarySection.BOOKMARKS -> BookmarksList(uiState, searchQuery, viewModel)
                    LibrarySection.COLLECTIONS -> CollectionsList(uiState, searchQuery, viewModel)
                    LibrarySection.HISTORY -> HistoryList(uiState, searchQuery, viewModel)
                    LibrarySection.DOWNLOADS -> DownloadsList(uiState, searchQuery, viewModel)
                }
            }
        }
    }

    if (showAddDialog) {
        LibraryQuickAddDialog(
            section = uiState.librarySection,
            onDismiss = { showAddDialog = false },
            onAddBookmark = { title, url ->
                viewModel.addBookmark(title, url)
                showAddDialog = false
            },
            onAddCollection = { name ->
                viewModel.createCollection(name)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BookmarksList(
    uiState: MomentumUiState,
    query: String,
    viewModel: MomentumViewModel
) {
    val items = remember(uiState.bookmarks, query) {
        uiState.bookmarks.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }
    if (items.isEmpty()) {
        EmptyLibraryMessage(Icons.Rounded.Bookmark, "No bookmarks match your filter.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(items, key = { it.id }) { bm ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.submitOmnibox(bm.url) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.removeBookmark(bm.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsList(
    uiState: MomentumUiState,
    query: String,
    viewModel: MomentumViewModel
) {
    val collections = uiState.collections.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(collections, key = { it.id }) { col ->
            val itemsInCol = uiState.bookmarks.filter { it.collectionId == col.id }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(col.accentHex).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(col.accentHex))
                        }
                        Column {
                            Text(col.name, style = MaterialTheme.typography.titleMedium)
                            Text("${itemsInCol.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    itemsInCol.forEach { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.submitOmnibox(bm.url) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(bm.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    uiState: MomentumUiState,
    query: String,
    viewModel: MomentumViewModel
) {
    val filtered = remember(uiState.history, query) {
        uiState.history.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }
    if (filtered.isEmpty()) {
        EmptyLibraryMessage(Icons.Rounded.History, "No browsing history recorded yet.")
        return
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val todayItems = filtered.filter { DateUtils.isToday(it.visitedAtMs) }
    val earlierItems = filtered.filterNot { DateUtils.isToday(it.visitedAtMs) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        if (todayItems.isNotEmpty()) {
            item {
                HistorySectionHeader("Today") { viewModel.clearHistory() }
            }
            items(todayItems, key = { it.id }) { entry ->
                HistoryRow(entry.title, entry.url, timeFormat.format(Date(entry.visitedAtMs)),
                    onClick = { viewModel.submitOmnibox(entry.url) },
                    onDelete = { viewModel.removeHistoryEntry(entry.id) }
                )
            }
        }
        if (earlierItems.isNotEmpty()) {
            item {
                HistorySectionHeader("Yesterday & Earlier") { viewModel.clearHistory() }
            }
            items(earlierItems, key = { it.id }) { entry ->
                HistoryRow(entry.title, entry.url, timeFormat.format(Date(entry.visitedAtMs)),
                    onClick = { viewModel.submitOmnibox(entry.url) },
                    onDelete = { viewModel.removeHistoryEntry(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(title: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClear) {
            Text("Clear all", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HistoryRow(
    title: String,
    url: String,
    timeText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.cd_delete))
            }
        }
    }
}

@Composable
private fun DownloadsList(
    uiState: MomentumUiState,
    query: String,
    viewModel: MomentumViewModel
) {
    var tabIdx by remember { mutableIntStateOf(0) }
    val items = remember(uiState.downloads, tabIdx, query) {
        uiState.downloads.filter { dl ->
            (tabIdx == 0 || dl.status == DownloadStatus.IN_PROGRESS || dl.status == DownloadStatus.PAUSED) &&
                (query.isBlank() || dl.fileName.contains(query, ignoreCase = true))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIdx, containerColor = Color.Transparent) {
            Tab(selected = tabIdx == 0, onClick = { tabIdx = 0 }, text = { Text("All") })
            Tab(selected = tabIdx == 1, onClick = { tabIdx = 1 }, text = { Text("In progress") })
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                EmptyLibraryMessage(Icons.Rounded.Download, "No downloads in this queue.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items, key = { it.id }) { dl ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dl.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val statusLabel = when (dl.status) {
                                        DownloadStatus.COMPLETED -> "Completed"
                                        DownloadStatus.IN_PROGRESS -> "${dl.progressPercent}%"
                                        DownloadStatus.PAUSED -> "Paused (${dl.progressPercent}%)"
                                        DownloadStatus.FAILED -> "Failed"
                                    }
                                    Text("${dl.sizeLabel} • $statusLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (dl.status == DownloadStatus.COMPLETED) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    IconButton(onClick = { viewModel.toggleDownloadPause(dl.id) }) {
                                        Icon(
                                            imageVector = if (dl.status == DownloadStatus.IN_PROGRESS) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Toggle download"
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeDownload(dl.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.cd_delete))
                                }
                            }
                            if (dl.status == DownloadStatus.IN_PROGRESS || dl.status == DownloadStatus.PAUSED) {
                                LinearProgressIndicator(
                                    progress = { dl.progressPercent / 100f },
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.downloads.isNotEmpty()) {
            OutlinedButton(
                onClick = { viewModel.clearDownloads() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryQuickAddDialog(
    section: LibrarySection,
    onDismiss: () -> Unit,
    onAddBookmark: (String, String) -> Unit,
    onAddCollection: (String) -> Unit
) {
    var field1 by remember { mutableStateOf("") }
    var field2 by remember { mutableStateOf("https://") }
    val isCollection = section == LibrarySection.COLLECTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCollection) "New Collection" else "Add Bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = field1,
                    onValueChange = { field1 = it },
                    label = { Text(if (isCollection) "Collection Name" else "Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isCollection) {
                    OutlinedTextField(
                        value = field2,
                        onValueChange = { field2 = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isCollection) onAddCollection(field1) else onAddBookmark(field1, field2) },
                enabled = field1.isNotBlank() && (isCollection || field2.isNotBlank())
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
