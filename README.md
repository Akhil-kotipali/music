# Audio Indexer & Media Player (Android)

A state-of-the-art Android application built with **Jetpack Compose**, **Kotlin Coroutines**, **Room Database**, **Media3 ExoPlayer**, and a **Multi-Provider AI Integration Manager**.

The application features deep internal & SD card audio discovery, autonomous background foreground scanning, real-time inotify **FileObserver** tracking with unprocessed track staging, an **AI Integration Manager** (Gemini, Groq, OpenAI) with automated rate-limit rotation and audit logging, and a **MediaSessionService** powered by a zero-repeat **FatiguePreventionQueue**.

---

## 🚀 Key Feature Highlights

### 1. Multi-Provider AI Integration Manager (Gemini, Groq, OpenAI)
- **Multi-Provider API Support**:
  - **Google Gemini**: Uses `gemini-1.5-flash` with direct REST calls.
  - **Groq**: Ultra-low latency inference using `llama-3.3-70b-versatile` or `llama-3.1-8b-instant`.
  - **OpenAI**: Supports `gpt-4o-mini` with standard bearer authentication.
- **Key Security & Health Monitoring**:
  - Secure local storage of API keys with masked key displays (`••••••••`).
  - Active/Inactive toggling, manual replacement, and health check tests.
  - **Rate-Limiting Protection (HTTP 429)**: Detects rate-limiting, flags affected keys, and automatically rotates requests to healthy fallback keys across providers.
