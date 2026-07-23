package com.glimpse.app.data.model

// shared/garden — the two persisted, hand-set facts about the garden.
// Everything else about it (growth stage, wilt) is derived live from
// existing message history (see GardenGrowth) rather than stored here, so
// there's only ever one source of truth for "how active have you two been."
data class GardenInfo(
    val name: String = "",
    val namedBy: String = "",
    val namedAt: Long = 0L,
    // Ratchets upward only (see FirebaseSync.raiseGardenPeakStreak) — the
    // highest streak either of you has ever reached, so a lapse wilts the
    // plant gradually down from its peak instead of snapping it back to a
    // seed the instant the current streak itself resets to 0.
    val peakStreakDays: Int = 0
) {
    val isNamed: Boolean get() = name.isNotBlank()
}
