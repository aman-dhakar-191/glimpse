package com.glimpse.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// Saves a message video (already hosted in Firebase Storage) to the
// device's own Movies/Glimpse gallery folder — same reasoning as
// ImageSaver, but streaming raw bytes via OkHttp instead of decoding
// through Coil, since a video isn't something Coil can decode as a bitmap
// anyway and streaming avoids holding the whole file in memory at once.
object VideoSaver {
    private const val TAG = "VideoSaver"
    private val client = OkHttpClient()

    suspend fun saveToGallery(context: Context, videoUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(videoUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@withContext false

                val filename = "Glimpse_${System.currentTimeMillis()}.mp4"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Glimpse")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    } ?: return@withContext false
                } else {
                    // Pre-Q has no scoped-storage MediaStore insert path —
                    // write directly into the public Movies/Glimpse folder
                    // and index it via MediaStore.Video.Media.DATA, same
                    // legacy approach ImageSaver's insertImage() call wraps
                    // for photos (needs WRITE_EXTERNAL_STORAGE, requested by
                    // the caller on these OS versions only).
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val glimpseDir = File(moviesDir, "Glimpse").apply { mkdirs() }
                    val file = File(glimpseDir, filename)
                    file.outputStream().use { out -> body.byteStream().use { input -> input.copyTo(out) } }
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DATA, file.absolutePath)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    }
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed", e)
            CrashLogger.recordException("VideoSaver.saveToGallery failed (videoUrl=$videoUrl)", e)
            false
        }
    }
}
