package com.glimpse.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

// Local-only, per-device customization ("how MY widget looks on MY home
// screen"), same as partner nicknames — never touches Firebase, never seen
// by the partner. A picked photo's content:// URI grant isn't guaranteed to
// survive past the current session (the modern photo picker's grant is
// scoped, not persistable), so this decodes and re-compresses a durable copy
// into app-internal storage immediately rather than holding onto the URI.
object WidgetBackgroundPhotoStore {
    private const val FILE_NAME = "widget_background.jpg"

    // Keeps the decoded bitmap comfortably under the RemoteViews Parcel
    // transaction size limit — same reasoning and value as
    // WidgetRenderer.loadBitmap's downscaling for photo messages.
    private const val MAX_DIMENSION = 480

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    fun save(context: Context, uri: Uri): Boolean = try {
        val original = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it) }
        if (original == null) {
            false
        } else {
            val scaled = if (original.width <= MAX_DIMENSION && original.height <= MAX_DIMENSION) {
                original
            } else {
                val scale = MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                )
            }
            file(context).outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            true
        }
    } catch (e: Exception) {
        false
    }

    fun clear(context: Context): Boolean {
        val target = file(context)
        return if (target.exists()) target.delete() else true
    }

    fun loadBitmap(context: Context): Bitmap? = try {
        val target = file(context)
        if (target.exists()) BitmapFactory.decodeFile(target.absolutePath) else null
    } catch (e: Exception) {
        null
    }
}