- **30-Second Text/Lyric Snippet Extraction**:
  - [AudioSnippetExtractor.kt](file:///c:/Users/krish/OneDrive/Desktop/new%20app/app/src/main/java/com/example/audioindexer/service/AudioSnippetExtractor.kt): If metadata tags are missing, probes the media file via `MediaExtractor` and binary ID3 scanners across the first 30 seconds (`30,000,000` microseconds) to extract embedded synchronized/unsynchronized lyrics (`USLT`/`SYLT`) or construct a 30-second acoustic audio profile.
- **Compose AI Categorization Prompt**:
  - Prompt: *"New songs detected. Allow AI categorisation?"*
  - Automatically queries the active AI provider with the strict system prompt:
    > *"Categorize this song into exactly one primary mood/genre tag from this list: Love, Motivational, Sad, High Energy, Relaxed, Focus."*
  - Stores the parsed tag into the Room database `songs` table under `ai_category`.
- **AI Audit Logging & Text Export**:
  - Logs every AI call: timestamp, provider, model, latency (ms), request prompt, response body, extracted category, and errors.
  - Full CRUD operations in the Compose UI: view detailed prompts/responses, delete single logs, and clear history.
  - **Export to .TXT File**: Generates formatted audit logs and launches the Android system share sheet via `FileProvider`.

---

### 2. ExoPlayer MediaSessionService & FatiguePreventionQueue
- **Media3 Background Audio Playback**:
  - [AudioPlaybackService.kt](file:///c:/Users/krish/OneDrive/Desktop/new%20app/app/src/main/java/com/example/audioindexer/playback/AudioPlaybackService.kt): Extends `androidx.media3.session.MediaSessionService` using `ExoPlayer`.
  - Supports background playback, lock-screen controls, and system media notifications.
- **FatiguePreventionQueue**:
  - [FatiguePreventionQueue.kt](file:///c:/Users/krish/OneDrive/Desktop/new%20app/app/src/main/java/com/example/audioindexer/playback/FatiguePreventionQueue.kt): Shuffles playlists dynamically and maintains a historical log of played track IDs.
  - **Zero-Repeat Rule**: Ensures **no song can be repeated or re-queued until every single song in the current playlist/queue has been played exactly once**.
  - When all songs in a playlist have been played, it advances to Cycle 2, re-shuffles dynamically, and guarantees that the first track of the new cycle is not the last track played.
- **Playback Controls & UI**:
  - Floating bottom mini player with Play/Pause, Next, Previous, and Fatigue Cycle indicator (`Cycle 1: 4/12 played`).
  - Full-screen player dialog with seek bar, waveform visualizer, AI mood badge, dynamic re-shuffle, and queue inspection.

---

### 3. SkipRecommendationEngine & Intelligent Playlist Pruning
- **Real-Time Hard Skip Detection**:
  - [SkipRecommendationEngine.kt](file:///c:/Users/krish/OneDrive/Desktop/new%20app/app/src/main/java/com/example/audioindexer/playback/SkipRecommendationEngine.kt): Analyzes user skips in real-time. If 'Next' is pressed within the first 15 seconds of playback (`< 15,000ms`), it registers as a **"Hard Skip"**.
- **Behavior 1: Short-Term Pivot**:
  - **Single Hard Skip**: Instantly reorders remaining tracks in the current queue cycle to pivot away from that song's `ai_category` (placing matching songs at the back of the queue).
  - **Consecutive Hard Skips (≥ 2)**: Narrows the upcoming queue aggressively to look for highly contrasted mood/genre categories (e.g., Sad ↔ High Energy/Motivational, Relaxed ↔ High Energy, Focus ↔ Love/High Energy).
  - **Hunting Mode (4+ Rapid Skips)**: Detects when a user skips 4 or more tracks rapidly within a 20-second rolling window. Enters "Hunting Mode" (assuming the user is searching for a specific song) and temporarily suspends all category penalties so recommendation metrics are not ruined.
- **Behavior 2: Long-Term Playlist Pruning**:
  - Tracks `skip_count` and `play_count` per playlist in Room database (`playlist_song_map` table).
  - If a song's skip-to-play ratio exceeds 80% (`skipRatio > 0.80`) inside a specific playlist after appearing at least 5 times (`totalAppearances >= 5`), the engine automatically deletes the song from that specific playlist map and notifies the user with an Android `Toast` notification (e.g., *"Auto-pruned 'Track Title' from Daily Mix (83% skip rate)"*).

---

### 4. Room Database Storage & Scanner Architecture
- **Room Database (`audio_indexer.db`)**:
  - `songs`: Production index storing `filePath` (unique), `title`, `artist`, `album`, `lyricsSnippet`, `ai_category`, duration, file size, format.
  - `staged_songs`: Isolated unprocessed tracks held by delta-checks or FileObservers.
  - `ai_keys`: Stored provider configurations, active status, rate limit status, and error counts.
  - `ai_logs`: Audit log records for every AI API call.
- **Delta-Check & Real-Time FileObserver**:
  - Traverses Internal Storage (`/storage/emulated/0`) and Removable SD Cards (`/storage/XXXX-XXXX`).
  - Filters strictly for `.mp3`, `.wav`, and `.m4a` files.
  - Newly discovered tracks are staged in `staged_songs` without processing until user review.
- **Permissions**:
  - `READ_EXTERNAL_STORAGE` (API ≤ 32) & `READ_MEDIA_AUDIO` (API 33+).
  - `MANAGE_EXTERNAL_STORAGE` (All Files Access) on Android 11+ (API 30+).
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

---

## 📂 Architecture Overview

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/audioindexer/
│   ├── AudioIndexerApp.kt              # Application class, DB & AiManager singletons
│   ├── MainActivity.kt                 # Compose activity & service bindings
│   ├── data/
│   │   ├── model/
│   │   │   ├── Song.kt                 # 'songs' Room entity (with ai_category)
│   │   │   ├── StagedSong.kt           # 'staged_songs' Room entity
│   │   │   ├── AiKeyConfig.kt          # Multi-provider API keys model
│   │   │   └── AiLog.kt                # Audit log entity with text export formatter
│   │   ├── dao/
│   │   │   ├── SongDao.kt              # Room DAO for songs & AI category queries
│   │   │   ├── StagedSongDao.kt        # DAO for isolated staged tracks
│   │   │   ├── AiKeyConfigDao.kt       # DAO for API key CRUD & rate limits
│   │   │   └── AiLogDao.kt             # DAO for AI audit logs
│   │   ├── db/
│   │   │   └── AudioDatabase.kt        # RoomDatabase v2
│   │   └── repository/
│   │       └── AudioRepository.kt      # Repository coordinating data flows
│   ├── playback/
│   │   ├── AudioPlaybackService.kt     # MediaSessionService with ExoPlayer
│   │   └── FatiguePreventionQueue.kt   # Zero-repeat queue algorithm
│   ├── service/
│   │   ├── AudioScannerService.kt      # Foreground service for scanning & monitoring
│   │   ├── AudioScanner.kt             # Traversal engine & delta-check algorithm
│   │   ├── AudioFileObserver.kt        # Inotify recursive directory watcher
│   │   ├── AudioSnippetExtractor.kt    # 30-second lyric/audio frame extractor
│   │   ├── MetadataExtractor.kt        # ID3 tags parser
│   │   └── ai/
│   │       └── AiManager.kt            # Gemini, Groq, OpenAI REST client & failover
│   ├── util/
│   │   ├── StorageUtils.kt             # Internal storage & SD card volume detector
│   │   └── PermissionUtils.kt          # All Files Access permission helpers
│   └── ui/
│       ├── MainViewModel.kt            # Coroutine StateFlow ViewModel
│       ├── MainScreen.kt               # Tabbed UI (Library, Staging, AI Manager, Scanner)
│       └── components/
│           ├── AiCategorizationDialog.kt # "New songs detected. Allow AI categorisation?"
│           ├── AiKeyComponents.kt        # Key cards & Add/Replace dialog
│           ├── AiLogComponents.kt        # Log cards & audit details
│           ├── AiManagerTab.kt           # AI Keys & Logs management tab
│           ├── PlaybackComponents.kt     # Mini player & Full player dialog
│           ├── PermissionCard.kt         # Permissions status card
│           ├── ScanControlsCard.kt       # Scanner controls card
│           ├── SongItemCard.kt           # Song item card with AI mood badge
│           └── SongDetailDialog.kt       # Full song details & lyrics dialog
└── res/
    ├── xml/
    │   └── file_paths.xml              # FileProvider configuration for .txt log sharing
    ├── drawable/
    └── values/
```

---

## 🛠️ Build and Deploy

1. Compile and build the debug APK locally:
   ```bash
   ./gradlew assembleDebug
   ```
2. Or trigger GitHub Actions CI:
   - Push to `main` branch or trigger manually via **Actions > Build Android APK > Run workflow**.
   - Download the generated `app-debug-apk` artifact from the summary page.
