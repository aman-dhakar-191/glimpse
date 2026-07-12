package com.glimpse.app.data.model

// Recurring (month/day only, no year) — an anniversary/birthday is
// fundamentally a yearly-repeating event, and dropping the year avoids the
// ambiguity of "count down to the original date" vs "count down to the next
// occurrence" once that date has passed. @JvmOverloads for the Realtime
// Database SDK's reflection-based deserializer, same as User/Message.
data class SpecialDate @JvmOverloads constructor(
    val label: String = "",
    val month: Int = 0,
    val day: Int = 0
)
