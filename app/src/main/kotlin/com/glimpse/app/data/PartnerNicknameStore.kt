package com.glimpse.app.data

import android.content.Context
import androidx.core.content.edit

// A local mirror of users/{myUid}/settings/partnerNickname (see
// FirebaseSync.fetchPartnerNicknameOnce), kept purely so FCMService can
// read the nickname *synchronously* while handling an incoming nudge: the
// Morse vibration pattern is derived from it (see MorseVibration), and
// blocking a push handler on a network round-trip just to learn a name
// you already know would delay the buzz that is the whole point.
//
// Not a source of truth — the Firebase node still is, and this is
// refreshed from it every time NicknameViewModel loads or saves. An empty
// value here just means "never loaded on this device yet", which callers
// treat as a fallback case rather than as "no nickname set".
object PartnerNicknameStore {
    private const val PREFS_NAME = "partner_nickname_prefs"
    private const val KEY_NICKNAME = "partner_nickname"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "")
            .orEmpty()

    fun save(context: Context, nickname: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_NICKNAME, nickname.trim())
        }
    }
}
