package com.abhilekh.app.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhilekh.app.core.thermal.ThermalTier

/**
 * Dynamic Progress Capsule: Modeled after Apple Dynamic Island & Android 16 Live Updates.
 * Renders batch progress, OCR state, and active thermal governor status.
 */
@Composable
fun DynamicProgressCapsule(
    isVisible: Boolean,
    currentStep: Int,
    totalSteps: Int,
    statusMessage: String,
    thermalTier: ThermalTier = ThermalTier.NOMINAL,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = AbhilekhTokens.Shapes.pill,
            color = AbhilekhTokens.Colors.Slate950,
            shadowElevation = AbhilekhTokens.Elevation.hud,
            modifier = Modifier
                .padding(horizontal = AbhilekhTokens.Spacing.base, vertical = AbhilekhTokens.Spacing.sm)
                .border(
                    width = 1.dp,
                    color = AbhilekhTokens.Colors.Slate700,
                    shape = AbhilekhTokens.Shapes.pill
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = AbhilekhTokens.Spacing.base, vertical = AbhilekhTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AbhilekhTokens.Spacing.md)
            ) {
                // Leading Progress / Icon Indicator
                if (currentStep < totalSteps) {
                    CircularProgressIndicator(
                        progress = { if (totalSteps > 0) currentStep.toFloat() / totalSteps else 0f },
                        modifier = Modifier.size(20.dp),
                        color = AbhilekhTokens.Colors.RoyalBlue,
                        trackColor = AbhilekhTokens.Colors.Slate800,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = AbhilekhTokens.Colors.Emerald,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Message & Page Count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (totalSteps > 0) {
                        Text(
                            text = "Page $currentStep of $totalSteps",
                            color = AbhilekhTokens.Colors.Slate400,
                            fontSize = 10.sp
                        )
                    }
                }

                // Trailing Thermal Indicator (if elevated)
                if (thermalTier != ThermalTier.NOMINAL) {
                    Box(
                        modifier = Modifier
                            .clip(AbhilekhTokens.Shapes.pill)
                            .background(
                                if (thermalTier == ThermalTier.SEVERE) AbhilekhTokens.Colors.Amber.copy(alpha = 0.2f)
                                else AbhilekhTokens.Colors.RoyalBlue.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "Thermal Governor",
                                tint = if (thermalTier == ThermalTier.SEVERE) AbhilekhTokens.Colors.Amber else AbhilekhTokens.Colors.RoyalBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (thermalTier == ThermalTier.SEVERE) "Cooling" else "Governed",
                                fontSize = 9.sp,
                                color = if (thermalTier == ThermalTier.SEVERE) AbhilekhTokens.Colors.Amber else AbhilekhTokens.Colors.RoyalBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
