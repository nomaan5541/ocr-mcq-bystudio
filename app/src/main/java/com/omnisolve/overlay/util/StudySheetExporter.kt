package com.omnisolve.overlay.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports solved flashcards & history entries to a clean A4 PDF or Markdown study sheet
 * and opens the Android Share sheet via FileProvider.
 */
object StudySheetExporter {

    fun buildMarkdownContent(entries: List<SolvedHistoryEntry>): String {
        val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("# Prism AI — Study Sheet & Flashcards")
            appendLine("_Generated: ${dateStr}_")
            appendLine()
            if (entries.isEmpty()) {
                appendLine("No solved questions recorded yet.")
            } else {
                entries.forEachIndexed { idx, item ->
                    val star = if (item.bookmarked) " ★" else ""
                    appendLine("### ${idx + 1}. ${item.questionPreview}$star")
                    appendLine("- **Answer:** `${item.answerLetter}`")
                    if (item.explanation.isNotBlank()) {
                        appendLine("- **Explanation:** ${item.explanation}")
                    }
                    appendLine("- **Subject / Source:** ${item.subject} • ${item.sourceLabel} (${item.timestamp})")
                    appendLine()
                }
            }
        }
    }

    fun exportAndShareMarkdown(context: Context, entries: List<SolvedHistoryEntry>): Boolean {
        return try {
            val mdContent = buildMarkdownContent(entries)
            val outFile = File(context.cacheDir, "Prism_AI_Study_Sheet.md")
            outFile.writeText(mdContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, "Prism AI Study Sheet")
                putExtra(Intent.EXTRA_TEXT, mdContent)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share Markdown Study Sheet").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exportAndSharePdf(context: Context, entries: List<SolvedHistoryEntry>): Boolean {
        return try {
            val doc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 18f
                isFakeBoldText = true
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 10.5f
            }
            val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 12f
                isFakeBoldText = true
            }
            val ansPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(4, 120, 87)
                textSize = 11.5f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 10.5f
            }

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas
            var y = margin + 16f

            val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("Prism AI — Study Sheet & Flashcards", margin, y, titlePaint)
            y += 18f
            canvas.drawText("Exported $dateStr  •  ${entries.size} item(s)", margin, y, subPaint)
            y += 26f

            val safeEntries = if (entries.isEmpty()) {
                listOf(
                    SolvedHistoryEntry(
                        id = 1L,
                        questionPreview = "No solved questions in current filter.",
                        answerLetter = "-",
                        sourceLabel = "Prism AI",
                        timestamp = dateStr
                    )
                )
            } else {
                entries
            }

            safeEntries.forEachIndexed { idx, item ->
                if (y > pageHeight - 95f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = margin + 16f
                }

                val star = if (item.bookmarked) " [Bookmarked]" else ""
                val qLine = "${idx + 1}. ${item.questionPreview.take(82)}$star"
                canvas.drawText(qLine, margin, y, qPaint)
                y += 16f

                canvas.drawText("Answer: ${item.answerLetter}   (${item.subject} • ${item.timestamp})", margin + 10f, y, ansPaint)
                y += 15f

                if (item.explanation.isNotBlank()) {
                    val expChunks = item.explanation.chunked(88).take(3)
                    expChunks.forEach { chunk ->
                        canvas.drawText(chunk, margin + 10f, y, bodyPaint)
                        y += 13f
                    }
                }
                y += 12f
            }

            doc.finishPage(page)

            val outFile = File(context.cacheDir, "Prism_AI_Study_Sheet.pdf")
            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "Prism AI Study Sheet (PDF)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share PDF Study Sheet").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
