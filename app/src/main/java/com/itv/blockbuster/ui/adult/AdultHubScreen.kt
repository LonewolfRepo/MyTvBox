package com.itv.blockbuster.ui.adult

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itv.blockbuster.ui.theme.BbAccent
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbCard
import com.itv.blockbuster.ui.theme.BbTextPrimary
import com.itv.blockbuster.ui.theme.BbTextSecondary
import com.itv.blockbuster.util.FocusRegistry

@Composable
fun AdultHubScreen(
    onNavigateToLive: () -> Unit,
    onNavigateToVod: () -> Unit,
    viewModel: AdultHubViewModel = hiltViewModel()
) {
    // FIX: Observe global unlock state
    val isUnlocked by viewModel.adultSessionManager.isUnlocked.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()
    val requestUnlock by viewModel.adultSessionManager.requestUnlock.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(!isUnlocked) }
    var passwordInput by remember { mutableStateOf("") }

    LaunchedEffect(requestUnlock) {
        if (requestUnlock > 0) {
            showPasswordDialog = true
            passwordInput = ""
            viewModel.resetError()
        }
    }

    // FIX: If locked externally (by navigating to Home/Movies), force dialog to show
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            showPasswordDialog = true
            passwordInput = ""
        }
    }

    if (showPasswordDialog && !isUnlocked) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Adult Content Access", color = BbTextPrimary) },
            text = {
                Column {
                    Text("Enter parental password to continue.", color = BbTextSecondary)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput, onValueChange = { passwordInput = it }, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BbAccent, unfocusedBorderColor = BbTextSecondary,
                            focusedTextColor = BbTextPrimary, unfocusedTextColor = BbTextPrimary
                        )
                    )
                    if (passwordError != null) { Spacer(Modifier.height(8.dp)); Text(passwordError!!, color = Color.Red, fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.verifyPassword(passwordInput) }) { Text("Unlock", color = BbAccent) } },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel", color = BbTextSecondary) } },
            containerColor = BbCard
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BbBackground).padding(24.dp)) {
        if (isUnlocked) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Adult Content", color = BbAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AdultMenuCard(icon = Icons.Default.LiveTv, title = "Adult Live", isLeftmost = true, onClick = onNavigateToLive)
                    AdultMenuCard(icon = Icons.Default.Movie, title = "Adult VOD", isLeftmost = false, onClick = onNavigateToVod)
                }
            }
        }
    }
}

@Composable
private fun AdultMenuCard(
    icon: ImageVector,
    title: String,
    // D-pad focus: only the leftmost card escapes to the rail on Left. Both
    // cards sit in the screen's only row, so both are simultaneously the top
    // AND bottom row - Up/Down are blocked from escaping to the rail on
    // either, same as every other browser screen's boundary rows.
    isLeftmost: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(200.dp).height(200.dp).clip(RoundedCornerShape(16.dp)).background(BbCard)
            .border(2.dp, BbAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .focusProperties {
                if (isLeftmost) left = FocusRegistry.leftEscapeTarget()
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = title, tint = BbAccent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = BbTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}