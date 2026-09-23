package com.itv.blockbuster.ui.settings

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
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
import com.itv.blockbuster.util.FocusRegistry
import kotlinx.coroutines.launch

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

// RENAMED concept: landing page now includes HOME as the first (default) choice
private val HOME_PAGE_OPTIONS = listOf("HOME", "LIVE_TV", "MOVIES", "TV_SHOWS", "TV_GUIDE", "FAVORITES", "RECENTS")
private val ENGINE_OPTIONS = listOf("EXO", "VLC")
private val TIMEZONE_OPTIONS = listOf("", "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata", "Asia/Shanghai", "Australia/Sydney")

@Composable
fun SettingsScreen(
    onOpenPortals: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    // D-pad focus: this screen's route, used to hand focus off from the rail
    // to its first item once content has loaded (see AppShell/FocusRegistry).
    route: String = Routes.SETTINGS
) {
    val state by viewModel.state.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val isPortrait = rememberFormFactor() == FormFactor.MOBILE_PORTRAIT
    var selected by remember { mutableStateOf(SettingsMenu.REMEMBER_PROFILE) }
    var dialog by remember { mutableStateOf<String?>(null) }
    // D-pad focus: the first menu row is the target FocusRegistry.notifyContentReady
    // shifts focus onto once this screen is armed (on TV/landscape only - the
    // menu list itself is static, so there's no "loading" state to wait for).
    val firstMenuRequester = remember { FocusRequester() }
    if (!isPortrait) {
        FocusRegistry.registerFirstItem(route, firstMenuRequester)
        // FIX: unregister on dispose - see FocusRegistry.unregisterFirstItem's
        // doc comment. Without this, a stale/detached requester could still
        // be handed out as a `down = ...` focus target after this menu is
        // torn down, crashing uncatchably on the next real D-pad press.
        DisposableEffect(route, firstMenuRequester) {
            onDispose { FocusRegistry.unregisterFirstItem(route, firstMenuRequester) }
        }
    }
    LaunchedEffect(isPortrait) {
        if (!isPortrait) FocusRegistry.notifyContentReady(route)
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        if (isPortrait) MobileSettings(state, viewModel, onOpenPortals) { dialog = it }
        else {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.weight(0.45f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text("Profile & Settings", color = BbTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
                    itemsIndexed(SettingsMenu.values().toList()) { index, menu ->
                        MenuRow(
                            menu = menu,
                            selected = selected == menu,
                            focusRequester = if (index == 0) firstMenuRequester else null,
                            isTopRow = index == 0,
                            isBottomRow = index == SettingsMenu.values().size - 1,
                            onClick = { selected = menu; handleMenuAction(menu, viewModel, onOpenPortals) { dialog = it } }
                        )
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
        "home" -> SelectDialog("Default Landing Page", HOME_PAGE_OPTIONS, state.defaultHomePage, onSelect = { viewModel.setDefaultHomePage(it); dialog = null }, onDismiss = { dialog = null })
        "player_live" -> SelectDialog("Live TV Player", ENGINE_OPTIONS, state.playerEngineLive, onSelect = { viewModel.setPlayerEngineLive(it); dialog = null }, onDismiss = { dialog = null })
        "player_vod" -> SelectDialog("VOD Player", ENGINE_OPTIONS, state.playerEngineVod, onSelect = { viewModel.setPlayerEngineVod(it); dialog = null }, onDismiss = { dialog = null })
        // NEW: Home category sort & visibility dialog
        "cats_home" -> SortableListDialog("Home Categories", state.homeSortItems, onSave = viewModel::saveHomeSort, onDismiss = { dialog = null })
        "cats_vod" -> SortableListDialog("Movie Categories", state.vodSortItems, onSave = viewModel::saveVodSort, onDismiss = { dialog = null })
        "cats_series" -> SortableListDialog("TV Show Categories", state.seriesSortItems, onSave = viewModel::saveSeriesSort, onDismiss = { dialog = null })
        "cats_live" -> SortableListDialog("Live TV Categories", state.liveSortItems, onSave = viewModel::saveLiveSort, onDismiss = { dialog = null })
        "confirm_data" -> ConfirmDialog("Clear all user data?", "Favorites, recents and playback history for this profile on this portal will be removed.", onConfirm = { viewModel.clearAllUserData(); dialog = null }, onDismiss = { dialog = null })
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
private fun MenuRow(
    menu: SettingsMenu,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    // D-pad focus: this whole list is the leftmost (and only) column in
    // Settings' left pane, so every row - not just the first - escapes to
    // the rail on Left. isTopRow/isBottomRow block Up/Down from escaping to
    // the rail at the top/bottom of the (short, non-scrolling) list, same as
    // every other browser screen.
    isTopRow: Boolean = false,
    isBottomRow: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(when { focused -> if (menu.destructive) BbDestructive else BbAccent; selected -> BbCard; else -> Color.Transparent })
            .then(if (focused) Modifier.border(2.dp, if (menu.destructive) BbDestructive else BbAccent, RoundedCornerShape(50)) else Modifier)
            .focusProperties {
                left = FocusRegistry.leftEscapeTarget()
                if (isTopRow) up = FocusRequester.Cancel
                if (isBottomRow) down = FocusRequester.Cancel
            }
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            // NEW: Home screen category sort & visibility
            ValueRow("Home Categories", "Sort & Display") { openDialog("cats_home") }
            ValueRow("Movie Categories", "Sort & Display") { openDialog("cats_vod") }
            ValueRow("TV Show Categories", "Sort & Display") { openDialog("cats_series") }
            ValueRow("Live TV Categories", "Sort & Display") { openDialog("cats_live") }
            // RENAMED from "Default Home Page"; HOME is now a choice and the default
            ValueRow("Default Landing Page", state.defaultHomePage) { openDialog("home") }
            // NEW toggle, off by default
            ToggleRow("Display Adult Content", state.displayAdultContent, viewModel::setDisplayAdultContent)
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
        item { ValueRow("Home Categories", "Sort & Display") { openDialog("cats_home") } }
        item { ValueRow("Movie Categories", "Sort & Display") { openDialog("cats_vod") } }
        item { ValueRow("TV Show Categories", "Sort & Display") { openDialog("cats_series") } }
        item { ValueRow("Live TV Categories", "Sort & Display") { openDialog("cats_live") } }
        // RENAMED from "Default Home Page"; HOME is now a choice and the default
        item { ValueRow("Default Landing Page", state.defaultHomePage) { openDialog("home") } }
        // NEW toggle, off by default
        item { ToggleRow("Display Adult Content", state.displayAdultContent, viewModel::setDisplayAdultContent) }
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
    // Single source of truth for "picked up for reordering" - set by
    // clicking the reorder handle (touch tap or D-pad select, same action
    // for both). While an index is held here, that row is visually pinned
    // (see isHeld below), DPAD Up/Down move it one slot at a time, and a
    // plain touch drag anywhere on the row repositions it.
    var heldIndex by remember { mutableStateOf<Int?>(null) }
    // Touch-drag only: how far the finger has moved past the held row's
    // resting position, in px. Used to (a) visually translate the row so it
    // tracks the finger and (b) decide when it's moved far enough to swap
    // with a neighbor.
    var dragOffsetPx by remember { mutableStateOf(0f) }
    // FIX (issue - "goes half way through another item and sticks back"):
    // this was a hardcoded 56.dp assumption, but the actual Row below has
    // no explicit height - its real rendered height (Checkbox + text +
    // icon, with padding) doesn't necessarily equal 56dp. The swap
    // threshold (rowHeightPx * 0.5) and the post-swap "subtract one row's
    // worth" reset were both computed against this WRONG assumed height,
    // out of sync with how far the row's content actually visually moves
    // per swap - one contributor to the drag visual looking broken.
    // Measured for real via onSizeChanged on the row itself, below,
    // starting from the same 56dp guess purely as a reasonable value for
    // the handful of frames before the first row reports its real size.
    val density = LocalDensity.current
    var rowHeightPx by remember { mutableStateOf(with(density) { 56.dp.toPx() }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialItems) {
        localItems = initialItems
    }

    // FIX (issue 1 - "can't grab an item I already grabbed/moved/dropped",
    // and the list not scrolling while dragging near the edge): every move,
    // whether triggered by a D-pad Up/Down press or a touch drag crossing
    // the swap threshold, now goes through this single function, which also
    // makes sure the target row is actually scrolled into view. Previously
    // nothing did this, so dragging or moving an item toward either end of
    // a list longer than one screen had nowhere to go - the list itself
    // never scrolled to follow it.
    // CRASH FIX: reordering the LazyColumn's keyed items for the currently
    // focused row - via a D-pad Up/Down press - was throwing a Compose
    // Foundation internal crash ("ActiveParent must have a focusedChild" in
    // TwoDimensionalFocusSearch). The onKeyEvent handler below now defers
    // the actual call into this function to the next frame (scope.launch)
    // instead of calling it synchronously mid key-dispatch, which is the
    // real fix. runCatching here is a second, defensive layer only -
    // matching the same convention FocusRegistry.kt already uses around
    // other focus APIs known to be flaky in this exact way - so if some
    // other path into this still races with Compose's focus system, it
    // degrades to "that particular move didn't happen" instead of crashing
    // the whole app.
    fun moveItem(from: Int, to: Int) {
        if (to !in localItems.indices || from == to) return
        runCatching {
            localItems = localItems.toMutableList().apply { add(to, removeAt(from)) }
            heldIndex = to
        }.onSuccess {
            scope.launch {
                runCatching {
                    val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == to }
                    if (!alreadyVisible) {
                        listState.animateScrollToItem(to)
                    }
                }
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Toast.makeText(
            context,
            "Tap to toggle a category, press and hold to pick it up, then use ↑/↓ (or drag) to move it. Press again to drop it.",
            Toast.LENGTH_LONG
        ).show()
    }

    // FIX (real cause, confirmed by repeated crashes at the identical stack
    // frame - androidx.compose.ui.focus.TwoDimensionalFocusSearchKt -
    // regardless of what we changed in our own onKeyEvent handler): the
    // exception is thrown INSIDE COMPOSE'S OWN default directional focus
    // search (AndroidComposeView's built-in D-pad handling), which only
    // runs when nothing along the key-dispatch path consumes the event.
    // Every previous attempt still left some path where a row's onKeyEvent
    // returned false for an Up/Down press - normal row-to-row navigation
    // while nothing is held, or the moment focus is still sitting on the
    // reorder-handle icon rather than the row - letting Compose fall
    // through into that crash-prone default search. This is a known,
    // documented Compose Foundation limitation with directional focus
    // search over dynamically-changing focusable content, not something
    // fixable by timing our own mutations around it.
    //
    // REAL FIX: never let Compose's own directional search run for this
    // list at all. One stable FocusRequester per row (sized once, reused
    // for the life of the dialog since the row COUNT never changes here -
    // only order and visibility do) lets every row's onKeyEvent handle
    // Up/Down itself, unconditionally, whether or not it's the held row -
    // moving the item when held, or just walking focus to the neighboring
    // row's requester directly when not. Every Up/Down key (both press and
    // release) is consumed either way, so
    // AndroidComposeView$keyInputModifier's default focusSearch call is
    // never reached from this list.
    val focusRequesters = remember { List(localItems.size) { FocusRequester() } }

    // FIX (issue 3 - "set focus on the first item once the list is
    // populated"): nothing previously requested focus when this dialog
    // first appeared - D-pad/keyboard users landed with no row focused at
    // all and had to fumble to find the list. localItems (and therefore
    // focusRequesters, sized from it) is already fully populated by the
    // time this composable is first created - the category lists are
    // loaded well before the user can open this dialog - so a single
    // one-shot effect on first composition is enough.
    LaunchedEffect(Unit) {
        runCatching { focusRequesters.firstOrNull()?.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = {
            onSave(localItems)
            onDismiss()
        },
        containerColor = BbSurface,
        title = { Text(title, color = BbTextPrimary) },
        text = {
            Column {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    // ROOT CAUSE OF THE CRASH: this used to be
                    // items(localItems, key = { it.id }) - a STABLE,
                    // per-item key. That tells Compose "item X's focus node
                    // is the same node no matter which row it's drawn in",
                    // so the moment a D-pad Up/Down move re-sorted
                    // localItems, Compose tried to physically relocate the
                    // CURRENTLY FOCUSED node to its new row in the tree.
                    // Relocating a live focus node out from under an
                    // in-progress directional focus search is exactly what
                    // throws "ActiveParent must have a focusedChild" in
                    // TwoDimensionalFocusSearch - this is a known Compose
                    // Foundation limitation with reordering focused,
                    // keyed lazy items, not something a scope.launch defer
                    // (the previous fix attempt, kept below for the
                    // now-harmless extra safety it also gives the list
                    // auto-scroll) can paper over, since the problem is
                    // WHICH node identity moves, not WHEN the move happens.
                    //
                    // REAL FIX: key by POSITION (itemsIndexed's default
                    // index-based key) instead of item identity. Now each
                    // row's focus node belongs permanently to a slot index
                    // and never moves in the tree - a reorder just swaps
                    // which item's data that fixed slot displays. Real
                    // D-pad focus is then explicitly walked to the new slot
                    // via the FocusRequester + LaunchedEffect below, so the
                    // highlighted "held" row and actual system focus always
                    // agree.
                    itemsIndexed(localItems) { index, item ->
                        // NOTE: isHeld is keyed purely on `heldIndex ==
                        // index` - i.e. "whichever slot the DRAGGED ITEM
                        // currently occupies", which moveItem() keeps in
                        // sync as swaps happen, NOT on which slot's
                        // pointerInput physically originated the touch. An
                        // earlier attempt (isDragOrigin, pinning the "held"
                        // treatment to the ORIGIN slot for the whole drag)
                        // caused a different bug: whatever new item shifted
                        // INTO the origin slot after a swap inherited that
                        // slot's translationY/highlight and appeared to
                        // jump around, even though it wasn't the item being
                        // dragged at all. heldIndex == index is actually
                        // correct here PROVIDED the touch gesture itself
                        // survives across swaps without being torn down -
                        // see the pointerInput(index) key fix below, which
                        // is what really needed fixing (it was previously
                        // keyed by item.id, which changes - and cancels the
                        // gesture mid-drag - the instant any swap moves a
                        // different item into this slot).
                        val isHeld = heldIndex == index

                        val rowInteractionSource = remember { MutableInteractionSource() }
                        val isFocused by rowInteractionSource.collectIsFocusedAsState()
                        val rowFocusRequester = remember(index) { focusRequesters.getOrElse(index) { FocusRequester() } }
                        // Tracks when the current Enter/DPAD-center press
                        // started, so a single row can distinguish a quick
                        // tap (toggle visibility) from a long-hold (grab
                        // the item) - see the onKeyEvent Enter/DirectionCenter
                        // branch below. 0L means "no press in progress".
                        var pressStartUptimeMs by remember { mutableStateOf(0L) }
                        // Keep real system focus glued to whichever slot is
                        // currently "held", even as the data under that
                        // slot index changes out from under it on every
                        // move.
                        LaunchedEffect(heldIndex) {
                            if (heldIndex == index) {
                                runCatching { rowFocusRequester.requestFocus() }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { rowHeightPx = it.height.toFloat() }
                                .zIndex(if (isHeld) 1f else 0f)
                                .graphicsLayer {
                                    // Only the slot heldIndex currently
                                    // points at translates - that's
                                    // wherever the dragged item's data
                                    // ACTUALLY is right now (moveItem moved
                                    // it there), so this naturally "follows"
                                    // the item from slot to slot as it's
                                    // dragged, rather than following
                                    // whichever slot the finger originally
                                    // touched down on.
                                    translationY = if (isHeld) dragOffsetPx else 0f
                                }
                                .shadow(if (isHeld) 8.dp else 0.dp, RoundedCornerShape(8.dp))
                                .background(
                                    if (isHeld) BbAccent.copy(alpha = 0.18f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isHeld || isFocused) 2.dp else 0.dp,
                                    color = if (isHeld || isFocused) BbAccent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusRequester(rowFocusRequester)
                                .focusable(interactionSource = rowInteractionSource)
                                // While this row is held, DPAD Up/Down move
                                // it one position at a time instead of
                                // Compose's default behavior of moving
                                // focus to the next row.
                                //
                                // CRASH FIX: reordering the LazyColumn's
                                // keyed items for the CURRENTLY FOCUSED row
                                // synchronously, from inside this same
                                // onKeyEvent callback, mutates the focus
                                // tree while Compose's own key-dispatch is
                                // still mid-traversal of it (arrow-key
                                // dispatch runs through the focus system's
                                // own directional search) - that's what was
                                // throwing "ActiveParent must have a
                                // focusedChild" and crashing the app.
                                // Deferring the actual reorder to
                                // scope.launch runs it on the next frame,
                                // after the in-flight key dispatch has fully
                                // finished, as a clean separate
                                // recomposition instead of a mutation
                                // underneath an active tree traversal.
                                .onKeyEvent { keyEvent ->
                                    // FIX (real cause, found from your exact repro:
                                    // grab via "long-hold OK", drop via "OK"): the
                                    // pick-up/drop control used to live on a
                                    // SEPARATE Icon composable with its own
                                    // implicit focus target (clickable() always
                                    // creates one). D-pad navigation onto that
                                    // icon, and Enter/DPAD-center presses on it,
                                    // went through Compose's own key/focus
                                    // handling - completely outside this
                                    // onKeyEvent - so nothing here could ever
                                    // consume them, and they were free to fall
                                    // through into the same crash-prone default
                                    // focusSearch as before. There is now exactly
                                    // ONE focus target per row (the icon below is
                                    // decorative only, focusProperties{canFocus=
                                    // false}), so this single handler owns every
                                    // key relevant to reordering: Up/Down move the
                                    // held item or walk focus row-to-row, Enter/
                                    // DPAD-center grabs or drops (long-hold to
                                    // grab, short press to toggle visibility -
                                    // preserving the old row-tap-toggles-
                                    // visibility behavior for D-pad), and Left/
                                    // Right are swallowed as harmless no-ops so
                                    // there is no key left that can reach
                                    // Compose's own directional search from
                                    // inside this list at all.
                                    when (keyEvent.key) {
                                        Key.DirectionUp, Key.DirectionDown -> {
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                val delta = if (keyEvent.key == Key.DirectionUp) -1 else 1
                                                if (isHeld) {
                                                    scope.launch { moveItem(index, index + delta) }
                                                } else {
                                                    val target = index + delta
                                                    if (target in focusRequesters.indices) {
                                                        runCatching { focusRequesters[target].requestFocus() }
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                            when (keyEvent.type) {
                                                KeyEventType.KeyDown -> {
                                                    // Ignore key-repeat: only record the
                                                    // start of a genuinely new press.
                                                    if (pressStartUptimeMs == 0L) {
                                                        pressStartUptimeMs = android.os.SystemClock.uptimeMillis()
                                                    }
                                                }
                                                KeyEventType.KeyUp -> {
                                                    val startedAt = pressStartUptimeMs
                                                    pressStartUptimeMs = 0L
                                                    if (isHeld) {
                                                        // Any press while held drops it,
                                                        // regardless of duration - matches
                                                        // the old "select again to drop"
                                                        // behavior.
                                                        heldIndex = null
                                                        dragOffsetPx = 0f
                                                    } else if (startedAt != 0L &&
                                                        android.os.SystemClock.uptimeMillis() - startedAt >= 400L
                                                    ) {
                                                        heldIndex = index
                                                        dragOffsetPx = 0f
                                                    } else {
                                                        val newList = localItems.map {
                                                            if (it.id == item.id) it.copy(isVisible = !it.isVisible) else it
                                                        }
                                                        localItems = newList
                                                    }
                                                }
                                                else -> {}
                                            }
                                            true
                                        }
                                        Key.DirectionLeft, Key.DirectionRight -> true
                                        Key.Back -> {
                                            if (isHeld) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    heldIndex = null
                                                    dragOffsetPx = 0f
                                                }
                                                true
                                            } else {
                                                // Nothing held: let Back fall through
                                                // to close the dialog normally.
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                }
                                // FIX (issue 1, real root cause - "can grab
                                // but unable to move on mobile"): the
                                // previous version SWAPPED between two
                                // different pointerInput blocks depending on
                                // isHeld - a detectTapGestures(onLongPress)
                                // block while not held, replaced by a
                                // detectDragGestures block once held became
                                // true. That swap happens via recomposition
                                // WHILE the long-press gesture's pointer is
                                // STILL DOWN (long-press fires before the
                                // finger lifts) - but Compose's pointer input
                                // system binds an in-progress touch stream to
                                // whichever pointerInput node originally
                                // captured its down event. A brand new
                                // pointerInput block attached mid-gesture
                                // only starts tracking the NEXT new touch, so
                                // the still-down finger from the very
                                // long-press that grabbed the item was never
                                // seen by the newly-swapped-in drag detector
                                // at all - the item visibly became "held"
                                // (the recomposition-driven highlight/border
                                // still applied) but nothing was listening
                                // for the finger's continued movement.
                                //
                                // REAL FIX: two pointerInput blocks that are
                                // ALWAYS present (never swapped), each
                                // covering the whole gesture lifecycle
                                // itself. detectDragGesturesAfterLongPress is
                                // Compose Foundation's purpose-built API for
                                // exactly "long-press to pick up, then keep
                                // dragging with the SAME continuous touch" -
                                // it's one gesture detector from down to up,
                                // so there is no hand-off point where a
                                // still-active touch could be dropped. It's
                                // paired here with a plain
                                // detectTapGestures(onTap) for the quick-tap-
                                // toggles-visibility behavior; these two are
                                // Compose's own documented compatible
                                // combination (a long-press-drag that
                                // actually starts consumes the gesture before
                                // the tap detector's onTap can fire for it;
                                // a genuinely short, unconsumed tap is exactly
                                // what onTap fires for) - unlike the OLD
                                // "two competing detectors" bug, which was
                                // actually caused by the swap above, not by
                                // having two detectors per se.
                                // FIX (issue - "moves about 1 item up/down
                                // and comes back to the same position"):
                                // both pointerInput blocks below were keyed
                                // by `item.id`. The moment the FIRST
                                // moveItem() swap fires, a DIFFERENT item's
                                // data moves into THIS slot - so `item.id`
                                // for this composable instance changes -
                                // which is exactly the signal Compose uses
                                // to CANCEL and recreate a pointerInput
                                // block. That cancellation is what fired
                                // onDragCancel mid-gesture, resetting
                                // heldIndex/dragOffsetPx right back to
                                // their unheld state one swap in -
                                // "moves ~1 item, then comes back". Keyed by
                                // `index` instead - stable for this slot's
                                // entire lifetime (this composable instance
                                // is permanently anchored to slot `index`;
                                // only the DATA displayed there changes) -
                                // so the gesture coroutine now survives
                                // any number of swaps for the rest of a
                                // single continuous drag.
                                .pointerInput(index) {
                                    detectTapGestures(
                                        onTap = {
                                            // Guard against a quick grab-then-
                                            // immediate-release also toggling
                                            // visibility - heldIndex is reset
                                            // to null by onDragEnd below the
                                            // instant the finger lifts, so by
                                            // the time this fires for that
                                            // same lift it could otherwise
                                            // still look like "just a tap".
                                            //
                                            // Reads localItems.getOrNull(index)
                                            // fresh here rather than closing
                                            // over the `item` lambda parameter -
                                            // since this pointerInput block no
                                            // longer restarts when the data at
                                            // this slot changes (see above), a
                                            // captured `item` would go stale
                                            // the moment anything swaps through
                                            // this slot, toggling the WRONG
                                            // (long-departed) item.
                                            if (heldIndex == null) {
                                                val currentId = localItems.getOrNull(index)?.id
                                                if (currentId != null) {
                                                    localItems = localItems.map {
                                                        if (it.id == currentId) it.copy(isVisible = !it.isVisible) else it
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(index) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { heldIndex = index; dragOffsetPx = 0f },
                                        // FIX (issue 1 continued): touch
                                        // drag-and-drop is lift-to-drop -
                                        // releasing the finger (or the
                                        // gesture being cancelled, e.g. by a
                                        // parent scroll intercepting it) ends
                                        // the hold automatically, unlike the
                                        // D-pad's explicit "press again to
                                        // drop" model above, which doesn't
                                        // have an equivalent "release" event
                                        // to hook.
                                        onDragEnd = { heldIndex = null; dragOffsetPx = 0f },
                                        onDragCancel = { heldIndex = null; dragOffsetPx = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetPx += amount.y
                                            // FIX (issue 1 - "should allow
                                            // sorting more than 1 position in
                                            // the same grab"): was a single
                                            // if/else, so a single onDrag
                                            // callback could only ever move
                                            // the item ONE position no matter
                                            // how far the finger had actually
                                            // traveled since the last
                                            // callback - Android can and does
                                            // batch multiple move events into
                                            // one larger delta on a fast
                                            // drag, so a quick flick across
                                            // several rows would only
                                            // register as one move, then need
                                            // the finger to keep moving
                                            // further just to "catch up" -
                                            // which is what read as
                                            // fast/uncontrollable rather than
                                            // tracking the finger directly.
                                            // Looping here means a single
                                            // callback that crosses several
                                            // row-height thresholds at once
                                            // moves the item that many
                                            // positions immediately, keeping
                                            // it under the finger.
                                            while (true) {
                                                val from = heldIndex ?: break
                                                when {
                                                    dragOffsetPx > rowHeightPx * 0.5f && from < localItems.lastIndex -> {
                                                        moveItem(from, from + 1)
                                                        dragOffsetPx -= rowHeightPx
                                                    }
                                                    dragOffsetPx < -rowHeightPx * 0.5f && from > 0 -> {
                                                        moveItem(from, from - 1)
                                                        dragOffsetPx += rowHeightPx
                                                    }
                                                    else -> break
                                                }
                                            }
                                        }
                                    )
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // FIX (issue 3 - "I don't want the checkbox
                            // focusable using D-pad"): Checkbox is a
                            // Material component with its own built-in
                            // focus/click handling, so it was showing up as
                            // its own separate D-pad focus stop ahead of the
                            // reorder handle - easy to land on by accident,
                            // and toggling it that way felt like it was
                            // "randomly" unchecking things while trying to
                            // reach the handle. focusProperties { canFocus =
                            // false } removes it from keyboard/D-pad focus
                            // traversal entirely while leaving it fully
                            // usable by direct touch tap. D-pad users now
                            // toggle visibility via a plain select on the
                            // row itself (the clickable branch above).
                            Checkbox(
                                checked = item.isVisible,
                                onCheckedChange = { checked ->
                                    val newList = localItems.map { if (it.id == item.id) it.copy(isVisible = checked) else it }
                                    localItems = newList
                                },
                                colors = CheckboxDefaults.colors(checkedColor = BbAccent),
                                modifier = Modifier.focusProperties { canFocus = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${index + 1}. ${item.title}",
                                color = if (item.isVisible) BbTextPrimary else BbTextMuted,
                                fontSize = 15.sp,
                                fontWeight = if (isHeld) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            // FIX (issue 2): the separate Move Up/Move Down
                            // IconButtons are gone - the reorder handle
                            // below (pick up, then ↑/↓ or drag) replaces
                            // them as the one reordering control.
                            //
                            // FIX (issue 1): this is now a PLAIN clickable -
                            // no pointerInput/drag detector attached here at
                            // all. Selecting it (touch tap or D-pad center)
                            // just toggles heldIndex; all dragging happens
                            // on the row itself, only once held (above).
                            //
                            // CRASH FIX: clickable() here used to give this
                            // icon its OWN independent D-pad focus target,
                            // separate from the row's. Every key that
                            // landed there or navigated onto/off of it went
                            // through Compose's own key and focus handling
                            // instead of the row's onKeyEvent above, and
                            // could fall through into the same crash-prone
                            // default focusSearch this whole fix is meant to
                            // avoid. This is now purely decorative - grab
                            // and drop are driven entirely by the row's own
                            // onKeyEvent (long-hold Enter/DPAD-center) - and
                            // focusProperties{canFocus=false} removes it
                            // from D-pad focus traversal the same way the
                            // Checkbox below already was.
                            Icon(
                                Icons.Default.Reorder,
                                "Hold to reorder",
                                tint = if (isHeld) BbAccent else BbTextMuted,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(4.dp)
                                    .focusProperties { canFocus = false }
                            )
                        }
                        HorizontalDivider(color = BbCard, thickness = 0.5.dp)
                    }
                }
            }
        },
        // FIX (issue 2 - "evaluate if Done is useless, remove if so"): it
        // was. onDismissRequest above already does the exact same
        // onSave(localItems); onDismiss() - Done was just a second way to
        // trigger identical behavior to tapping outside the dialog or
        // pressing Back. Since this dialog has no dismissButton either,
        // Material3's AlertDialog still requires a non-null confirmButton
        // composable - an empty one renders no button at all, leaving
        // tap-outside/Back as the only (and only necessary) way to close,
        // both of which already save.
        confirmButton = {}
    )
}
