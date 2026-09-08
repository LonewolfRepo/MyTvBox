package com.itv.blockbuster.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.domain.model.Server
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbDestructive
import com.itv.blockbuster.ui.theme.BbSurface
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import kotlinx.coroutines.launch
import java.util.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServersScreen(
    viewModel: ServersViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var serverToEdit by remember { mutableStateOf<Server?>(null) }
    var serverToDelete by remember { mutableStateOf<Server?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    if (serverToEdit != null) {
        ServerDialog(
            server = serverToEdit!!,
            onSave = { updatedServer ->
                if (updatedServer.id == 0) viewModel.addServer(updatedServer)
                else viewModel.updateServer(updatedServer)
                serverToEdit = null
            },
            onDismiss = { if (!uiState.isConnecting) serverToEdit = null }
        )
    }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            containerColor = BbSurface,
            title = { Text("Delete Portal", color = BbTextPrimary) },
            text = { Text("Are you sure you want to delete '${serverToDelete?.name}'?", color = BbTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServer(serverToDelete!!)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BbDestructive)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Cancel", color = BbTextSecondary) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "Manage Portals",
                style = MaterialTheme.typography.headlineMedium,
                color = BbTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { serverToEdit = Server(name = "", host = "", mac = generateDefaultMac()) },
                colors = ButtonDefaults.buttonColors(containerColor = BbAccent),
                enabled = !uiState.isConnecting
            ) {
                if (uiState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isConnecting) "Connecting..." else "Add Portal")
            }

            Spacer(Modifier.height(24.dp))

            if (servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = BbTextMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No portals configured.", color = BbTextMuted, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Add a Stalker portal to start watching.", color = BbTextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                // FIX (2 & 3): Servers share the same line with more padding (24.dp).
                // Each PortalCard unit (card + Edit + Delete) is ONE FlowRow child,
                // so when the line can't fit a full unit, the whole unit wraps to a new line.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    servers.forEach { server ->
                        val isActive = activeServer?.id == server.id
                        PortalCard(
                            server = server,
                            isActive = isActive,
                            isConnecting = uiState.isConnecting,
                            onActivate = {
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Connecting to ${server.name.ifEmpty { "portal" }}..."
                                    )
                                }
                                viewModel.activateServer(server)
                            },
                            onEdit = { serverToEdit = server },
                            onDelete = { serverToDelete = server }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
fun PortalCard(
    server: Server,
    isActive: Boolean,
    isConnecting: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var cardFocused by remember { mutableStateOf(false) }
    var editFocused by remember { mutableStateOf(false) }
    var deleteFocused by remember { mutableStateOf(false) }

    // FIX (1): Fixed-length card (280.dp) that fits at least 15 characters of the
    // portal name. On narrow screens it shrinks just enough so the card plus its
    // Edit/Delete buttons always fit on one line.
    BoxWithConstraints {
        val fixedCardWidth = 240.dp
        val buttonsBlockWidth = 6.dp + 56.dp + 6.dp + 56.dp // spacers + Edit + Delete
        val cardWidth = if (maxWidth >= fixedCardWidth + buttonsBlockWidth) {
            fixedCardWidth
        } else {
            (maxWidth - buttonsBlockWidth).coerceAtLeast(160.dp)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Server card: FIXED width pill, click = connect / reconnect ──
            Row(
                modifier = Modifier
                    .width(cardWidth)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        when {
                            cardFocused -> BbAccent
                            isActive -> BbAccent.copy(alpha = 0.15f)
                            else -> BbCard
                        }
                    )
                    .then(
                        if (cardFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(28.dp))
                        else Modifier
                    )
                    .clickable(enabled = !isConnecting, onClick = onActivate)
                    .onFocusChanged { cardFocused = it.isFocused }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnecting && isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = if (cardFocused) Color.White else BbAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (cardFocused) Color.White else if (isActive) BbAccent else BbTextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = server.name.ifEmpty { "Unnamed Portal" },
                    color = if (cardFocused) Color.White else BbTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f) // Fills the fixed card width; ≥15 chars visible
                )
                if (isActive) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = if (cardFocused) Color.White else BbAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── Edit button (immediately right of the card) ──
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (editFocused) BbAccent else BbCard)
                    .then(
                        if (editFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(28.dp))
                        else Modifier
                    )
                    .clickable(enabled = !isConnecting, onClick = onEdit)
                    .onFocusChanged { editFocused = it.isFocused },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = if (editFocused) Color.White else BbTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // ── Delete button (immediately right of Edit) ──
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (deleteFocused) BbDestructive else BbCard)
                    .then(
                        if (deleteFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(28.dp))
                        else Modifier
                    )
                    .clickable(enabled = !isConnecting, onClick = onDelete)
                    .onFocusChanged { deleteFocused = it.isFocused },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (deleteFocused) Color.White else BbDestructive,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ServerDialog(
    server: Server,
    onSave: (Server) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = server.id == 0
    var name by remember { mutableStateOf(server.name) }
    var host by remember { mutableStateOf(server.host) }
    var mac by remember { mutableStateOf(server.mac) }
    var useCredentials by remember { mutableStateOf(server.useCredentials) }
    var username by remember { mutableStateOf(server.username) }
    var password by remember { mutableStateOf(server.password) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            color = BbSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isNew) "Add Portal" else "Edit Portal",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = BbTextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                HorizontalDivider(color = BbCard)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Portal Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BbAccent,
                            unfocusedBorderColor = BbTextMuted,
                            focusedLabelColor = BbAccent,
                            unfocusedLabelColor = BbTextMuted,
                            cursorColor = BbAccent
                        )
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Portal URL (e.g., http://domain.com:8080)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BbAccent,
                            unfocusedBorderColor = BbTextMuted,
                            focusedLabelColor = BbAccent,
                            unfocusedLabelColor = BbTextMuted,
                            cursorColor = BbAccent
                        )
                    )
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("MAC Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Auto-generated, but editable if required", color = BbTextMuted, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BbAccent,
                            unfocusedBorderColor = BbTextMuted,
                            focusedLabelColor = BbAccent,
                            unfocusedLabelColor = BbTextMuted,
                            cursorColor = BbAccent
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useCredentials,
                            onCheckedChange = { useCredentials = it },
                            colors = CheckboxDefaults.colors(checkedColor = BbAccent)
                        )
                        Text("Use Username & Password", color = BbTextPrimary)
                    }
                    if (useCredentials) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BbAccent,
                                unfocusedBorderColor = BbTextMuted,
                                focusedLabelColor = BbAccent,
                                unfocusedLabelColor = BbTextMuted,
                                cursorColor = BbAccent
                            )
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BbAccent,
                                unfocusedBorderColor = BbTextMuted,
                                focusedLabelColor = BbAccent,
                                unfocusedLabelColor = BbTextMuted,
                                cursorColor = BbAccent
                            )
                        )
                    }
                }
                HorizontalDivider(color = BbCard)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BbTextSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (host.isNotBlank() && mac.isNotBlank()) {
                                onSave(
                                    server.copy(
                                        name = name,
                                        host = host,
                                        mac = mac,
                                        useCredentials = useCredentials,
                                        username = username,
                                        password = password
                                    )
                                )
                            }
                        },
                        enabled = host.isNotBlank() && mac.length >= 17,
                        colors = ButtonDefaults.buttonColors(containerColor = BbAccent)
                    ) {
                        Text(if (isNew) "Save & Connect" else "Update & Connect")
                    }
                }
            }
        }
    }
}

fun generateDefaultMac(): String {
    val random = Random()
    return String.format("00:1A:79:%02X:%02X:%02X", random.nextInt(256), random.nextInt(256), random.nextInt(256))
}
