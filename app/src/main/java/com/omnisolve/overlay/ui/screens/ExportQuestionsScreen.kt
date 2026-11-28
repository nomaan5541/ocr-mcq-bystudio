package com.omnisolve.overlay.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class ExportDocumentFormat(val id: String, val label: String, val extension: String) {
    TXT("txt", "Study Sheet (.TXT)", "txt"),
    CSV("csv", "Spreadsheet (.CSV)", "csv"),
    JSON("json", "Dataset (.JSON)", "json"),
    MD("md", "Markdown (.MD)", "md")
}

data class ZipBackupSummary(
    val zipFile: File,
    val entryNames: List<String>,
    val sizeBytes: Long,
    val createdTimestamp: String
)

@Composable
fun ExportQuestionsScreen(
    entries: List<SolvedHistoryEntry>,
    onBackToHome: () -> Unit,
    onToggleBookmarkEntry: (Long) -> Unit,
    onAddSampleExport: () -> Unit,
    onClearExportHistory: () -> Unit,
    onShowBannerMessage: (String) -> Unit,
    offlineCacheCount: Int = 0,
    offlineCacheSavedCalls: Int = 0,
    onDeleteEntry: (Long) -> Unit = {},
    onClearOfflineCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 24).dp
    val listState = rememberLazyListState()
    var selectedFormat by remember { mutableStateOf(ExportDocumentFormat.TXT) }
    var bookmarkedOnly by remember { mutableStateOf(false) }
    var cachedOnly by remember { mutableStateOf(false) }
    var includeExplanations by remember { mutableStateOf(true) }
    var includeTimestamps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var lastZipBackup by remember { mutableStateOf<ZipBackupSummary?>(null) }

    val filteredEntries = remember(entries, bookmarkedOnly, cachedOnly, searchQuery) {
        val base = entries.filter { item ->
            (!bookmarkedOnly || item.bookmarked) && (!cachedOnly || item.isCachedResult)
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            base
        } else {
            base.filter {
                it.questionPreview.contains(q, ignoreCase = true) ||
                    it.explanation.contains(q, ignoreCase = true) ||
                    it.subject.contains(q, ignoreCase = true) ||
                    it.answerLetter.contains(q, ignoreCase = true) ||
                    it.modelUsed.contains(q, ignoreCase = true)
            }
        }
    }

    val formattedPayload = remember(
        filteredEntries,
        selectedFormat,
        includeExplanations,
        includeTimestamps
    ) {
        buildExportDocumentPayload(
            entries = filteredEntries,
            format = selectedFormat,
            includeExplanations = includeExplanations,
            includeTimestamps = includeTimestamps
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("export_questions_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 0A. OFFLINE ROOM CACHE VAULT SUMMARY CARD ──
        item(key = "offline_room_cache_vault_card") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.accentEmerald.copy(alpha = 0.5f), RoundedCornerShape(cardCorner))
                    .padding(14.dp)
                    .testTag("card_offline_room_cache_vault"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette.accentEmeraldContainer)
                            .border(1.dp, palette.accentEmerald.copy(alpha = 0.6f), RoundedCornerShape(11.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OfflineBolt,
                            contentDescription = "Offline Room Cache",
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Offline Room Cache • Active",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${maxOf(offlineCacheCount, entries.size)} saved offline • $offlineCacheSavedCalls repeat AI calls saved",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (offlineCacheCount > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onClearOfflineCache)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("btn_clear_room_cache")
                    ) {
                        Text(
                            text = "Clear Cache",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ── 0. FULL .ZIP BACKUP & ARCHIVE STUDIO CARD ──
        item(key = "zip_backup_studio_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.2.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(cardCorner))
                    .padding(16.dp)
                    .testTag("card_backup_zip_generator"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Archive,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Full Backup Archive (.ZIP)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.inkPrimary
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.accentSky)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "5 Files in .ZIP",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = palette.onAccentInk,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Bundles .TXT, .CSV, .JSON, .MD & App Settings into a single .ZIP file",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.8.sp,
                                    color = palette.inkSecondary
                                )
                            )
                        }
                    }
                }

                // Action Row: Create & Save .ZIP | Share .ZIP | Verify/Restore .ZIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Create & Save Full .ZIP Backup
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1.15f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSky)
                            .clickable(role = Role.Button) {
                                val summary = createFullBackupZipArchive(
                                    context = context,
                                    entries = filteredEntries,
                                    includeExplanations = includeExplanations,
                                    includeTimestamps = includeTimestamps
                                )
                                if (summary != null) {
                                    lastZipBackup = summary
                                    onShowBannerMessage(
                                        "Created ${summary.zipFile.name} (${summary.sizeBytes} bytes • 5 files)"
                                    )
                                } else {
                                    onShowBannerMessage("Failed to create .ZIP backup archive")
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_save_backup_zip")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = null,
                            tint = palette.onAccentInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Create .ZIP",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    // 2. Share .ZIP Archive via FileProvider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                val summary = lastZipBackup ?: createFullBackupZipArchive(
                                    context = context,
                                    entries = filteredEntries,
                                    includeExplanations = includeExplanations,
                                    includeTimestamps = includeTimestamps
                                )
                                if (summary != null) {
                                    lastZipBackup = summary
                                    shareBackupZipArchive(context, summary.zipFile, onShowBannerMessage)
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_share_backup_zip")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Share .ZIP",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    // 3. Verify / Restore Latest .ZIP Backup
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                val summary = lastZipBackup ?: createFullBackupZipArchive(
                                    context = context,
                                    entries = filteredEntries,
                                    includeExplanations = includeExplanations,
                                    includeTimestamps = includeTimestamps
                                )
                                if (summary != null) {
                                    lastZipBackup = summary
                                    val restoredCount = restoreSettingsFromBackupZip(context, summary.zipFile)
                                    onShowBannerMessage(
                                        "Verified .ZIP (${summary.entryNames.size} files) & restored $restoredCount prefs"
                                    )
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_restore_backup_zip")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Restore",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Live .ZIP Archive Status Box when created
                val activeBackup = lastZipBackup
                if (activeBackup != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentEmeraldContainer)
                            .border(1.dp, palette.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "BACKUP READY: ${activeBackup.zipFile.name} (${activeBackup.sizeBytes} bytes)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentEmerald,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp
                            )
                        )
                        Text(
                            text = "Bundled files: ${activeBackup.entryNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkPrimary,
                                fontSize = 11.2.sp
                            )
                        )
                        Text(
                            text = "Path: ${activeBackup.zipFile.absolutePath}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.2.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── 1. EXPORT STUDIO OVERVIEW & CONTROLS CARD ──
        item(key = "export_header_controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp)
                    .testTag("section_export_qa"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Export Questions & Answers",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.inkPrimary
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.accentSky)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${filteredEntries.size} Q&A",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = palette.onAccentInk,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Format, filter, copy, share, or save your solved Q&A study sheets",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.8.sp,
                                    color = palette.inkSecondary
                                )
                            )
                        }
                    }
                }

                // Quick Queue Actions: + Add Sample Q&A & Clear Export Queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onAddSampleExport)
                            .padding(vertical = 10.dp, horizontal = 10.dp)
                            .testTag("btn_export_add_sample")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Sample Q&A",
                            tint = palette.accentSky,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add Sample Q&A",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onClearExportHistory)
                            .padding(vertical = 10.dp, horizontal = 10.dp)
                            .testTag("btn_export_clear_all")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Export Queue",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Reset Queue",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Format Selector Row (.TXT / .CSV / .JSON / .MD)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. Select Export File Format",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportDocumentFormat.entries.forEach { format ->
                            val selected = selectedFormat == format
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) palette.accentSky else palette.glassMedium)
                                    .border(
                                        1.dp,
                                        if (selected) palette.accentSky else palette.cardHairlineBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(role = Role.Tab) { selectedFormat = format }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("btn_export_format_${format.id}")
                            ) {
                                Text(
                                    text = format.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (selected) palette.onAccentInk else palette.inkSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Filter & Formatting Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "2. Filter & Content Options",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExportOptionChip(
                            label = "All Solved (${entries.size})",
                            selected = !bookmarkedOnly && !cachedOnly,
                            onClick = {
                                bookmarkedOnly = false
                                cachedOnly = false
                            }
                        )
                        ExportOptionChip(
                            label = "Cached Offline (${entries.count { it.isCachedResult }})",
                            selected = cachedOnly,
                            onClick = { cachedOnly = !cachedOnly }
                        )
                        ExportOptionChip(
                            label = "Bookmarked (${entries.count { it.bookmarked }})",
                            selected = bookmarkedOnly,
                            onClick = { bookmarkedOnly = !bookmarkedOnly }
                        )
                        ExportOptionChip(
                            label = if (includeExplanations) "Explanations: ON" else "Explanations: OFF",
                            selected = includeExplanations,
                            onClick = { includeExplanations = !includeExplanations }
                        )
                        ExportOptionChip(
                            label = if (includeTimestamps) "Timestamps: ON" else "Timestamps: OFF",
                            selected = includeTimestamps,
                            onClick = { includeTimestamps = !includeTimestamps }
                        )
                    }
                }

                // Keyword Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Filter questions by keyword, subject, or option...",
                            color = palette.inkMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.glassMedium,
                        unfocusedContainerColor = palette.canvasBottom,
                        focusedBorderColor = palette.accentSky,
                        unfocusedBorderColor = palette.cardHairlineBorder,
                        focusedTextColor = palette.inkPrimary,
                        unfocusedTextColor = palette.inkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_export_search")
                )

                // Primary Export Action Bar: Copy All, Share Sheet, Save File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Copy All Q&A to Clipboard
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSky)
                            .clickable(role = Role.Button) {
                                copyExportToClipboard(context, "Prism Solved Q&A Export", formattedPayload)
                                onShowBannerMessage(
                                    "Copied ${filteredEntries.size} Q&A (${selectedFormat.extension.uppercase()}) to clipboard"
                                )
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_export_copy_clipboard")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = palette.onAccentInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Copy All",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    // 2. Share via Android Share Sheet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                shareExportPayload(context, formattedPayload, selectedFormat)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_export_share_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Share Q&A",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    // 3. Save Export File on Device
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                val savedFile = saveExportPayloadToDisk(context, formattedPayload, selectedFormat)
                                if (savedFile != null) {
                                    onShowBannerMessage("Saved ${savedFile.name} (${filteredEntries.size} Q&A)")
                                    (context as? android.app.Activity)?.let { act ->
                                        com.omnisolve.overlay.ads.AdManager.showInterstitial(act)
                                    }
                                } else {
                                    onShowBannerMessage("Could not save export file")
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("btn_export_save_file")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Save .${selectedFormat.extension.uppercase()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ── 2. LIVE DOCUMENT PREVIEW CARD ──
        item(key = "export_live_preview_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.canvasBottom)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE EXPORT PREVIEW (.${selectedFormat.extension.uppercase()})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentSky,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "${formattedPayload.length} chars • ${filteredEntries.size} items",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkMuted,
                            fontSize = 10.5.sp
                        )
                    )
                }
                Text(
                    text = formattedPayload.lines().take(12).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.2.sp,
                        lineHeight = 16.sp,
                        color = palette.inkPrimary
                    ),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── 3. CAPTURED QUESTIONS & ANSWERS LIST HEADER ──
        item(key = "export_items_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Captured Questions & Answers (${filteredEntries.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                )
                Text(
                    text = "Tap copy or bookmark on any item",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = palette.inkSecondary
                    )
                )
            }
        }

        if (filteredEntries.isEmpty()) {
            item(key = "export_empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (bookmarkedOnly) {
                            "No bookmarked questions match your filter. Tap the bookmark icon on any solved question or switch to 'All Solved'."
                        } else {
                            "No solved questions in export queue yet. Tap '+ Add Sample Q&A' above or solve any screen question."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 12.2.sp
                        )
                    )
                }
            }
        } else {
            itemsIndexed(
                items = filteredEntries,
                key = { _, item -> "export_item_${item.id}" }
            ) { idx, item ->
                ExportQuestionCard(
                    index = idx + 1,
                    entry = item,
                    includeExplanations = includeExplanations,
                    includeTimestamps = includeTimestamps,
                    onToggleBookmark = { onToggleBookmarkEntry(item.id) },
                    onDeleteSingle = { onDeleteEntry(item.id) },
                    onCopySingle = {
                        val singleText = buildString {
                            append("Q${idx + 1}: ${item.questionPreview}\n")
                            append("Answer: Option ${item.answerLetter}")
                            if (includeExplanations && item.explanation.isNotBlank()) {
                                append("\nExplanation: ${item.explanation}")
                            }
                            if (includeTimestamps) {
                                append("\nTime: ${item.timestamp}")
                            }
                        }
                        copyExportToClipboard(context, "Solved Q&A #${idx + 1}", singleText)
                        onShowBannerMessage("Copied Q${idx + 1} & Answer (${item.answerLetter}) to clipboard")
                    }
                )
            }
        }

        // ── 4. BACK TO HOME BUTTON ──
        item(key = "export_screen_back_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassMedium)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onBackToHome)
                    .padding(horizontal = 16.dp, vertical = 13.dp)
                    .testTag("btn_export_screen_done"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Back to Home Screen",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ExportOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) palette.accentSkyContainer else palette.glassMedium)
            .border(
                1.dp,
                if (selected) palette.accentSky else palette.cardHairlineBorder,
                RoundedCornerShape(50.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) palette.inkPrimary else palette.inkSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun ExportQuestionCard(
    index: Int,
    entry: SolvedHistoryEntry,
    includeExplanations: Boolean,
    includeTimestamps: Boolean,
    onToggleBookmark: () -> Unit,
    onDeleteSingle: () -> Unit = {},
    onCopySingle: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(10, 22).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accentSky)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Ans: ${entry.answerLetter}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    )
                }

                if (entry.isCachedResult) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accentEmeraldContainer)
                            .border(1.dp, palette.accentEmerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Cached",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }

                Text(
                    text = buildString {
                        append("Q$index • ${entry.subject}")
                        if (includeTimestamps) {
                            append(" • ${entry.timestamp}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onToggleBookmark)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = if (entry.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark Q&A",
                            tint = if (entry.bookmarked) palette.accentAmber else palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onDeleteSingle)
                        .testTag("btn_delete_history_${entry.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete history entry",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onCopySingle)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Single Q&A",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = entry.questionPreview,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.2.sp,
                lineHeight = 18.5.sp
            )
        )

        if (includeExplanations && entry.explanation.isNotBlank()) {
            Text(
                text = entry.explanation,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )
            )
        }
    }
}

