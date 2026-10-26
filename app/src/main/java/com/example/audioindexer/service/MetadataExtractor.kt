package com.example.audioindexer.service

import android.media.MediaMetadataRetriever
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import java.io.File
import java.util.Locale

import com.example.audioindexer.util.AudioClassifier

data class AudioMetadata(
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val lyricsSnippet: String,
    val durationMs: Long,
    val fileSize: Long,
    val extension: String,
    val isMusic: Boolean = true,
    val audioType: String = "MUSIC",
    val classificationReason: String = ""
)

object MetadataExtractor {

    /**
     * Extracts audio metadata using MediaMetadataRetriever and AudioClassifier.
     * Intelligently distinguishes genuine songs from voice notes, ringtones, and recordings.
     */
    fun extract(file: File): AudioMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long = 0L
        val ext = file.extension.lowercase(Locale.ROOT)
        val fileSize = file.length()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            // File may be locked, unreadable, or missing ID3 headers
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        val resolvedTitle = if (!title.isNullOrBlank()) title.trim() else file.nameWithoutExtension
        val resolvedArtist = if (!artist.isNullOrBlank()) artist.trim() else "Unknown Artist"
        val resolvedAlbum = if (!album.isNullOrBlank()) album.trim() else (file.parentFile?.name ?: "Unknown Album")

        // Intelligent song vs non-song classification
        val classification = AudioClassifier.classify(
            file = file,
            durationMs = durationMs,
            fileSize = fileSize,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum
        )

        // Placeholder text field for lyricsSnippet
        val lyricsSnippet = "Placeholder: Lyrics for '$resolvedTitle' by $resolvedArtist (pending indexer parse)"

        return AudioMetadata(
            filePath = file.absolutePath,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            lyricsSnippet = lyricsSnippet,
            durationMs = durationMs,
            fileSize = fileSize,
            extension = ext,
            isMusic = classification.isSong,
            audioType = classification.type.name,
            classificationReason = classification.reason
        )
    }

    fun toSong(metadata: AudioMetadata): Song {
        return Song(
            filePath = metadata.filePath,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            lyricsSnippet = metadata.lyricsSnippet,
            isMusic = metadata.isMusic,
            audioType = metadata.audioType,
            durationMs = metadata.durationMs,
            fileSize = metadata.fileSize,
            extension = metadata.extension,
            dateAdded = System.currentTimeMillis()
        )
    }

    fun toStagedSong(metadata: AudioMetadata, source: String = "DELTA_CHECK"): StagedSong {
        return StagedSong(
            filePath = metadata.filePath,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            lyricsSnippet = metadata.lyricsSnippet,
            isMusic = metadata.isMusic,
            audioType = metadata.audioType,
            durationMs = metadata.durationMs,
            fileSize = metadata.fileSize,
            extension = metadata.extension,
            detectedAt = System.currentTimeMillis(),
            source = source,
            status = "STAGED_UNPROCESSED"
        )
    }
}
