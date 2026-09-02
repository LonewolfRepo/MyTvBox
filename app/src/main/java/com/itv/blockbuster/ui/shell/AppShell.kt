package com.itv.blockbuster.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.itv.blockbuster.ui.navigation.AppSection
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.Routes
import com.itv.blockbuster.ui.navigation.navigateToSection
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

private val RailSections = listOf(
    AppSection.SEARCH,
    AppSection.MOVIES,
    AppSection.TV_SHOWS,
    AppSection.LIVE_TV,
    AppSection.TV_GUIDE,
    AppSection.MY_LIST,
    AppSection.RECENT
)

private val MenuSections = listOf(
    AppSection.HOME,
    AppSection.MOVIES,
    AppSection.TV_SHOWS,
    AppSection.LIVE_TV,
    AppSection.TV_GUIDE,
    AppSection.MY_LIST,
    AppSection.RECENT
)

@Composable
fun AppShell(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val formFactor = rememberFormFactor()
    val currentRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route

    when (formFactor) {
        FormFactor.MOBILE_PORTRAIT -> PortraitShell(navController, currentRoute, content)
        else -> RailShell(navController, currentRoute, content)
    }
}

// =====================================================================
// TV / LANDSCAPE: auto-collapsing left rail
// =====================================================================

@Composable
private fun RailShell(
    navController: NavHostController,
    currentRoute: String?,
    content: @Composable () -> Unit
) {
    var railExpanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railExpanded) 280.dp else 84.dp,
        label = "railWidth"
    )

    Row(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(BbSurface)
                .onFocusChanged { railExpanded = it.hasFocus }
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BLOCKBUSTER",
                color = BbAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (railExpanded) 18.sp else 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Profile / Change profile
            RailItem(
                icon = Icons.Default.AccountCircle,
                label = "Change Profile",
                expanded = railExpanded,
                selected = false,
                onClick = { navController.navigate(Routes.PROFILE_PICKER) }
            )

            RailSections.forEach { section ->
                RailItem(
                    icon = section.icon,
                    label = section.label,
                    expanded = railExpanded,
                    selected = currentRoute == section.route,
                    onClick = { navController.navigateToSection(section.route) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            RailItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                expanded = railExpanded,
                selected = currentRoute == Routes.SETTINGS || currentRoute == Routes.SERVERS,
                onClick = { navController.navigateToSection(Routes.SETTINGS) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val pillColor by animateColorAsState(
        targetValue = when {
            focused -> BbAccent
            selected -> BbAccent.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        label = "pill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(pillColor)
            .then(
                if (focused) Modifier.border(2.dp, BbAccent, RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) BbTextPrimary else if (selected) BbAccent else BbTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = if (focused) BbTextPrimary else BbTextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =====================================================================
// PORTRAIT: top bar + overlay menu + bottom bar
// =====================================================================

@Composable
private fun PortraitShell(
    navController: NavHostController,
    currentRoute: String?,
    content: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    val currentLabel = when (currentRoute) {
        Routes.PROFILE_HUB -> "Profile"
        Routes.SETTINGS -> "Settings"
        Routes.SERVERS -> "Portals"
        else -> AppSection.values()
            .firstOrNull { it.route == currentRoute }?.label ?: "Home"
    }

    Column(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BbBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BLOCKBUSTER",
                color = BbAccent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel,
                    color = BbTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open menu",
                    tint = BbTextPrimary
                )
            }
        }

        // Content + overlay menu
        Box(modifier = Modifier.weight(1f)) {
            content()

            if (menuOpen) {
                OverlayMenu(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        menuOpen = false
                        navController.navigateToSection(route)
                    },
                    onClose = { menuOpen = false }
                )
            }
        }

        // Bottom bar: Home / Search / Profile
        NavigationBar(containerColor = BbSurface) {
            NavigationBarItem(
                selected = currentRoute == Routes.HOME,
                onClick = { navController.navigateToSection(Routes.HOME) },
                icon = { Icon(AppSection.HOME.icon, "Home") },
                label = { Text("Home") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.SEARCH,
                onClick = { navController.navigateToSection(Routes.SEARCH) },
                icon = { Icon(Icons.Default.Search, "Search") },
                label = { Text("Search") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
            NavigationBarItem(
                selected = currentRoute == Routes.PROFILE_HUB,
                onClick = { navController.navigateToSection(Routes.PROFILE_HUB) },
                icon = { Icon(Icons.Default.AccountCircle, "Profile") },
                label = { Text("Profile") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BbAccent,
                    selectedTextColor = BbAccent,
                    unselectedIconColor = BbTextMuted,
                    unselectedTextColor = BbTextMuted,
                    indicatorColor = BbAccent.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
private fun OverlayMenu(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MenuSections.forEach { section ->
            val active = currentRoute == section.route
            Text(
                text = section.label,
                color = if (active) BbAccent else BbTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(section.route) }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClose)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "Close menu", tint = Color.White)
        }
    }
}