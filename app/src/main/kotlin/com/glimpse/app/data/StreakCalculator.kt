package com.glimpse.app.data

import com.glimpse.app.data.model.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Shared between StatsViewModel (on-demand display) and StreakCheckWorker
// (daily background milestone check) so the two never drift apart on what
// "the streak" actually means.
object StreakCalculator {

    val MILESTONES = listOf(7, 14, 30, 50, 100, 200, 365, 500, 1000)

    // Consecutive days (counting back from today) with at least one message.
    // Today not having one yet doesn't zero the streak — same convention as
    // Duolingo/Snapchat, since the day isn't over.
    fun currentStreakDays(messages: List<Message>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val datesWithMessages = messages.map {
            Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
        }.toSet()
        if (datesWithMessages.isEmpty()) return 0

        var day = LocalDate.now(zone)
        if (day !in datesWithMessages) day = day.minusDays(1)
        var streak = 0
        while (day in datesWithMessages) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun isMilestone(days: Int): Boolean = days in MILESTONES
}
