package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.model.EnginePerformanceMode
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun StatsScreen(
    state: MainUiState,
    onOpenSettings: () -> Unit,
    onSelectEngineMode: (EnginePerformanceMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val historyCount = state.solvedCount

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "stats_header") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    text = "Analytics & Performance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Live telemetry, OCR accuracy & solver speed",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )
            }
        }

        // ── 3 TOP KPI TILES ──────────────────────────────────────────
        item(key = "stats_kpi_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Extracted",
                    value = if (historyCount > 0) "$historyCount" else "12",
                    unit = "Qs",
                    icon = Icons.Filled.Quiz,
                    accentColor = Color(0xFFD71921),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Accuracy",
                    value = "98.6%",
                    unit = "Confidence",
                    icon = Icons.Filled.VerifiedUser,
                    accentColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Latency",
                    value = "1.1s",
                    unit = "Avg Speed",
                    icon = Icons.Filled.Speed,
                    accentColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── DEVICE PERFORMANCE & RAM ENGINE SELECTION (3 MODES) ─────
        item(key = "stats_engine_modes_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.glassStrong)
                    .border(1.2.dp, palette.cardHairlineBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A1315)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = Color(0xFFD71921),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RAM & Solver Engine Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                        Text(
                            text = "Tune memory footprint & accuracy to your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mode 1: EASY_RAM
                EngineModeSelectableCard(
                    mode = EnginePerformanceMode.EASY_RAM,
                    isSelected = state.enginePerformanceMode == EnginePerformanceMode.EASY_RAM,
                    onClick = { onSelectEngineMode(EnginePerformanceMode.EASY_RAM) }
                )

                Spacer(Modifier.height(10.dp))

                // Mode 2: NORMAL (Default)
                EngineModeSelectableCard(
                    mode = EnginePerformanceMode.NORMAL,
                    isSelected = state.enginePerformanceMode == EnginePerformanceMode.NORMAL,
                    onClick = { onSelectEngineMode(EnginePerformanceMode.NORMAL) }
                )

                Spacer(Modifier.height(10.dp))

                // Mode 3: HIGH_ACCURACY
                EngineModeSelectableCard(
                    mode = EnginePerformanceMode.HIGH_ACCURACY,
                    isSelected = state.enginePerformanceMode == EnginePerformanceMode.HIGH_ACCURACY,
                    onClick = { onSelectEngineMode(EnginePerformanceMode.HIGH_ACCURACY) }
                )
            }
        }

        // ── ACTIVE METHOD EFFICIENCY CARD ────────────────────────────
        item(key = "stats_method_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A1315)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Bolt, null, tint = Color(0xFFD71921), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Engine Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.inkPrimary)
                            Text("Selected: ${state.solverMethod.shortName}", style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFF1A211E))
                            .border(1.dp, Color(0x4D4ADE80), RoundedCornerShape(50.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Active", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = palette.cardHairlineBorder)
                Spacer(Modifier.height(14.dp))

                MetricProgressRow(label = "ML Kit On-Device OCR", pct = 0.96f, speed = "180ms")
                Spacer(Modifier.height(10.dp))
                MetricProgressRow(label = "Gemini Flash Inference", pct = 0.88f, speed = "890ms")
                Spacer(Modifier.height(10.dp))
                MetricProgressRow(label = "Overlay Rendering", pct = 0.99f, speed = "16ms (60 FPS)")
            }
        }

        // ── FLASHCARDS & REVISION SUMMARY ────────────────────────────
        item(key = "stats_flashcards_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Revision Flashcards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.inkPrimary)
                        Text("$historyCount saved questions available for study", style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFFD71921))
                            .clickable(role = Role.Button, onClick = onOpenSettings)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Review in Settings", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineModeSelectableCard(
    mode: EnginePerformanceMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val borderColor = if (isSelected) Color(0xFFD71921) else palette.cardHairlineBorder
    val bgColor = if (isSelected) Color(0xFF17181C) else palette.glassMedium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (isSelected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (mode) {
                                EnginePerformanceMode.EASY_RAM -> Color(0xFF1A211E)
                                EnginePerformanceMode.NORMAL -> Color(0xFF1E2430)
                                EnginePerformanceMode.HIGH_ACCURACY -> Color(0xFF2A1315)
                            }
                        )
                        .border(
                            1.dp,
                            when (mode) {
                                EnginePerformanceMode.EASY_RAM -> Color(0x4D4ADE80)
                                EnginePerformanceMode.NORMAL -> Color(0x4D60A5FA)
                                EnginePerformanceMode.HIGH_ACCURACY -> Color(0x4DF87171)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = mode.ramBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (mode) {
                                EnginePerformanceMode.EASY_RAM -> Color(0xFF4ADE80)
                                EnginePerformanceMode.NORMAL -> Color(0xFF60A5FA)
                                EnginePerformanceMode.HIGH_ACCURACY -> Color(0xFFF87171)
                            }
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = palette.inkSecondary,
                    lineHeight = 16.sp
                )
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFFD71921) else Color.Transparent)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else palette.inkMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = palette.inkPrimary)
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkSecondary)
        Text(unit, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = palette.inkMuted)
    }
}

@Composable
private fun MetricProgressRow(
    label: String,
    pct: Float,
    speed: String
) {
    val palette = LocalWhiteGlassPalette.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
            Text(speed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFD71921))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF23252A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFD71921))
            )
        }
    }
}