fun buildExportDocumentPayload(
    entries: List<SolvedHistoryEntry>,
    format: ExportDocumentFormat,
    includeExplanations: Boolean,
    includeTimestamps: Boolean
): String {
    if (entries.isEmpty()) {
        return when (format) {
            ExportDocumentFormat.TXT -> "# Prism Solver: Question & Answer Export\n(No solved questions selected)"
            ExportDocumentFormat.CSV -> "No,Subject,Question,Answer,Explanation,Timestamp"
            ExportDocumentFormat.JSON -> "[]"
            ExportDocumentFormat.MD -> "# Prism Solver: Study Export\n_No solved questions selected._"
        }
    }

    return when (format) {
        ExportDocumentFormat.TXT -> buildString {
            appendLine("=== PRISM SOLVER: QUESTIONS & ANSWERS EXPORT (${entries.size} ITEMS) ===")
            appendLine()
            entries.forEachIndexed { idx, item ->
                appendLine("Q${idx + 1} [${item.subject}]: ${item.questionPreview}")
                appendLine("Correct Answer: ${item.answerLetter}")
                if (includeExplanations && item.explanation.isNotBlank()) {
                    appendLine("Explanation: ${item.explanation}")
                }
                if (includeTimestamps) {
                    appendLine("Time: ${item.timestamp}")
                }
                appendLine("------------------------------------------------------------")
            }
        }.trimEnd()

        ExportDocumentFormat.CSV -> buildString {
            appendLine("No,Subject,Question,Answer,Explanation,Timestamp")
            entries.forEachIndexed { idx, item ->
                val safeQ = item.questionPreview.replace("\"", "\"\"")
                val safeAns = item.answerLetter.replace("\"", "\"\"")
                val safeExp = if (includeExplanations) item.explanation.replace("\"", "\"\"") else ""
                val safeTime = if (includeTimestamps) item.timestamp else ""
                appendLine("${idx + 1},\"${item.subject}\",\"$safeQ\",\"$safeAns\",\"$safeExp\",\"$safeTime\"")
            }
        }.trimEnd()

        ExportDocumentFormat.JSON -> {
            val payloadList = entries.mapIndexed { idx, item ->
                linkedMapOf<String, Any>(
                    "number" to (idx + 1),
                    "subject" to item.subject,
                    "question" to item.questionPreview,
                    "answer" to item.answerLetter
                ).apply {
                    if (includeExplanations) {
                        put("explanation", item.explanation)
                    }
                    if (includeTimestamps) {
                        put("timestamp", item.timestamp)
                    } else {
                        put("exported", true)
                    }
                }
            }
            GsonBuilder().setPrettyPrinting().create().toJson(payloadList)
        }

        ExportDocumentFormat.MD -> buildString {
            appendLine("# Prism Solver: Study Sheet (${entries.size} Q&A)")
            appendLine()
            entries.forEachIndexed { idx, item ->
                appendLine("### Q${idx + 1}. ${item.questionPreview}")
                appendLine("- **Correct Answer:** `${item.answerLetter}`")
                appendLine("- **Subject:** ${item.subject}")
                if (includeExplanations && item.explanation.isNotBlank()) {
                    appendLine("- **Explanation:** ${item.explanation}")
                }
                if (includeTimestamps) {
                    appendLine("- **Solved:** ${item.timestamp}")
                }
                appendLine()
            }
        }.trimEnd()
    }
}

