package com.itv.blockbuster.ui.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.domain.model.Profile
import com.itv.blockbuster.ui.navigation.FormFactor
import com.itv.blockbuster.ui.navigation.rememberFormFactor
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary

val AVATAR_COLORS = listOf(
    Color(0xFF2F7EF9),
    Color(0xFF7C4DFF),
    Color(0xFF00B8D4),
    Color(0xFF00C853),
    Color(0xFFFF6D00),
    Color(0xFFF50057)
)

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val formFactor = rememberFormFactor()
    val isTv = formFactor == FormFactor.TV

    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val avatarSize = if (isTv) 128.dp else 88.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BbAccent.copy(alpha = 0.25f), BbBackground),
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isTv) {
                TextButton(
                    onClick = { viewModel.setEditMode(!editMode) },
                    modifier = Modifier.align(Alignment.End).padding(end = 24.dp)
                ) {
                    Text(
                        text = if (editMode) "Done" else "Edit Profile",
                        color = BbAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "Choose Profile",
                color = BbTextPrimary,
                fontSize = if (isTv) 40.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = if (isTv) 56.dp else 32.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 36.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    ProfileAvatar(
                        profile = profile,
                        size = avatarSize,
                        onClick = {
                            if (editMode) {
                                editingProfile = profile
                            } else {
                                viewModel.selectProfile(profile)
                                onProfileSelected()
                            }
                        }
                    )
                }

                if (editMode) {
                    AddProfileTile(size = avatarSize) { showAddDialog = true }
                }
            }

            if (isTv) {
                IconButton(
                    onClick = { viewModel.setEditMode(!editMode) },
                    modifier = Modifier
                        .padding(top = 48.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            BbTextSecondary.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (editMode) Icons.Default.Add else Icons.Default.Edit,
                        contentDescription = if (editMode) "Add profile" else "Edit profiles",
                        tint = BbTextPrimary
                    )
                }
            }
        }
    }

    editingProfile?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            canDelete = viewModel.canDelete(),
            onRename = { name, color -> viewModel.renameProfile(profile.id, name, color) },
            onDelete = { viewModel.deleteProfile(profile.id) },
            onDismiss = { editingProfile = null }
        )
    }

    if (showAddDialog) {
        ProfileNameDialog(
            title = "Add Profile",
            initialName = "Profile ${profiles.size + 1}",
            onConfirm = { name, color -> viewModel.addProfile(name, color) },
            onDismiss = { showAddDialog = false }
        )
    }
}

// =====================================================================
// AVATAR (shared with ProfileHubScreen)
// =====================================================================

@Composable
fun ProfileAvatar(
    profile: Profile,
    size: Dp,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused || highlighted) 1.12f else 1f,
        label = "avatarScale"
    )
    val avatarColor = AVATAR_COLORS.getOrElse(profile.colorIndex) { AVATAR_COLORS[0] }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor)
                .then(
                    if (focused) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = profile.name,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(size * 0.72f)
            )
        }
        Text(
            text = profile.name,
            color = if (focused || highlighted) BbAccent else BbTextPrimary,
            fontSize = if (size > 100.dp) 18.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun AddProfileTile(size: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (focused) 3.dp else 2.dp,
                    color = if (focused) Color.White else BbTextSecondary.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add profile",
                tint = BbTextPrimary,
                modifier = Modifier.size(size * 0.4f)
            )
        }
        Text(
            text = "Add Profile",
            color = if (focused) BbAccent else BbTextSecondary,
            fontSize = if (size > 100.dp) 18.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

// =====================================================================
// DIALOGS
// =====================================================================

@Composable
private fun AvatarColorRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AVATAR_COLORS.forEachIndexed { index, color ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
}

@Composable
fun ProfileEditDialog(
    profile: Profile,
    canDelete: Boolean,
    onRename: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var colorIndex by remember { mutableStateOf(profile.colorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BbSurfaceDialog,
        title = { Text("Edit Profile", color = BbTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("Avatar color", color = BbTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    AvatarColorRow(selectedIndex = colorIndex) { colorIndex = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(name, colorIndex)
                    onDismiss()
                }
            ) { Text("Save", color = BbAccent) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canDelete) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    ) { Text("Delete", color = BbDestructive) }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = BbTextSecondary)
                }
            }
        }
    )
}

@Composable
fun ProfileNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var colorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BbSurfaceDialog,
        title = { Text(title, color = BbTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("Avatar color", color = BbTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    AvatarColorRow(selectedIndex = colorIndex) { colorIndex = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, colorIndex)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("Add", color = BbAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BbTextSecondary)
            }
        }
    )
}

// Local alias to keep dialog surface distinct from shell surface
private val BbSurfaceDialog = Color(0xFF141419)