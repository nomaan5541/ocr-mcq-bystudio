package com.omnisolve.overlay.model

enum class SolverMethod(
    val id: String,
    val title: String,
    val shortName: String,
    val subtitle: String,
    val isHomePrimary: Boolean
) {
    FIREBASE_SERVER(
        id = "FIREBASE_SERVER",
        title = "1) Server Sol",
        shortName = "Server Sol",
        subtitle = "Log in on server — sends the extracted screen question to server and returns the answer.",
        isHomePrimary = true
    ),
    BACKGROUND_GEMINI(
        id = "BACKGROUND_GEMINI",
        title = "2) OCR Text Extractor",
        shortName = "OCR Text Extractor",
        subtitle = "Previous method: takes screenshot → extracts text via OCR → sends to background Gemini Flash.",
        isHomePrimary = true
    ),
    API_KEYS(
        id = "API_KEYS",
        title = "3) API Key",
        shortName = "API Key",
        subtitle = "Uses locally stored keys from Settings (NVIDIA Free, OpenRouter, Gemini, OpenAI) with auto quota shift.",
        isHomePrimary = true
    ),
    OVERLAPPING_GOOGLE(
        id = "OVERLAPPING_GOOGLE",
        title = "Overlapping Google Completely",
        shortName = "Overlapping Google",
        subtitle = "Opens a full interactive floating Google / Gemini window overlapping completely on top of your screen.",
        isHomePrimary = false
    );

    companion object {
        val homeMethods: List<SolverMethod> = listOf(FIREBASE_SERVER, BACKGROUND_GEMINI, API_KEYS)

        fun fromId(raw: String?): SolverMethod {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.id == clean } ?: BACKGROUND_GEMINI
        }
    }
}
