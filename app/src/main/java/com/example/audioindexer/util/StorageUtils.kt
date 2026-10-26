package com.example.audioindexer.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isRemovable: Boolean,
    val isAvailable: Boolean
)

object StorageUtils {

    // Supported audio extensions per user requirement
    val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a")

    /**
     * Identifies all storage directories on the device, including:
     * - Primary internal storage
     * - Removable SD cards
     * - Secondary external storage volumes
     */
    fun getAllStorageDirectories(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val seenPaths = mutableSetOf<String>()

        // 1. Primary Internal Storage via Environment
        val primaryInternal = Environment.getExternalStorageDirectory()
        if (primaryInternal != null && primaryInternal.exists() && primaryInternal.canRead()) {
            val path = primaryInternal.absolutePath
            seenPaths.add(path)
            volumes.add(
                StorageVolumeInfo(
                    name = "Internal Shared Storage",
                    path = path,
                    isRemovable = false,
                    isAvailable = true
                )
            )
        }

        // 2. StorageManager storage volumes (API 24+)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                for (volume in storageManager.storageVolumes) {
                    val dirFile: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        // Reflection fallback for API 24-29
                        try {
                            val getPathMethod = volume.javaClass.getMethod("getPathFile")
                            getPathMethod.invoke(volume) as? File
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (dirFile != null && dirFile.exists() && !seenPaths.contains(dirFile.absolutePath)) {
                        seenPaths.add(dirFile.absolutePath)
                        val isRemovable = volume.isRemovable
                        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            volume.getDescription(context)
                        } else {
                            if (isRemovable) "SD Card" else "Internal Storage"
                        }
                        volumes.add(
                            StorageVolumeInfo(
                                name = description ?: (if (isRemovable) "SD Card" else "Storage Volume"),
                                path = dirFile.absolutePath,
                                isRemovable = isRemovable,
                                isAvailable = dirFile.canRead()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Fall back gracefully
            }
        }

        // 3. Fallback via ContextCompat.getExternalFilesDirs for SD cards
        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalDirs) {
                if (dir != null) {
                    // Path is like /storage/XXXX-XXXX/Android/data/pkg/files
                    val root = extractVolumeRoot(dir.absolutePath)
                    if (root != null && !seenPaths.contains(root)) {
                        val file = File(root)
                        if (file.exists() && file.canRead()) {
                            seenPaths.add(root)
                            volumes.add(
                                StorageVolumeInfo(
                                    name = "SD Card (${file.name})",
                                    path = root,
                                    isRemovable = true,
                                    isAvailable = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log/ignore
        }

        // 4. Fallback check directly in /storage
        try {
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                val subDirs = storageRoot.listFiles()
                if (subDirs != null) {
                    for (sub in subDirs) {
                        val name = sub.name.lowercase(Locale.ROOT)
                        if (name != "self" && name != "knox-emulated" && sub.isDirectory && sub.canRead()) {
                            val path = sub.absolutePath
                            if (!seenPaths.contains(path)) {
                                seenPaths.add(path)
                                volumes.add(
                                    StorageVolumeInfo(
                                        name = if (name.contains("emulated")) "Internal Emulated" else "SD Card ($name)",
                                        path = path,
                                        isRemovable = !name.contains("emulated"),
                                        isAvailable = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Security or IO error
        }

        return volumes
    }

    /**
     * Extracts root storage mount point from nested Android/data path
     */
    private fun extractVolumeRoot(path: String): String? {
        val marker = "/Android"
        val idx = path.indexOf(marker)
        return if (idx > 0) {
            path.substring(0, idx)
        } else null
    }

    /**
     * Checks if a file has an audio extension matching requirements (.mp3, .wav, .m4a)
     */
    fun isAudioFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase(Locale.ROOT)
        return SUPPORTED_EXTENSIONS.contains(ext)
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSecs = durationMs / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
