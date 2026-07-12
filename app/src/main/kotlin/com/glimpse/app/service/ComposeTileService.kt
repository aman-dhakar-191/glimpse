package com.glimpse.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.glimpse.app.MainActivity

// A Quick Settings shortcut straight to Compose — one swipe-down-and-tap
// away, even faster than finding the home-screen widget. Purely a launcher;
// it has no on/off state of its own; the tile just always sits at
// STATE_INACTIVE so the system doesn't render it as an active toggle.
class ComposeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_OPEN_COMPOSE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // startActivityAndCollapse(Intent) was deprecated in API 34 in favor
        // of the PendingIntent overload — branching to keep both the old
        // (minSdk 26) and new path working instead of dropping support for
        // either.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
