package com.omnisolve.overlay.model

/**
 * Core solving engines for Prism AI Solver:
 * 1) [CIRCLE_TO_SEARCH]  — Background Circle to Search (Instant 2-4s): Sends text + cropped photo to background Google Search + Micro-Vision race.
 * 2) [DIRECT_VISION]     — Direct Vision AI (Photo Solver): Sends compressed screen snapshot directly to Multimodal Gemini Vision.
 * 3) [GHOST_DOM]         — Ghost DOM Reader (Zero-Screenshot): Reads Android's internal UI tree (AccessibilityNodeInfo) with 0ms OCR delay.
 * 4) [BACKGROUND_GEMINI] — OCR + Gemini AI: Screenshot -> On-device ML Kit OCR -> Background Gemini Flash.
 * 5) [API_KEYS]          — Personal API Keys: Uses locally stored keys (Gemini, Groq, OpenRouter, NVIDIA, OpenAI) with auto quota shift.
 * 6) [FIREBASE_SERVER]   — Cloud Server AI: Sends extracted question to Cloud Gemini Server.
 * 7) [TURBO_HYBRID]      — Turbo Auto-Hybrid (All-in-One backup mode).
 * 8) [OVERLAPPING_GOOGLE]— Interactive floating Google Gemini overlay window.
 */
enum class SolverMethod(
    val id: String,
    val shortName: String,
    val title: String,
    val subtitle: String,
    val beginnerTag: String,
    val howItWorksSimple: String,
    val ratingScore: String,
    val ratingStars: Int,
    val ratingLabel: String,
    val isRecommendedDefault: Boolean = false
) {
    BACKGROUND_GEMINI(
        id = "BACKGROUND_GEMINI",
        shortName = "Fast Answer",
        title = "Fast Answer",
        subtitle = "Reads questions from your screen and replies quickly without leaving your app.",
        beginnerTag = "Default • Fast",
        howItWorksSimple = "Reads your screen only when you tap the pill and shows the answer in seconds.",
        ratingScore = "4.8",
        ratingStars = 5,
        ratingLabel = "Top Choice • Works Signed Out",
        isRecommendedDefault = true
    ),
    TURBO_HYBRID(
        id = "TURBO_HYBRID",
        shortName = "Best Accuracy",
        title = "Best Accuracy",
        subtitle = "Double-checks multiple smart helpers together and highlights when they agree.",
        beginnerTag = "Most Accurate",
        howItWorksSimple = "Compares multiple helpers and shows when they agree on the same answer.",
        ratingScore = "4.9",
        ratingStars = 5,
        ratingLabel = "Highest Accuracy (4.9/5)"
    ),
    FIREBASE_SERVER(
        id = "FIREBASE_SERVER",
        shortName = "Cloud Helper",
        title = "Cloud Helper",
        subtitle = "Uses our main cloud solver for clean answers with a short explanation.",
        beginnerTag = "Cloud",
        howItWorksSimple = "Checks your question on our cloud helper and sends back a clear explanation.",
        ratingScore = "4.7",
        ratingStars = 5,
        ratingLabel = "Clear Explanations"
    ),
    CIRCLE_TO_SEARCH(
        id = "CIRCLE_TO_SEARCH",
        shortName = "Web + Facts",
        title = "Web Facts Lookup",
        subtitle = "Checks live web facts alongside AI — handy for trivia, history, and GK.",
        beginnerTag = "Web Facts",
        howItWorksSimple = "Looks up reliable web facts and matches them to the best option.",
        ratingScore = "4.7",
        ratingStars = 5,
        ratingLabel = "Great for Facts & Trivia"
    ),
    DIRECT_VISION(
        id = "DIRECT_VISION",
        shortName = "Photo & Diagrams",
        title = "Photo & Diagrams",
        subtitle = "Understands charts, geometry shapes, and math equations right off your screen.",
        beginnerTag = "Math & Charts",
        howItWorksSimple = "Reads images and math symbols directly so diagrams stay accurate.",
        ratingScore = "4.6",
        ratingStars = 5,
        ratingLabel = "Best for Diagrams & Math"
    ),
    GHOST_DOM(
        id = "GHOST_DOM",
        shortName = "Direct Text",
        title = "Direct App Text Reader",
        subtitle = "Reads visible question text cleanly from supported quiz apps.",
        beginnerTag = "Supported Apps",
        howItWorksSimple = "Reads visible text inside quiz apps and falls back to screen reading if needed.",
        ratingScore = "4.5",
        ratingStars = 4,
        ratingLabel = "Fast in Quiz Apps"
    ),
    API_KEYS(
        id = "API_KEYS",
        shortName = "Personal Keys",
        title = "Your Own API Keys",
        subtitle = "Uses personal keys you saved in Settings and switches automatically if one is busy.",
        beginnerTag = "Bring Your Key",
        howItWorksSimple = "Uses your personal keys with automatic backup switching.",
        ratingScore = "4.7",
        ratingStars = 5,
        ratingLabel = "For Power Users"
    ),
    OVERLAPPING_GOOGLE(
        id = "OVERLAPPING_GOOGLE",
        shortName = "Floating Window",
        title = "Floating AI Window",
        subtitle = "Keeps a small movable AI window open over any app for follow-up questions.",
        beginnerTag = "Full View",
        howItWorksSimple = "Opens a resizable window above your app.",
        ratingScore = "4.3",
        ratingStars = 4,
        ratingLabel = "Follow-Up Help"
    );

    companion object {
        val everydayThree: List<SolverMethod> = listOf(
            BACKGROUND_GEMINI,
            TURBO_HYBRID,
            DIRECT_VISION
        )

        val advancedModes: List<SolverMethod> = listOf(
            FIREBASE_SERVER,
            CIRCLE_TO_SEARCH,
            GHOST_DOM,
            API_KEYS
        )

        val primaryThree: List<SolverMethod> = listOf(
            BACKGROUND_GEMINI,
            TURBO_HYBRID,
            FIREBASE_SERVER,
            CIRCLE_TO_SEARCH,
            DIRECT_VISION,
            GHOST_DOM,
            API_KEYS
        )

        fun fromId(id: String?): SolverMethod {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BACKGROUND_GEMINI
        }
    }
}
