package com.abhilekh.app.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fail-Loud Amber Alert HUD: Prompts user whenever an Aadhaar or sensitive ID is detected
 * with borderline confidence or requires manual verification before PDF assembly.
 */
@Composable
fun AmberWarningHUD(
    isVisible: Boolean,
    pageNumber: Int,
    detectedTextSnippet: String?,
    onReviewClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AbhilekhTokens.Spacing.base, vertical = AbhilekhTokens.Spacing.sm),
            shape = AbhilekhTokens.Shapes.lg,
            color = AbhilekhTokens.Colors.Slate900,
            shadowElevation = AbhilekhTokens.Elevation.hud
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = AbhilekhTokens.Colors.Amber,
                        shape = AbhilekhTokens.Shapes.lg
                    )
                    .padding(AbhilekhTokens.Spacing.base)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AbhilekhTokens.Spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AbhilekhTokens.Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(AbhilekhTokens.Shapes.sm)
                                .background(AbhilekhTokens.Colors.Amber.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Aadhaar Detected",
                                tint = AbhilekhTokens.Colors.Amber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aadhaar Detected (Page $pageNumber)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Verify or manually redact the first 8 digits to ensure privacy.",
                                color = AbhilekhTokens.Colors.Slate400,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (detectedTextSnippet != null) {
                        Surface(
                            shape = AbhilekhTokens.Shapes.sm,
                            color = AbhilekhTokens.Colors.Slate800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Snippet: $detectedTextSnippet",
                                color = AbhilekhTokens.Colors.Slate200,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AbhilekhTokens.Spacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = AbhilekhTokens.Shapes.md,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AbhilekhTokens.Colors.Slate400
                            )
                        ) {
                            Text("Ignore", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onReviewClick,
                            modifier = Modifier.weight(1.5f),
                            shape = AbhilekhTokens.Shapes.md,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AbhilekhTokens.Colors.Amber,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Review Brush", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
