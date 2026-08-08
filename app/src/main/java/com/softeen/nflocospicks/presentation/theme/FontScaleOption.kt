package com.softeen.nflocospicks.presentation.theme

/**
 * In-app font-size override, layered on top of (not a replacement for) the
 * Android system accessibility font scale. Persisted as a plain string key
 * in DataStore (see [UserPreferences.fontScalePreference]) to match the
 * existing `languageTag` pattern.
 */
enum class FontScaleOption(val key: String, val multiplier: Float) {
    PEQUENO("pequeno", 0.9f),
    NORMAL("normal", 1.0f),
    GRANDE("grande", 1.15f);

    companion object {
        fun fromKey(key: String?): FontScaleOption =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
