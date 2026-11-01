package com.omnisolve.overlay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.util.StudySheetExporter

/**
 * Interactive Liquid Glass Flashcard Deck & One-Tap Study Sheet Exporter (PDF & Markdown).
 * Supports search, subject filters, bookmarking, tap-to-flip, and left/right swipe navigation.
 */
@Composable
fun FlashcardDeckSection(
    entries: List<SolvedHistoryEntry>,
    searchQuery: String,
    subjectFilter: String,
    bookmarkedOnly: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSubjectFilterChange: (String) -> Unit,
    onToggleBookmarkedOnly: (Boolean) -> Unit,
    onToggleBookmarkEntry: (Long) -> Unit,
    onExportMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current

    val filteredEntries = remember(entries, searchQuery, subjectFilter, bookmarkedOnly) {
        entries.filter { entry ->
            val matchesBookmark = !bookmarkedOnly || entry.bookmarked
            val matchesSubject = subjectFilter == "ALL" ||
                entry.subject.equals(subjectFilter, ignoreCase = true) ||
                entry.sourceLabel.contains(subjectFilter, ignoreCase = true)
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                entry.questionPreview.contains(q, ignoreCase = true) ||
                entry.explanation.contains(q, ignoreCase = true) ||
                entry.answerLetter.contains(q, ignoreCase = true)
            matchesBookmark && matchesSubject && matchesQuery
        }
    }

    var currentIndex by rememberSaveable(filteredEntries.size) { mutableIntStateOf(0) }
    var isFlipped by rememberSaveable(currentIndex, filteredEntries.size) { mutableStateOf(false) }

    val safeIndex = if (filteredEntries.isEmpty()) 0 else currentIndex.coerceIn(0, filteredEntries.lastIndex)
    val activeCard = filteredEntries.getOrNull(safeIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .livingGlassSurface(
                strength = GlassMaterialStrength.Card,
                cornerRadius = 20.dp
            )
            .padding(16.dp)
            .testTag("section_flashcard_deck")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liquid Glass Flashcard Deck",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Text(
                    text = "Tap card to flip • Swipe left/right to browse",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )
            }
            Text(
                text = if (filteredEntries.isEmpty()) "0 / 0" else "${safeIndex + 1} / ${filteredEntries.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accentSky
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search flashcards",
                    tint = palette.inkSecondary
                )
            },
            placeholder = { Text("Search questions or answers...") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (palette.isDarkObsidian) Color(0xFF141D2E) else Color.White,
                unfocusedContainerColor = if (palette.isDarkObsidian) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                focusedBorderColor = palette.accentSky,
                unfocusedBorderColor = palette.cardHairlineBorder,
                focusedTextColor = palette.inkPrimary,
                unfocusedTextColor = palette.inkPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_flashcard_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subject & Bookmark filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LivingSecondaryButton(
                text = if (bookmarkedOnly) "Saved ★" else "Saved",
                icon = if (bookmarkedOnly) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                selected = bookmarkedOnly,
                onClick = { onToggleBookmarkedOnly(!bookmarkedOnly) },
                testTag = "chip_filter_bookmarked",
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
            )
            val subjects = listOf("ALL") + SubjectMode.entries.map { it.id }
            subjects.forEach { subj ->
                val label = if (subj == "ALL") "All" else SubjectMode.fromId(subj).label
                LivingSecondaryButton(
                    text = label,
                    selected = subjectFilter.equals(subj, ignoreCase = true),
                    onClick = { onSubjectFilterChange(subj) },
                    testTag = "chip_filter_${subj.lowercase()}",
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeCard == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (palette.isDarkObsidian) Color(0xFF111827) else Color(0xFFF1F5F9)
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching flashcards yet. Solve questions or clear filters to study.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )
            }
        } else {
            var dragTotal by remember { mutableStateOf(0f) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (palette.isDarkObsidian) Color(0xFF141E30) else Color(0xFFF0F9FF)
                    )
                    .border(
                        width = 1.4.dp,
                        color = palette.accentSky,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .pointerInput(filteredEntries.size, safeIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                            onDragEnd = {
                                if (dragTotal < -65f && filteredEntries.size > 1) {
                                    currentIndex = (safeIndex + 1) % filteredEntries.size
                                    isFlipped = false
                                } else if (dragTotal > 65f && filteredEntries.size > 1) {
                                    currentIndex = (safeIndex - 1 + filteredEntries.size) % filteredEntries.size
                                    isFlipped = false
                                }
                            }
                        )
                    }
                    .clickable(
                        role = Role.Button,
                        onClick = { isFlipped = !isFlipped }
                    )
                    .padding(16.dp)
                    .testTag("card_flashcard_active")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isFlipped) "ANSWER SIDE" else "QUESTION SIDE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSky
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Flip,
                            contentDescription = "Flip flashcard",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onToggleBookmarkEntry(activeCard.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_bookmark_flashcard")
                        ) {
                            Icon(
                                imageVector = if (activeCard.bookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Filled.BookmarkBorder
                                },
                                contentDescription = if (activeCard.bookmarked) {
                                    "Remove bookmark"
                                } else {
                                    "Bookmark flashcard"
                                },
                                tint = if (activeCard.bookmarked) palette.accentAmber else palette.inkSecondary
                            )
                        }
                    }
                }

                if (!isFlipped) {
                    Text(
                        text = activeCard.questionPreview,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${activeCard.subject} • ${activeCard.sourceLabel} • ${activeCard.timestamp}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkSecondary
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(palette.accentEmeraldContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeCard.answerLetter,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accentEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Correct Answer: ${activeCard.answerLetter}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                    }
                    if (activeCard.explanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeCard.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LivingSecondaryButton(
                    text = "Prev",
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    onClick = {
                        if (filteredEntries.isNotEmpty()) {
                            currentIndex = (safeIndex - 1 + filteredEntries.size) % filteredEntries.size
                            isFlipped = false
                        }
                    },
                    testTag = "btn_prev_flashcard",
                    modifier = Modifier.weight(1f)
                )
                LivingSecondaryButton(
                    text = "Next",
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = {
                        if (filteredEntries.isNotEmpty()) {
                            currentIndex = (safeIndex + 1) % filteredEntries.size
                            isFlipped = false
                        }
                    },
                    testTag = "btn_next_flashcard",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivingPrimaryButton(
                text = "Export PDF",
                icon = Icons.Filled.PictureAsPdf,
                onClick = {
                    val ok = StudySheetExporter.exportAndSharePdf(context, filteredEntries)
                    onExportMessage(
                        if (ok) "Study Sheet PDF ready to share"
                        else "Could not export PDF right now"
                    )
                },
                testTag = "btn_export_pdf",
                modifier = Modifier.weight(1f)
            )
            LivingSecondaryButton(
                text = "Markdown",
                icon = Icons.Filled.Description,
                onClick = {
                    val ok = StudySheetExporter.exportAndShareMarkdown(context, filteredEntries)
                    onExportMessage(
                        if (ok) "Markdown study sheet ready to share"
                        else "Could not export Markdown right now"
                    )
                },
                testTag = "btn_export_markdown",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
