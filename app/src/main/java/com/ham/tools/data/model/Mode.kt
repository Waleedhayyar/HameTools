package com.ham.tools.data.model

/**
 * Enum representing radio communication modes
 */
enum class Mode(val displayName: String) {
    SSB("SSB"),    // Single Side Band
    CW("CW"),      // Continuous Wave (Morse Code)
    FM("FM"),      // Frequency Modulation
    FT8("FT8");    // Digital mode

    companion object {
        /**
         * Get all modes as a list for UI selection
         */
        fun asList(): List<Mode> = entries
    }
}
