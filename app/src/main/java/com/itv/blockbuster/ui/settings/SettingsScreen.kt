package com.itv.blockbuster.ui.settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.CategorySortItem

enum class SettingsMenu(val label: String, val icon: ImageVector, val destructive: Boolean = false) {
    REMEMBER_PROFILE("Remember Last Profile", Icons.Default.People),
    ANIMATIONS("App Animations", Icons.Default.Movie),
    DATE_TIME("Date & Time", Icons.Default.AccessTime),
    CONTENT("Content Settings", Icons.Default.Tune),
    PLAYER("Player Settings", Icons.Default.PlayArrow),
    AUDIO("Audio Settings", Icons.Default.VolumeUp),
    PORTAL("Portal", Icons.Default.Dns),
    DIAGNOSTIC("Diagnostic", Icons.Default.BugReport),
    CLEAR_CACHE("Clear Cache", Icons.Default.Cached),
    CLEAR_SEARCH("Clear All Search History", Icons.Default.History),
    CLEAR_DATA("Clear All User Data", Icons.Default.Delete, destructive = true)
}

private val HOME_PAGE_OPTIONS = listOf("LIVE_TV", "MOVIES", "TV_SHOWS", "TV_GUIDE", "FAVORITES", "RECENTS")
private val MY_LIST_OPTIONS = listOf("ALL", "LIVE", "MOVIES", "SERIES")
private val ENGINE_OPTIONS = listOf("EXO", "VLC")
private val TIMEZONE_OPTIONS = listOf("", "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata", "Asia/Shanghai", "Australia/Sydney")

@Composable
fun SettingsScreen(onOpenPortals: () -> Unit, onLogout: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val isPortrait = rememberFormFactor() == FormFactor.MOBILE_PORTRAIT
    var selected by remember { mutableStateOf(SettingsMenu.REMEMBER_PROFILE) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (isPortrait) MobileSettings(state, viewModel, onOpenPortals) { dialog = it }
        else {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.weight(0.45f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text("Profile & Settings", color = BbTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
                    items(SettingsMenu.values().toList()) { menu ->
                        MenuRow(menu, selected == menu) { selected = menu; handleMenuAction(menu, viewModel, onOpenPortals) { dialog = it } }
                    }
                }
                Column(modifier = Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    DetailPane(selected, state, viewModel, onOpenPortals) { dialog = it }
                }
            }
        }
        notice?.let { msg ->
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).clip(RoundedCornerShape(8.dp)).background(BbSurface).border(1.dp, BbAccent, RoundedCornerShape(8.dp)).clickable { viewModel.clearNotice() }.padding(horizontal = 20.dp, vertical = 12.dp)) { Text(msg, color = BbTextPrimary, fontSize = 14.sp) }
        }
    }

    when (dialog) {
        "timezone" -> SelectDialog("Time Zone", TIMEZONE_OPTIONS.map { if (it.isEmpty()) "System default" else it }, if (state.timezone.isEmpty()) "System default" else state.timezone, onSelect = { label -> viewModel.setTimezone(if (label == "System default") "" else label); dialog = null }, onDismiss = { dialog = null })
        "home" -> SelectDialog("Default Home Page", HOME_PAGE_OPTIONS, state.defaultHomePage, onSelect = { viewModel.setDefaultHomePage(it); dialog = null }, onDismiss = { dialog = null })
        "mylist" -> SelectDialog("Default My List Page", MY_LIST_OPTIONS, state.defaultMyListPage, onSelect = { viewModel.setDefaultMyListPage(it); dialog = null }, onDismiss = { dialog = null })
        "player_live" -> SelectDialog("Live TV Player", ENGINE_OPTIONS, state.playerEngineLive, onSelect = { viewModel.setPlayerEngineLive(it); dialog = null }, onDismiss = { dialog = null })
        "player_vod" -> SelectDialog("VOD Player", ENGINE_OPTIONS, state.playerEngineVod, onSelect = { viewModel.setPlayerEngineVod(it); dialog = null }, onDismiss = { dialog = null })
        "cats_vod" -> SortableListDialog("Movie Categories", state.vodSortItems, onSave = viewModel::saveVodSort, onDismiss = { dialog = null })
        "cats_series" -> SortableListDialog("TV Show Categories", state.seriesSortItems, onSave = viewModel::saveSeriesSort, onDismiss = { dialog = null })
        "cats_live" -> SortableListDialog("Live TV Categories", state.liveSortItems, onSave = viewModel::saveLiveSort, onDismiss = { dialog = null })"confirm_data" -> ConfirmDialog("Clear all user data?", "Favorites, recents and playback history for this profile on this portal will be removed.", onConfirm = { viewModel.clearAllUserData(); dialog = null }, onDismiss = { dialog = null })
        "diagnostic" -> InfoDialog("Diagnostic", "Host: ${state.diagHost.ifEmpty { "—" }}\nPortal path: ${state.diagPath}\nConnected: ${if (state.diagConnected) "Yes" else "No"}") { dialog = null }
    }
}