/**
 * Creates a complete `prism_ocr_solver_backup.zip` archive containing:
 * 1. `questions_answers.txt`
 * 2. `questions_answers.csv`
 * 3. `questions_answers.json`
 * 4. `questions_answers.md`
 * 5. `app_settings_backup.json` (non-sensitive preferences & configuration snapshot)
 */
fun createFullBackupZipArchive(
    context: Context,
    entries: List<SolvedHistoryEntry>,
    includeExplanations: Boolean = true,
    includeTimestamps: Boolean = true
): ZipBackupSummary? {
    return try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val zipFile = File(dir, "prism_ocr_solver_backup.zip")
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val excludedSecretKeys = setOf(
            "PREF_NVIDIA_API_KEY",
            "PREF_OPENROUTER_API_KEY",
            "PREF_GEMINI_API_KEY",
            "PREF_OPENAI_API_KEY",
            "PREF_CUSTOM_API_KEY"
        )
        val settingsJson = buildString {
            appendLine("{")
            appendLine("  \"app\": \"Prism Solver\",")
            appendLine("  \"version\": \"3.5.2\",")
            appendLine("  \"preferences\": {")
            val safeEntries = prefs.all.entries
                .filter { it.key !in excludedSecretKeys }
                .sortedBy { it.key }
            safeEntries.forEachIndexed { idx, (k, v) ->
                val comma = if (idx < safeEntries.lastIndex) "," else ""
                val escapedVal = v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                appendLine("    \"$k\": \"$escapedVal\"$comma")
            }
            appendLine("  }")
            append("}")
        }

        val filesMap = linkedMapOf(
            "questions_answers.txt" to buildExportDocumentPayload(entries, ExportDocumentFormat.TXT, includeExplanations, includeTimestamps),
            "questions_answers.csv" to buildExportDocumentPayload(entries, ExportDocumentFormat.CSV, includeExplanations, includeTimestamps),
            "questions_answers.json" to buildExportDocumentPayload(entries, ExportDocumentFormat.JSON, includeExplanations, includeTimestamps),
            "questions_answers.md" to buildExportDocumentPayload(entries, ExportDocumentFormat.MD, includeExplanations, includeTimestamps),
            "app_settings_backup.json" to settingsJson
        )

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            filesMap.forEach { (entryName, content) ->
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                val bytes = content.toByteArray(Charsets.UTF_8)
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        ZipBackupSummary(
            zipFile = zipFile,
            entryNames = filesMap.keys.toList(),
            sizeBytes = zipFile.length(),
            createdTimestamp = timestamp
        )
    } catch (_: Exception) {
        null
    }
}

