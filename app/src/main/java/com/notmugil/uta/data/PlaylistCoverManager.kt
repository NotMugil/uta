package com.notmugil.uta.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object PlaylistCoverManager {
    private const val DIR_NAME = "playlist_covers"
    private val coverCache = ConcurrentHashMap<String, File?>()

    private fun getCoverFile(context: Context, playlistId: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        return File(dir, "$playlistId.png")
    }

    fun hasCustomCover(context: Context, playlistId: String): Boolean = getCustomCoverFile(context, playlistId) != null

    fun getCustomCoverFile(context: Context, playlistId: String): File? {
        val cached = coverCache[playlistId]
        if (cached != null) return cached

        val file = getCoverFile(context, playlistId)
        return if (file.exists() && file.length() > 0) {
            coverCache[playlistId] = file
            file
        } else {
            null
        }
    }

    fun saveCustomCover(context: Context, playlistId: String, bitmap: Bitmap): File {
        val file = getCoverFile(context, playlistId)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        coverCache[playlistId] = file
        return file
    }

    fun deleteCustomCover(context: Context, playlistId: String) {
        coverCache.remove(playlistId)
        val file = getCoverFile(context, playlistId)
        if (file.exists()) {
            file.delete()
        }
    }
}
