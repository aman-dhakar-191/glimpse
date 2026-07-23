package com.glimpse.app.data

// Five stages, thresholded on the garden's peak streak (see GardenInfo) —
// deliberately mirroring StreakCalculator.MILESTONES' early values (7, 30)
// so "the plant blooms" and "you hit a streak milestone" line up.
enum class GardenStage(val minStreakDays: Int) {
    Seed(0),
    Sprout(1),
    Budding(3),
    Blooming(7),
    Flourishing(30);

    companion object {
        fun forStreak(streakDays: Int): GardenStage = entries.last { streakDays >= it.minStreakDays }
    }
}

// Pure functions of (peak streak, days since last message) — no Firebase
// access here, so the actual wilt math is easy to reason about/change
// independently of how those two numbers get fetched.
object GardenGrowth {
    // A single missed day (idleDays == 1, i.e. nothing sent today but
    // something was sent yesterday) doesn't wilt anything — only a longer
    // lapse does, one stage per WILT_STEP_DAYS, never below Seed.
    private const val WILT_GRACE_DAYS = 1
    private const val WILT_STEP_DAYS = 3

    fun currentStage(peakStreakDays: Int, idleDays: Int?): GardenStage {
        if (idleDays == null) return GardenStage.Seed
        val baseStage = GardenStage.forStreak(peakStreakDays)
        val wiltSteps = (idleDays - WILT_GRACE_DAYS).coerceAtLeast(0) / WILT_STEP_DAYS
        val targetOrdinal = (baseStage.ordinal - wiltSteps).coerceAtLeast(0)
        return GardenStage.entries[targetOrdinal]
    }

    fun isWilting(idleDays: Int?): Boolean = (idleDays ?: 0) > WILT_GRACE_DAYS
}
