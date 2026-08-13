package com.softeen.nflocospicks.presentation.theme

/**
 * In-app team-logo size override for PickScreen only. Applied as a multiplier
 * on top of PickScreen's existing width-derived logo size (not a fixed dp),
 * so the responsive sizing behavior is preserved across screen sizes.
 * Persisted as a plain string key in DataStore (see
 * [UserPreferences.iconScalePreference]) to match the existing
 * `fontScalePreference` pattern.
 */
enum class IconScaleOption(val key: String, val multiplier: Float) {
    PEQUENO("pequeno", 0.6f),
    MEDIANO("mediano", 0.8f),
    GRANDE("grande", 1.0f);

    companion object {
        fun fromKey(key: String?): IconScaleOption =
            entries.firstOrNull { it.key == key } ?: GRANDE
    }
}
