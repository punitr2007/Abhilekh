package com.abhilekh.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val RoyalBlue = Color(0xFF2563EB)
val RoyalBlueDark = Color(0xFF1D4ED8)
val EmeraldTrust = Color(0xFF059669)
val EmeraldLight = Color(0xFFD1FAE5)
val AmberWarning = Color(0xFFD97706)
val AmberLight = Color(0xFFFEF3C7)
val WhatsAppGreen = Color(0xFF25D366)

val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Slate100,
    onPrimaryContainer = Slate900,
    secondary = EmeraldTrust,
    onSecondary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700
)

val DarkColorScheme = darkColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = Color.White,
    secondary = EmeraldTrust,
    onSecondary = Color.White,
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate100
)
