package com.notmugil.uta.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object PlaylistCoverManager {
    private const val DIR_NAME = "playlist_covers"

    private fun getCoverFile(context: Context, playlistId: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        return File(dir, "$playlistId.png")
    }

    fun hasCustomCover(context: Context, playlistId: String): Boolean = getCoverFile(context, playlistId).exists()

    fun getCustomCoverFile(context: Context, playlistId: String): File? {
        val file = getCoverFile(context, playlistId)
        return if (file.exists()) file else null
    }

    fun saveCustomCover(context: Context, playlistId: String, bitmap: Bitmap): File {
        val file = getCoverFile(context, playlistId)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file
    }

    fun deleteCustomCover(context: Context, playlistId: String) {
        val file = getCoverFile(context, playlistId)
        if (file.exists()) {
            file.delete()
        }
    }
}
