package com.omnisolve.overlay.model

import com.google.gson.annotations.SerializedName

/**
 * Rich Answer Result supporting all quiz and study formats:
 * - Standard 4-to-6 option MCQs (A, B, C, D, E, F)
 * - Multi-Select Checkboxes (A, C, D)
 * - True / False & Assertion-Reason (True, False, A-D)
 * - Fill-in-the-Blank / Numerical Values (42.5, Mitochondrion)
 * - Matching / Ordering Pairs (1-C, 2-A, 3-B)
 */
data class AnswerModel(
    @SerializedName("correctChoice") val correctChoice: String = "?",
    @SerializedName("shortAnswer") val shortAnswer: String = "",
    @SerializedName("explanation") val explanation: String = "",
    @SerializedName("questionType") val questionType: String = QUESTION_TYPE_MCQ,
    @SerializedName("confidence") val confidence: Int = 95,
    @SerializedName("language") val language: String = "English",
    @SerializedName("translatedQuestion") val translatedQuestion: String = "",
    val subject: String = SubjectMode.GENERAL.id,
    val verified: Boolean = false,
    val engineLabel: String = "Firebase Cloud Gemini"
) {
    companion object {
        const val QUESTION_TYPE_MCQ = "MCQ"
        const val QUESTION_TYPE_MULTI_SELECT = "MULTI_SELECT"
        const val QUESTION_TYPE_TRUE_FALSE = "TRUE_FALSE"
        const val QUESTION_TYPE_FILL_BLANK = "FILL_BLANK"
        const val QUESTION_TYPE_MATCHING = "MATCHING"

        val VALID_QUESTION_TYPES = setOf(
            QUESTION_TYPE_MCQ,
            QUESTION_TYPE_MULTI_SELECT,
            QUESTION_TYPE_TRUE_FALSE,
            QUESTION_TYPE_FILL_BLANK,
            QUESTION_TYPE_MATCHING
        )
    }

    /**
     * Returns single primary option letters (A..F) for auto-tap or haptic pulses.
     * Supports both single choice ("B") and multi-select ("A, C, D").
     */
    fun extractTargetLetters(): List<String> {
        val raw = correctChoice.trim().uppercase()
        if (raw.length == 1 && raw[0] in 'A'..'F') {
            return listOf(raw)
        }
        // Multi-select like "A, C" or "A, C, D"
        if (questionType == QUESTION_TYPE_MULTI_SELECT || raw.contains(",")) {
            val tokens = raw.split(Regex("[,;&/\\s]+"))
                .map { it.trim() }
                .filter { it.length == 1 && it[0] in 'A'..'F' }
                .distinct()
            if (tokens.isNotEmpty() && !raw.contains("-")) {
                return tokens
            }
        }
        return emptyList()
    }
}

enum class SubjectMode(val id: String, val label: String, val shortHint: String) {
    GENERAL("GENERAL", "General", "All-round academic & aptitude"),
    MATH("MATH", "Math & Calc", "Step-by-step algebra, calculus & stats"),
    SCIENCE("SCIENCE", "Physics & Chem", "Formulas, units, stoichiometry & laws"),
    CODING("CODING", "Coding & DSA", "Big-O complexity, output trace & bugs"),
    MEDICAL("MEDICAL", "Medical & Bio", "Clinical vignettes, anatomy & pathways"),
    LAW("LAW", "Law & Reasoning", "Logical deduction, legal & verbal");

    companion object {
        fun fromId(id: String?): SubjectMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: GENERAL
    }
}

enum class HudShapeStyle(val id: String, val title: String, val subtitle: String) {
    DYNAMIC_ISLAND(
        "DYNAMIC_ISLAND",
        "Dynamic Island Pill",
        "Frosted iOS 26 capsule with 1-tap solve & expandable explanation"
    ),
    SPLIT_PILL(
        "SPLIT_PILL",
        "Split Answer Pill",
        "Displays both option letter (B) and short answer text side-by-side"
    ),
    MINI_PILL(
        "MINI_PILL",
        "Mini Capsule",
        "Ultra-compact floating pill showing crisp answer & confidence"
    ),
    STATUS_STRIP(
        "STATUS_STRIP",
        "Status-Bar Micro Strip",
        "Ultra-thin sleek strip tucked right along the top status edge"
    ),
    EDGE_TAB(
        "EDGE_TAB",
        "Edge-Docked Glass Tab",
        "Subtle side-docked translucent tab hugging the screen border"
    ),
    CORNER_BADGE(
        "CORNER_BADGE",
        "Transparent Corner Badge",
        "Minimalist corner glass badge with instant tap-to-solve"
    ),
    BUBBLE(
        "BUBBLE",
        "Elastic Prism Orb",
        "Draggable liquid-physics orb with spring-rebound docking"
    );

    companion object {
        fun fromId(id: String?): HudShapeStyle =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: DYNAMIC_ISLAND
    }
}

enum class StealthDisguiseMode(val id: String, val title: String, val description: String) {
    NONE("NONE", "Standard Prism Badge", "Shows crisp answer badge (B, A/C, True, 42) + confidence"),
    BATTERY("BATTERY", "Battery Telemetry", "91% = A • 92% = B • 93% = C • 94% = D • 95% = E • 96% = F"),
    CLOCK("CLOCK", "Digital Clock Widget", "12:01 = A • 12:02 = B • 12:03 = C • 12:04 = D • 12:05 = E"),
    NETWORK("NETWORK", "Network Speed Meter", "0.91 MB/s = A • 0.92 MB/s = B • 0.93 MB/s = C • 0.94 MB/s = D"),
    FOUR_DOTS("FOUR_DOTS", "Subtle 4-Dot Indicator", "Lights dot 1..4 for A..D (●○○○ = A, ○●○○ = B)");

    companion object {
        fun fromId(id: String?): StealthDisguiseMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: NONE
    }
}
