package com.glimpse.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Saves a message photo (already hosted in Firebase Storage) to the device's
// own Pictures/Glimpse gallery folder, so a photo your partner sent isn't
// stuck living only inside the app.
object ImageSaver {
    private const val TAG = "ImageSaver"

    suspend fun saveToGallery(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = downloadBitmap(context, imageUrl) ?: return@withContext false
            val filename = "Glimpse_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Glimpse")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: return@withContext false
            } else {
                // Pre-Q has no scoped-storage MediaStore insert path; this
                // legacy call handles both the file write and the gallery
                // index entry, but needs WRITE_EXTERNAL_STORAGE granted
                // first (requested by the caller on these OS versions only).
                @Suppress("DEPRECATION")
                val inserted = MediaStore.Images.Media.insertImage(
                    context.contentResolver, bitmap, filename, null
                )
                if (inserted == null) return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed", e)
            false
        }
    }

    private suspend fun downloadBitmap(context: Context, url: String): Bitmap? = try {
        val imageLoader = ImageLoader.Builder(context).build()
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    } catch (e: Exception) {
        Log.e(TAG, "downloadBitmap failed", e)
        null
    }
}