fun restoreSettingsFromBackupZip(context: Context, zipFile: File): Int {
    if (!zipFile.exists()) return 0
    return try {
        ZipFile(zipFile).use { zip ->
            val settingsEntry = zip.getEntry("app_settings_backup.json") ?: return 0
            val jsonText = zip.getInputStream(settingsEntry).bufferedReader().use { it.readText() }
            val regex = Regex(""""(PREF_[^"]+)"\s*:\s*"([^"]*)"""")
            val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var count = 0
            regex.findAll(jsonText).forEach { match ->
                val key = match.groupValues[1]
                val rawVal = match.groupValues[2]
                when {
                    rawVal.equals("true", ignoreCase = true) || rawVal.equals("false", ignoreCase = true) -> {
                        editor.putBoolean(key, rawVal.toBoolean())
                        count++
                    }
                    rawVal.toIntOrNull() != null -> {
                        editor.putInt(key, rawVal.toInt())
                        count++
                    }
                    else -> {
                        editor.putString(key, rawVal)
                        count++
                    }
                }
            }
            editor.apply()
            count
        }
    } catch (_: Exception) {
        0
    }
}

private fun shareBackupZipArchive(
    context: Context,
    zipFile: File,
    onShowBannerMessage: (String) -> Unit
) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, "Prism Solver: Full Backup (.ZIP)")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Prism Backup (.ZIP)"))
    } catch (_: Exception) {
        onShowBannerMessage("Saved backup at: ${zipFile.absolutePath}")
    }
}

private fun copyExportToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Exception) {
    }
}

private fun shareExportPayload(context: Context, content: String, format: ExportDocumentFormat) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Prism Solver: Questions & Answers (${format.extension.uppercase()})"
            )
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Questions & Answers"))
    } catch (_: Exception) {
    }
}

private fun saveExportPayloadToDisk(
    context: Context,
    content: String,
    format: ExportDocumentFormat
): File? {
    return try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "prism_questions_answers.${format.extension}")
        file.writeText(content)
        file
    } catch (_: Exception) {
        null
    }
}
