package com.neko.music.data.manager

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.neko.music.data.model.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class LocalMusicManager(private val context: Context) {

    suspend fun scanLocalMusic(): List<Music> = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Music>()
        resolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                val displayName = cursor.getStringOrNull(displayNameColumn).orEmpty()
                val title = cursor.getStringOrNull(titleColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    ?: displayName.removeFileExtension().ifBlank { "Local Music" }
                val artist = cursor.getStringOrNull(artistColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                val album = cursor.getStringOrNull(albumColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                val durationMs = cursor.getLongOrZero(durationColumn)
                val dateAddedSec = cursor.getLongOrZero(dateAddedColumn)

                songs += Music(
                    id = mediaId.toLocalMusicId(),
                    title = title,
                    artist = artist,
                    album = album,
                    duration = (durationMs / 1000L).toInt().coerceAtLeast(0),
                    filePath = uri,
                    coverFilePath = null,
                    uploadUserId = null,
                    createdAt = (dateAddedSec * 1000L).takeIf { it > 0L }?.toString(),
                )
            }
        }
        songs
    }

    private fun Long.toLocalMusicId(): Int {
        val positive = (this % Int.MAX_VALUE).toInt().absoluteValue.coerceAtLeast(1)
        return -positive
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }

    private fun android.database.Cursor.getLongOrZero(columnIndex: Int): Long {
        return if (isNull(columnIndex)) 0L else getLong(columnIndex)
    }

    private fun String.isUnknownMediaStoreValue(): Boolean {
        return this == MediaStore.UNKNOWN_STRING || equals("<unknown>", ignoreCase = true)
    }

    private fun String.removeFileExtension(): String {
        val lastDot = lastIndexOf('.')
        return if (lastDot > 0) substring(0, lastDot) else this
    }
}
