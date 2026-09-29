package com.abhilekh.app.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single Source of Truth: Owned Design System Tokens for Abhilekh.
 * Follows the owned-source component philosophy (zero opaque UI library dependencies).
 */
object AbhilekhTokens {

    // ─── Color Palette ────────────────────────────────────────────────────────
    object Colors {
        // Brand Primary
        val RoyalBlue = Color(0xFF2563EB)
        val RoyalBlueDark = Color(0xFF1D4ED8)
        val RoyalBlueLight = Color(0xFFDBEAFE)

        // Trust & Security (Masked Aadhaar Badge)
        val Emerald = Color(0xFF059669)
        val EmeraldDark = Color(0xFF047857)
        val EmeraldLight = Color(0xFFD1FAE5)

        // Sovereign Amber (Fail-Loud Aadhaar Warning HUD)
        val Amber = Color(0xFFD97706)
        val AmberDark = Color(0xFFB45309)
        val AmberLight = Color(0xFFFEF3C7)

        // Neutral Slate / Obsidian Surfaces
        val Slate950 = Color(0xFF090D16)
        val Slate900 = Color(0xFF0F172A)
        val Slate800 = Color(0xFF1E293B)
        val Slate700 = Color(0xFF334155)
        val Slate600 = Color(0xFF475569)
        val Slate500 = Color(0xFF64748B)
        val Slate400 = Color(0xFF94A3B8)
        val Slate200 = Color(0xFFE2E8F0)
        val Slate100 = Color(0xFFF1F5F9)
        val Slate50 = Color(0xFFF8FAFC)

        // Utility & Actions
        val WhatsAppGreen = Color(0xFF25D366)
        val WhatsAppGreenDark = Color(0xFF128C7E)
        val RedactionMaskBlack = Color(0xFF000000)
        val ErrorRed = Color(0xFFEF4444)
    }

    // ─── Spacing Grid ─────────────────────────────────────────────────────────
    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val base: Dp = 16.dp
        val lg: Dp = 20.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
        val xxxl: Dp = 48.dp
    }

    // ─── Corner Radii & Shapes ────────────────────────────────────────────────
    object Shapes {
        val xs = RoundedCornerShape(4.dp)
        val sm = RoundedCornerShape(8.dp)
        val md = RoundedCornerShape(12.dp)
        val lg = RoundedCornerShape(16.dp)
        val xl = RoundedCornerShape(24.dp)
        val pill = RoundedCornerShape(999.dp)
    }

    // ─── Elevation ────────────────────────────────────────────────────────────
    object Elevation {
        val none: Dp = 0.dp
        val card: Dp = 2.dp
        val popup: Dp = 6.dp
        val modal: Dp = 12.dp
        val hud: Dp = 20.dp
    }
}
