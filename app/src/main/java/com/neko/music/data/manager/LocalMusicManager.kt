package com.neko.music.data.manager

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.neko.music.data.cache.MusicCacheManager
import com.neko.music.data.model.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import kotlin.math.absoluteValue

class LocalMusicManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalMusicManager"
    }

    suspend fun scanLocalMusic(): List<Music> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val cacheManager = MusicCacheManager.getInstance(appContext)
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
            MediaStore.Audio.Media.DATA,
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
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, mediaId)
                val uri = contentUri.toString()
                val displayName = cursor.getStringOrNull(displayNameColumn).orEmpty()
                val filePath = cursor.getStringOrNull(dataColumn)
                val fallbackTitle = cursor.getStringOrNull(titleColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    ?: displayName.removeFileExtension().ifBlank { "Local Music" }
                val fallbackArtist = cursor.getStringOrNull(artistColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                val fallbackAlbum = cursor.getStringOrNull(albumColumn)
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                val durationMs = cursor.getLongOrZero(durationColumn)
                val dateAddedSec = cursor.getLongOrZero(dateAddedColumn)
                val localMusicId = mediaId.toLocalMusicId()
                val metadata = readEmbeddedMetadata(contentUri)
                val lyrics = readSidecarLyrics(filePath) ?: readId3Lyrics(filePath)
                val coverFile = metadata.coverBytes?.let { bytes ->
                    cacheManager.saveLocalCover(localMusicId, bytes).getOrNull()
                }
                if (!lyrics.isNullOrBlank()) {
                    cacheManager.saveLocalLyrics(localMusicId, lyrics)
                }
                val title = metadata.title
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle
                val artist = metadata.artist
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackArtist
                val album = metadata.album
                    ?.takeUnless { it.isUnknownMediaStoreValue() }
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackAlbum
                val resolvedDurationMs = metadata.durationMs.takeIf { it > 0L } ?: durationMs
                val extraMetadata = buildLocalMetadataSummary(
                    albumArtist = metadata.albumArtist,
                    composer = metadata.composer,
                    genre = metadata.genre,
                    year = metadata.year,
                    discNumber = metadata.discNumber,
                    trackNumber = metadata.trackNumber,
                    displayName = displayName,
                    hasLyrics = !lyrics.isNullOrBlank(),
                )

                songs += Music(
                    id = localMusicId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = (resolvedDurationMs / 1000L).toInt().coerceAtLeast(0),
                    filePath = uri,
                    coverFilePath = coverFile?.absolutePath,
                    uploadUserId = null,
                    createdAt = extraMetadata.ifBlank {
                        (dateAddedSec * 1000L).takeIf { it > 0L }?.toString().orEmpty()
                    },
                    lrc = !lyrics.isNullOrBlank(),
                    albumArtist = metadata.albumArtist,
                    composer = metadata.composer,
                    genre = metadata.genre,
                    year = metadata.year,
                    discNumber = metadata.discNumber,
                    trackNumber = metadata.trackNumber,
                    fileName = displayName,
                    lyricsPreview = lyrics?.lines()
                        ?.map { it.trim() }
                        ?.firstOrNull { it.isNotBlank() }
                )
            }
        }
        songs
    }

    private data class EmbeddedMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val composer: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val discNumber: String? = null,
        val trackNumber: String? = null,
        val durationMs: Long = 0L,
        val coverBytes: ByteArray? = null,
    )

    private fun readEmbeddedMetadata(uri: Uri): EmbeddedMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context.applicationContext, uri)
            EmbeddedMetadata(
                title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                composer = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                genre = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                discNumber = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                trackNumber = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                durationMs = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                coverBytes = retriever.embeddedPicture,
            )
        } catch (e: Exception) {
            Log.w(TAG, "读取本地音频元数据失败: $uri", e)
            EmbeddedMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun MediaMetadataRetriever.metadata(keyCode: Int): String? {
        return extractMetadata(keyCode)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readSidecarLyrics(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        val audioFile = File(filePath)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val candidates = listOf(
            File(parent, "$baseName.lrc"),
            File(parent, "$baseName.LRC"),
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }?.let { file ->
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "读取同名歌词失败: ${file.absolutePath}", e)
                null
            }
        }
    }

    private fun readId3Lyrics(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.exists() || file.length() < 20L) return null
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(10)
                if (input.read(header) != 10) return null
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }
                val majorVersion = header[3].toInt() and 0xFF
                val tagSize = synchsafeToInt(header, 6)
                if (tagSize <= 0 || tagSize > 4 * 1024 * 1024) return null
                val data = input.readNBytesCompat(tagSize)
                parseId3LyricsFrames(data, majorVersion)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 ID3 歌词失败: ${file.absolutePath}", e)
            null
        }
    }

    private fun parseId3LyricsFrames(data: ByteArray, majorVersion: Int): String? {
        var offset = 0
        while (offset + 10 <= data.size) {
            val frameId = data.decodeAscii(offset, 4)
            if (frameId.all { it.code == 0 }) break
            val frameSize = if (majorVersion == 4) {
                synchsafeToInt(data, offset + 4)
            } else {
                int32ToInt(data, offset + 4)
            }
            if (frameSize <= 0 || offset + 10 + frameSize > data.size) break
            if (frameId == "USLT" || frameId == "SYLT") {
                decodeLyricsFrame(data.copyOfRange(offset + 10, offset + 10 + frameSize))?.let { lyrics ->
                    if (lyrics.isNotBlank()) return lyrics
                }
            }
            offset += 10 + frameSize
        }
        return null
    }

    private fun decodeLyricsFrame(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val charset = when (frame[0].toInt() and 0xFF) {
            1 -> Charsets.UTF_16
            2 -> Charset.forName("UTF-16BE")
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        var offset = 4
        offset = skipEncodedTerminator(frame, offset, charset)
        if (offset >= frame.size) return null
        return frame.copyOfRange(offset, frame.size)
            .toString(charset)
            .trim('\u0000', '\n', '\r', ' ', '\t')
            .takeIf { it.isNotBlank() }
    }

    private fun skipEncodedTerminator(bytes: ByteArray, start: Int, charset: Charset): Int {
        var index = start
        val twoByteTerminator = charset.name().startsWith("UTF-16", ignoreCase = true)
        while (index < bytes.size) {
            if (twoByteTerminator) {
                if (index + 1 < bytes.size && bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) {
                    return index + 2
                }
                index += 2
            } else {
                if (bytes[index] == 0.toByte()) return index + 1
                index += 1
            }
        }
        return bytes.size
    }

    private fun synchsafeToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun int32ToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String {
        return copyOfRange(offset, offset + length).toString(Charsets.ISO_8859_1)
    }

    private fun java.io.InputStream.readNBytesCompat(size: Int): ByteArray {
        val result = ByteArray(size)
        var total = 0
        while (total < size) {
            val read = read(result, total, size - total)
            if (read <= 0) break
            total += read
        }
        return if (total == size) result else result.copyOf(total)
    }

    private fun buildLocalMetadataSummary(
        albumArtist: String?,
        composer: String?,
        genre: String?,
        year: String?,
        discNumber: String?,
        trackNumber: String?,
        displayName: String,
        hasLyrics: Boolean,
    ): String {
        return listOfNotNull(
            albumArtist?.takeIf { it.isNotBlank() }?.let { "albumArtist=$it" },
            composer?.takeIf { it.isNotBlank() }?.let { "composer=$it" },
            genre?.takeIf { it.isNotBlank() }?.let { "genre=$it" },
            year?.takeIf { it.isNotBlank() }?.let { "year=$it" },
            discNumber?.takeIf { it.isNotBlank() }?.let { "disc=$it" },
            trackNumber?.takeIf { it.isNotBlank() }?.let { "track=$it" },
            displayName.takeIf { it.isNotBlank() }?.let { "file=$it" },
            if (hasLyrics) "lyrics=true" else null,
        ).joinToString(";")
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
