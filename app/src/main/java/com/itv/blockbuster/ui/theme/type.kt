package com.itv.blockbuster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, color = BbTextPrimary),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, color = BbTextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = BbTextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = BbTextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = BbTextPrimary),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, color = BbTextPrimary),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = BbTextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BbTextPrimary),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, color = BbTextMuted)
)