private fun handleMenuAction(menu: SettingsMenu, viewModel: SettingsViewModel, onOpenPortals: () -> Unit, openDialog: (String) -> Unit) {
    when (menu) {
        SettingsMenu.PORTAL -> onOpenPortals()
        SettingsMenu.CLEAR_CACHE -> viewModel.clearCache()
        SettingsMenu.CLEAR_SEARCH -> viewModel.clearSearchHistory()
        SettingsMenu.CLEAR_DATA -> openDialog("confirm_data")
        SettingsMenu.DIAGNOSTIC -> openDialog("diagnostic")
        SettingsMenu.DATE_TIME -> openDialog("timezone")
        else -> Unit
    }
}

@Composable
private fun MenuRow(menu: SettingsMenu, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(when { focused -> if (menu.destructive) BbDestructive else BbAccent; selected -> BbCard; else -> Color.Transparent }).then(if (focused) Modifier.border(2.dp, if (menu.destructive) BbDestructive else BbAccent, RoundedCornerShape(50)) else Modifier).clickable(onClick = onClick).focusable().onFocusChanged { focused = it.isFocused }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(menu.icon, null, tint = when { focused -> BbTextPrimary; menu.destructive -> BbDestructive; else -> BbTextSecondary }, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(menu.label, color = if (menu.destructive && !focused) BbDestructive else BbTextPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun DetailPane(selected: SettingsMenu, state: SettingsUiState, viewModel: SettingsViewModel, onOpenPortals: () -> Unit, openDialog: (String) -> Unit) {
    SectionHeader(selected.label)
    when (selected) {
        SettingsMenu.REMEMBER_PROFILE -> ToggleRow("Remember Last Profile", state.rememberLastProfile, viewModel::setRememberLastProfile)
        SettingsMenu.ANIMATIONS -> ToggleRow("App Animations", state.appAnimations, viewModel::setAppAnimations)
        SettingsMenu.DATE_TIME -> ValueRow("Time Zone", state.timezone.ifEmpty { "System default" }) { openDialog("timezone") }
        SettingsMenu.CONTENT -> {
            ValueRow("Movie Categories", "Sort & Display") { openDialog("cats_vod") }
            ValueRow("TV Show Categories", "Sort & Display") { openDialog("cats_series") }
            ValueRow("Live TV Categories", "Sort & Display") { openDialog("cats_live") }
            ValueRow("Default Home Page", state.defaultHomePage) { openDialog("home") }
            ValueRow("Default My List Page", state.defaultMyListPage) { openDialog("mylist") }
        }
        SettingsMenu.PLAYER -> {
            ValueRow("Live TV Player", state.playerEngineLive) { openDialog("player_live") }
            ValueRow("VOD Player", state.playerEngineVod) { openDialog("player_vod") }
            ToggleRow("Autoplay Next Episode", state.autoPlayNext, viewModel::setAutoPlayNext)
            ToggleRow("Autostart Live TV", state.autoStartLive, viewModel::setAutoStartLive)
            StepperRow("Rewind Interval (s)", state.rewindInterval, 5, 5..60, viewModel::setRewindInterval)
            StepperRow("Forward Interval (s)", state.forwardInterval, 5, 5..120, viewModel::setForwardInterval)
        }
        SettingsMenu.AUDIO -> Text("Audio settings will be available in a future update.", color = BbTextMuted, fontSize = 14.sp)
        SettingsMenu.PORTAL -> ActionRow("Open Portal Manager", BbTextPrimary, onOpenPortals)
        SettingsMenu.DIAGNOSTIC -> ActionRow("View Diagnostic", BbTextPrimary) { openDialog("diagnostic") }
        SettingsMenu.CLEAR_CACHE -> ActionRow("Clear image & network cache", BbTextPrimary) { viewModel.clearCache() }
        SettingsMenu.CLEAR_SEARCH -> ActionRow("Clear stored search history", BbTextPrimary) { viewModel.clearSearchHistory() }
        SettingsMenu.CLEAR_DATA -> ActionRow("Remove favorites, recents & progress", BbDestructive) { openDialog("confirm_data") }
    }
}

@Composable
private fun MobileSettings(state: SettingsUiState, viewModel: SettingsViewModel, onOpenPortals: () -> Unit, openDialog: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { SectionHeader("App Settings") }
        item { ToggleRow("Remember Last Profile", state.rememberLastProfile, viewModel::setRememberLastProfile) }
        item { ToggleRow("App Animations", state.appAnimations, viewModel::setAppAnimations) }
        item { ValueRow("Time Zone", state.timezone.ifEmpty { "System default" }) { openDialog("timezone") } }
        item { SectionHeader("Content Settings") }
        item { ValueRow("Movie Categories", "Sort & Display") { openDialog("cats_vod") } }
        item { ValueRow("TV Show Categories", "Sort & Display") { openDialog("cats_series") } }
        item { ValueRow("Live TV Categories", "Sort & Display") { openDialog("cats_live") } }
        item { ValueRow("Default Home Page", state.defaultHomePage) { openDialog("home") } }
        item { ValueRow("Default My List Page", state.defaultMyListPage) { openDialog("mylist") } }
        item { SectionHeader("Player Settings") }
        item { ValueRow("Live TV Player", state.playerEngineLive) { openDialog("player_live") } }
        item { ValueRow("VOD Player", state.playerEngineVod) { openDialog("player_vod") } }
        item { ToggleRow("Autoplay Next Episode", state.autoPlayNext, viewModel::setAutoPlayNext) }
        item { ToggleRow("Autostart Live TV", state.autoStartLive, viewModel::setAutoStartLive) }
        item { StepperRow("Rewind Interval (s)", state.rewindInterval, 5, 5..60, viewModel::setRewindInterval) }
        item { StepperRow("Forward Interval (s)", state.forwardInterval, 5, 5..120, viewModel::setForwardInterval) }
        item { SectionHeader("Other") }
        item { ActionRow("Portal", BbTextPrimary, onOpenPortals) }
        item { ActionRow("Diagnostic", BbTextPrimary) { openDialog("diagnostic") } }
        item { ActionRow("Clear Cache", BbTextPrimary) { viewModel.clearCache() } }
        item { ActionRow("Clear All Search History", BbTextPrimary) { viewModel.clearSearchHistory() } }
        item { ActionRow("Clear All User Data", BbDestructive) { openDialog("confirm_data") } }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable private fun SectionHeader(title: String) { Text(title, color = BbTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
@Composable private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = BbTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedTrackColor = BbAccent)) } }
@Composable private fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) { Row(modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = BbTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text(value, color = BbTextSecondary, fontSize = 14.sp); if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = BbTextMuted, modifier = Modifier.size(18.dp)) } }
@Composable private fun ActionRow(label: String, color: Color, onClick: () -> Unit) { Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun StepperRow(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = BbTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f)); TextButton(onClick = { if (value - step >= range.first) onChange(value - step) }) { Text("−", color = BbTextPrimary) }; Text("$value", color = BbTextPrimary, fontSize = 15.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center); TextButton(onClick = { if (value + step <= range.last) onChange(value + step) }) { Text("+", color = BbTextPrimary) } } }
@Composable private fun SelectDialog(title: String, options: List<String>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, containerColor = BbSurface, title = { Text(title, color = BbTextPrimary) }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { options.forEach { opt -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }.padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(opt, color = if (opt == current) BbAccent else BbTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f)); if (opt == current) Text("✓", color = BbAccent) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = BbTextSecondary) } }) }
@Composable private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, containerColor = BbSurface, title = { Text(title, color = BbTextPrimary) }, text = { Text(message, color = BbTextSecondary) }, confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm", color = BbDestructive) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = BbTextSecondary) } }) }
@Composable private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, containerColor = BbSurface, title = { Text(title, color = BbTextPrimary) }, text = { Text(message, color = BbTextSecondary) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = BbTextSecondary) } }) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SortableListDialog(
    title: String,
    initialItems: List<CategorySortItem>,
    onSave: (List<CategorySortItem>) -> Unit,
    onDismiss: () -> Unit
) {
    // Local state prevents global recompositions during drag/toggle
    var localItems by remember { mutableStateOf(initialItems) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeight = 56.dp

    LaunchedEffect(initialItems) {
        localItems = initialItems
    }

    AlertDialog(
        onDismissRequest = {
            onSave(localItems)
            onDismiss()
        },
        containerColor = BbSurface,
        title = { Text(title, color = BbTextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(localItems, key = { it.id }) { item ->
                    val index = localItems.indexOf(item)
                    val isDragged = draggedIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()

                            .zIndex(if (isDragged) 1f else 0f)
                            .shadow(if (isDragged) 8.dp else 0.dp, RoundedCornerShape(8.dp))
                            .background(
                                if (isDragged) BbAccent.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .focusable()
                            .pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedIndex = index; dragOffset = 0f },
                                    onDragEnd = { draggedIndex = null; dragOffset = 0f },
                                    onDragCancel = { draggedIndex = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val from = draggedIndex
                                        if (from != null) {
                                            val shift = when {
                                                dragOffset > rowHeight.value * 0.5f -> 1
                                                dragOffset < -rowHeight.value * 0.5f -> -1
                                                else -> 0
                                            }
                                            val to = from + shift
                                            if (shift != 0 && to in localItems.indices) {
                                                val newList = localItems.toMutableList().apply { add(to, removeAt(from)) }
                                                draggedIndex = to
                                                dragOffset = 0f
                                                localItems = newList
                                            }
                                        }
                                    }
                                )
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isVisible,
                            onCheckedChange = { checked ->
                                val newList = localItems.map { if (it.id == item.id) it.copy(isVisible = checked) else it }
                                localItems = newList
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BbAccent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${index + 1}. ${item.title}",
                            color = if (item.isVisible) BbTextPrimary else BbTextMuted,
                            fontSize = 15.sp,
                            fontWeight = if (isDragged) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Column {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = localItems.toMutableList().apply { add(index - 1, removeAt(index)) }
                                        localItems = newList
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move Up", tint = BbTextSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (index < localItems.lastIndex) {
                                        val newList = localItems.toMutableList().apply { add(index + 1, removeAt(index)) }
                                        localItems = newList
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move Down", tint = BbTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Icon(Icons.Default.Reorder, "Drag", tint = if (isDragged) BbAccent else BbTextMuted, modifier = Modifier.size(24.dp))
                    }
                    HorizontalDivider(color = BbCard, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(localItems)
                onDismiss()
            }) { Text("Done", color = BbAccent) }
        }
    )
}