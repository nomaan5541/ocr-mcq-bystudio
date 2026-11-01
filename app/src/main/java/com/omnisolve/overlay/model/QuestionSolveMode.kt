package com.omnisolve.overlay.model

/**
 * Specialized solving modes selectable from the Dynamic Flow Bar or Settings:
 * - STANDARD: Instant single-line option solving (A-F, True/False, value).
 * - MATCH_FOLLOWING: Solves Column-I / Column-II / Matrix-Match pairings carefully and outputs
 *   either the matching option letter (A-F) or full compact pairing (e.g., "A-2, B-4, C-1, D-3").
 * - COMPLEX_MATH: Allocates higher reasoning budget and step-verification instructions for
 *   complicated mathematics, calculus, physics circuits, and multi-statement questions.
 */
enum class QuestionSolveMode(
    val id: String,
    val chipLabel: String,
    val fullLabel: String
) {
    STANDARD(
        id = "STANDARD",
        chipLabel = "STD",
        fullLabel = "Standard Fast"
    ),
    MATCH_FOLLOWING(
        id = "MATCH",
        chipLabel = "MATCH",
        fullLabel = "Match the Following"
    ),
    COMPLEX_MATH(
        id = "COMPLEX_MATH",
        chipLabel = "MATH+",
        fullLabel = "Complicated Math+"
    );

    companion object {
        fun fromId(raw: String?): QuestionSolveMode {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.id == clean } ?: STANDARD
        }
    }
}
