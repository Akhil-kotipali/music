package com.example.audioindexer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.audioindexer.data.dao.AiKeyConfigDao
import com.example.audioindexer.data.dao.AiLogDao
import com.example.audioindexer.data.dao.CategoryDao
import com.example.audioindexer.data.dao.PlaylistDao
import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.dao.StagedSongDao
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiLog
import com.example.audioindexer.data.model.Category
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.model.PlaylistSongMap
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong

@Database(
    entities = [
        Song::class,
        StagedSong::class,
        AiKeyConfig::class,
        AiLog::class,
        Playlist::class,
        PlaylistSongMap::class,
        Category::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun stagedSongDao(): StagedSongDao
    abstract fun aiKeyConfigDao(): AiKeyConfigDao
    abstract fun aiLogDao(): AiLogDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AudioDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_keys` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `apiKey` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `isRateLimited` INTEGER NOT NULL,
                        `rateLimitResetTime` INTEGER NOT NULL,
                        `errorCount` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `lastUsedTime` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_keys_provider` ON `ai_keys` (`provider`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_keys_isActive` ON `ai_keys` (`isActive`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `provider` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `apiKeyMasked` TEXT NOT NULL,
                        `requestPrompt` TEXT NOT NULL,
                        `responseBody` TEXT NOT NULL,
                        `extractedCategory` TEXT,
                        `status` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `songId` INTEGER,
                        `songTitle` TEXT,
                        `errorMessage` TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_logs_timestamp` ON `ai_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_logs_provider` ON `ai_logs` (`provider`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_logs_status` ON `ai_logs` (`status`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `is_music` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `audio_type` TEXT NOT NULL DEFAULT 'MUSIC'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_is_music` ON `songs` (`is_music`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_audio_type` ON `songs` (`audio_type`)")

                db.execSQL("ALTER TABLE `staged_songs` ADD COLUMN `is_music` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `staged_songs` ADD COLUMN `audio_type` TEXT NOT NULL DEFAULT 'MUSIC'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_song_map` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlist_id` INTEGER NOT NULL,
                        `song_id` INTEGER NOT NULL,
                        `play_count` INTEGER NOT NULL,
                        `skip_count` INTEGER NOT NULL,
                        `last_played_at` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_song_map_playlist_id_song_id` ON `playlist_song_map` (`playlist_id`, `song_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlist_id` ON `playlist_song_map` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_song_id` ON `playlist_song_map` (`song_id`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                val now = System.currentTimeMillis()
                val defaults = listOf(
                    "('Love', 'Romantic and emotional tracks', '#FF4081', 1, $now)",
                    "('Motivational', 'Inspiring and energetic anthems', '#FF9100', 1, $now)",
                    "('Sad', 'Melancholic, sorrowful, or reflective songs', '#7C4DFF', 1, $now)",
                    "('High Energy', 'Fast, upbeat, dance, and workout tracks', '#00E676', 1, $now)",
                    "('Relaxed', 'Chill, acoustic, ambient, and lo-fi tracks', '#00E5FF', 1, $now)",
                    "('Focus', 'Instrumental, study, and deep concentration music', '#FFD600', 1, $now)"
                )
                db.execSQL("INSERT OR IGNORE INTO `categories` (`name`, `description`, `colorHex`, `isDefault`, `createdAt`) VALUES ${defaults.joinToString(", ")}")
            }
        }

        fun getDatabase(context: Context): AudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudioDatabase::class.java,
                    "audio_indexer.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val now = System.currentTimeMillis()
                            val defaults = listOf(
                                "('Love', 'Romantic and emotional tracks', '#FF4081', 1, $now)",
                                "('Motivational', 'Inspiring and energetic anthems', '#FF9100', 1, $now)",
                                "('Sad', 'Melancholic, sorrowful, or reflective songs', '#7C4DFF', 1, $now)",
                                "('High Energy', 'Fast, upbeat, dance, and workout tracks', '#00E676', 1, $now)",
                                "('Relaxed', 'Chill, acoustic, ambient, and lo-fi tracks', '#00E5FF', 1, $now)",
                                "('Focus', 'Instrumental, study, and deep concentration music', '#FFD600', 1, $now)"
                            )
                            db.execSQL("INSERT OR IGNORE INTO `categories` (`name`, `description`, `colorHex`, `isDefault`, `createdAt`) VALUES ${defaults.joinToString(", ")}")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
