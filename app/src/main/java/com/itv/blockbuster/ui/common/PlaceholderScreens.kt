package com.itv.blockbuster.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itv.blockbuster.ui.navigation.AppSection
import com.itv.blockbuster.ui.theme.BbBackground
import com.itv.blockbuster.ui.theme.BbTextMuted
import com.itv.blockbuster.ui.theme.BbTextPrimary

@Composable
fun SectionPlaceholder(
    section: AppSection,
    title: String = section.label
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BbBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = BbTextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = BbTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Arrives in Phase 2",
                color = BbTextMuted,
                fontSize = 14.sp
            )
        }
    }
}