package com.momentum.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momentum.browser.R
import com.momentum.browser.model.BrowserTab
import com.momentum.browser.model.MomentumUiState
import com.momentum.browser.model.Space
import com.momentum.browser.model.TabGroup
import com.momentum.browser.viewmodel.MomentumViewModel

@Composable
fun TabOverviewScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    onOpenSpaces: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showGroupsSheet by remember { mutableStateOf(false) }

    val spaceTabs = remember(uiState.tabs, uiState.activeSpaceId, searchQuery) {
        uiState.tabs.filter { tab ->
            tab.spaceId == uiState.activeSpaceId &&
                (searchQuery.isBlank() ||
                    tab.title.contains(searchQuery, ignoreCase = true) ||
                    tab.url.contains(searchQuery, ignoreCase = true))
        }
    }

    val spaceGroups = remember(uiState.tabGroups, uiState.activeSpaceId) {
        uiState.tabGroups.filter { it.spaceId == uiState.activeSpaceId }
    }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your tabs", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${uiState.tabs.size} open • ${uiState.activeSpace.name} space",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenSpaces,
                        modifier = Modifier.testTag("tabs_open_spaces_button")
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = "Spaces")
                    }
                    IconButton(
                        onClick = { viewModel.toggleTabGridMode() },
                        modifier = Modifier.testTag("tabs_layout_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isTabGridMode) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                            contentDescription = "Toggle tab layout"
                        )
                    }
                }
            }

            // Space Quick Filter Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.spaces, key = { it.id }) { space ->
                    FilterChip(
                        selected = space.id == uiState.activeSpaceId,
                        onClick = { viewModel.selectSpace(space.id) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(space.colorHex))
                            )
                        },
                        label = {
                            val count = uiState.tabs.count { it.spaceId == space.id }
                            Text("${space.name} ($count)")
                        }
                    )
                }
            }

            // Search Tabs Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tabs...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_tabs_input")
            )

            // Tab Cards (Grid or Spatial Stacked Cards)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (spaceTabs.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No matching tabs in ${uiState.activeSpace.name}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + New Tab to start browsing in this Space.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (uiState.isTabGridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(spaceTabs, key = { it.id }) { tab ->
                            val group = spaceGroups.firstOrNull { it.id == tab.groupId }
                            VisualTabCard(
                                tab = tab,
                                group = group,
                                isActive = tab.id == uiState.activeTabId,
                                compact = true,
                                onSelect = { viewModel.selectTabAndOpen(tab.id) },
                                onClose = { viewModel.closeTab(tab.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(spaceTabs, key = { it.id }) { tab ->
                            val group = spaceGroups.firstOrNull { it.id == tab.groupId }
                            VisualTabCard(
                                tab = tab,
                                group = group,
                                isActive = tab.id == uiState.activeTabId,
                                compact = false,
                                onSelect = { viewModel.selectTabAndOpen(tab.id) },
                                onClose = { viewModel.closeTab(tab.id) }
                            )
                        }
                    }
                }
            }

            // Bottom Actions: + New Tab | Groups | Incognito
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.createNewTab(isIncognito = false) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tabs_new_tab_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Tab")
                }

                OutlinedButton(
                    onClick = { showGroupsSheet = true },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tabs_groups_button")
                ) {
                    Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Groups")
                }

                OutlinedButton(
                    onClick = { viewModel.createNewTab(isIncognito = true) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tabs_incognito_button")
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showGroupsSheet) {
        TabGroupsSheet(
            tabs = spaceTabs,
            groups = spaceGroups,
            onCreateGroup = viewModel::createTabGroup,
            onRenameGroup = viewModel::renameTabGroup,
            onAssignTabToGroup = viewModel::assignTabToGroup,
            onDismiss = { showGroupsSheet = false }
        )
    }
}

@Composable
private fun VisualTabCard(
    tab: BrowserTab,
    group: TabGroup?,
    isActive: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        tab.isIncognito -> MaterialTheme.colorScheme.secondary
        group != null -> Color(group.colorHex).copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tab.isIncognito) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .testTag("tab_card_${tab.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (tab.isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Public,
                    contentDescription = null,
                    tint = if (tab.isIncognito) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tab.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (group != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(group.colorHex).copy(alpha = 0.18f)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(group.colorHex),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 68.dp else 56.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tab.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabGroupsSheet(
    tabs: List<BrowserTab>,
    groups: List<TabGroup>,
    onCreateGroup: (String, Long) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onAssignTabToGroup: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val palette = listOf(0xFFC5FF45, 0xFF8B7CFF, 0xFF4ADE80, 0xFFFB923C, 0xFF38BDF8)
    var selectedColor by remember { mutableLongStateOf(palette.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tab Groups", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("New group name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onCreateGroup(newGroupName, selectedColor)
                        newGroupName = ""
                    },
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("Create")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (selectedColor == hex) 2.5.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = hex }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            groups.forEach { grp ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(grp.colorHex))
                                )
                                Text(grp.name, style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(
                                onClick = {
                                    editingGroupId = grp.id
                                    renameText = grp.name
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename group", modifier = Modifier.size(16.dp))
                            }
                        }

                        if (editingGroupId == grp.id) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    onRenameGroup(grp.id, renameText)
                                    editingGroupId = null
                                }) {
                                    Text("Save")
                                }
                            }
                        }

                        // Quick tab assignment chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tabs, key = { "${grp.id}_${it.id}" }) { tab ->
                                val inGroup = tab.groupId == grp.id
                                FilterChip(
                                    selected = inGroup,
                                    onClick = {
                                        onAssignTabToGroup(tab.id, if (inGroup) null else grp.id)
                                    },
                                    label = {
                                        Text(tab.title.take(16), maxLines = 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun SpacesScreen(
    uiState: MomentumUiState,
    viewModel: MomentumViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                        Text("Spaces", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("spaces_add_icon_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create Space")
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.spaces, key = { it.id }) { space ->
                        val tabCount = uiState.tabs.count { it.spaceId == space.id }
                        SpaceCard(
                            space = space,
                            tabCount = tabCount,
                            isSelected = space.id == uiState.activeSpaceId,
                            onClick = {
                                viewModel.selectSpace(space.id)
                                onBack()
                            }
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Organize your browsing with Spaces", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Each Space keeps its own tabs and groups cleanly separated by focus area.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_space_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Space")
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, colorHex ->
                viewModel.createSpace(name, colorHex)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun SpaceCard(
    space: Space,
    tabCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val base = Color(space.colorHex)
    val icon = when (space.iconKey) {
        "work" -> Icons.Rounded.Work
        "research" -> Icons.Rounded.Science
        else -> Icons.Rounded.Person
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
        modifier = Modifier
            .width(142.dp)
            .height(174.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("space_card_${space.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(base.copy(alpha = 0.92f), base.copy(alpha = 0.65f))
                    )
                )
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0B0D0C),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.TopStart)
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B0D0C)
                )
                Text(
                    text = "$tabCount tabs",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF0B0D0C).copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colors = listOf(0xFF4ADE80, 0xFF8B7CFF, 0xFFFB923C, 0xFFC5FF45, 0xFF38BDF8)
    var selectedColor by remember { mutableLongStateOf(colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (selectedColor == hex) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
