# Audio Indexer (Android)

A modern Android application built with **Jetpack Compose**, **Kotlin**, **Coroutines**, and **Room Database**. It features an autonomous background foreground service capable of deep scanning internal storage and SD card directories for audio files (`.mp3`, `.wav`, `.m4a`), indexing them into a Room database `songs` table with a `lyricsSnippet` placeholder, and executing an active **FileObserver** & **Delta-Check** isolation system that captures newly added tracks into a `staged_songs` staging table without processing.

---

## 🚀 Key Features

1. **Storage-Wide Audio Discovery**:
   - Recursively traverses all mounted **Internal Shared Storage** (`Environment.getExternalStorageDirectory()`) and removable **SD Cards** (via `StorageManager` volume discovery, `ContextCompat.getExternalFilesDirs()`, and `/storage` mount points).
   - Filters specifically for `.mp3`, `.wav`, and `.m4a` files.
   - Extracts ID3 metadata (Title, Artist, Album, Duration, File Size) via `android.media.MediaMetadataRetriever` with robust fallbacks.

2. **Room Database Architecture**:
   - **`songs` table**: Main production catalog storing:
     - `filePath`: Absolute file path (unique index).
     - `title`: Audio track title.
     - `artist`: Artist / performer name.
     - `album`: Album or enclosing collection.
     - `lyricsSnippet`: Text field for lyrics previews / snippets (initialized with placeholder text).
     - `durationMs`: Track length in milliseconds.
     - `fileSize`: File size in bytes.
     - `extension`: Format (`mp3`, `wav`, `m4a`).
     - `dateAdded` & `dateModified`: Unix timestamps.
   - **`staged_songs` table**: Staging isolation table holding newly detected tracks **without processing**.

3. **Delta-Check & Real-Time FileObserver System**:
   - **Delta-Check System**: Compares actual physical storage contents against existing records in `songs` and `staged_songs`. Newly detected files on disk are isolated and inserted into `staged_songs` as unprocessed tracks.
   - **AudioFileObserverManager**: Listens to kernel `inotify` events (`CREATE`, `MOVED_TO`, `CLOSE_WRITE`) across standard audio directories and SD card paths. Instantly isolates incoming audio arrivals into `staged_songs` without modifying the main index.
   - **Staging Review UI**: Allows reviewing isolated tracks, inspecting metadata/lyrics placeholders, batch promoting to the main library, or dismissing them.

4. **Background Foreground Service (`AudioScannerService`)**:
   - Executes long-running storage sweeps without getting terminated by Android's memory killer or background execution limits.
   - Features ongoing low-priority notification with live progress counters.
   - Connects directly to the UI via bound service interface and Kotlin `StateFlow`.

5. **Permissions Architecture**:
   - `READ_EXTERNAL_STORAGE` (for Android 12 and below).
   - `READ_MEDIA_AUDIO` (for Android 13+ / API 33+).
   - `MANAGE_EXTERNAL_STORAGE` ("All Files Access" on Android 11+ / API 30+) required to traverse arbitrary non-media directories and SD cards.
   - `POST_NOTIFICATIONS` & `FOREGROUND_SERVICE_DATA_SYNC`.
   - In-app interactive permission banner directing users straight to the system All Files Access settings activity.

6. **Jetpack Compose UI (Material 3)**:
   - Sleek dark/light theme with cyan & violet accents.
   - **Library Tab**: Searchable catalog of indexed songs with duration and lyrics snippet preview chips.
   - **Staging Tab**: View of isolated tracks held in staging with "Process & Index" and "Clear" actions.
   - **Scanner Tab**: Scan controls, live progress bars, FileObserver toggle, and storage mount point diagnostics.
   - **Song Detail Modal**: Modal inspect window showcasing all fields including `lyricsSnippet`.

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/audioindexer/
│   ├── AudioIndexerApp.kt              # Application class & DB singleton
│   ├── MainActivity.kt                 # Compose activity & permission handlers
│   ├── data/
│   │   ├── model/
│   │   │   ├── Song.kt                 # 'songs' Room entity
│   │   │   └── StagedSong.kt           # 'staged_songs' Room entity
│   │   ├── dao/
│   │   │   ├── SongDao.kt              # Reactive Song queries & batch inserts
│   │   │   └── StagedSongDao.kt        # StagedSong queries & isolation DAO
│   │   ├── db/
│   │   │   └── AudioDatabase.kt        # RoomDatabase definition (version 1)
│   │   └── repository/
│   │       └── AudioRepository.kt      # Repository coordinating data flows & promotions
│   ├── service/
│   │   ├── AudioScannerService.kt      # Foreground service for scanning & monitoring
│   │   ├── AudioScanner.kt             # Traversal engine & delta-check algorithm
│   │   ├── AudioFileObserver.kt        # Inotify recursive directory watcher
│   │   └── MetadataExtractor.kt        # MediaMetadataRetriever parser & lyrics placeholder
│   ├── util/
│   │   ├── StorageUtils.kt             # Internal storage & SD card volume detector
│   │   └── PermissionUtils.kt          # Granular & All Files Access permission helpers
│   └── ui/
│       ├── MainViewModel.kt            # Coroutine StateFlow ViewModel
│       ├── MainScreen.kt               # Tabbed Jetpack Compose screen
│       ├── theme/
│       │   ├── Color.kt                # Cyan, violet, dark surface colors
│       │   ├── Theme.kt                # Material 3 ColorScheme
│       │   └── Type.kt                 # Typography definitions
│       └── components/
│           ├── PermissionCard.kt       # Permission status & request triggers
│           ├── ScanControlsCard.kt     # Full scan, delta check, observer toggles
│           ├── StorageVolumesCard.kt   # Internal & SD Card volume info
│           ├── SongItemCard.kt         # Card for indexed songs
│           ├── StagedSongItemCard.kt   # Card for isolated staged tracks
│           └── SongDetailDialog.kt     # Detailed song & lyrics snippet dialog
└── res/
    ├── drawable/                       # Vector assets & icons
    ├── mipmap-anydpi-v26/              # Adaptive launcher icons
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

---

## 🛠️ Build and Run

To build the APK or open the project:
1. Open the directory `new app` in **Android Studio**.
2. Run standard Gradle assemble:
   ```bash
   ./gradlew assembleDebug
   ```
3. Deploy to a connected physical device or emulator running Android 8.0+ (API 26+) up to Android 15 (API 35).
