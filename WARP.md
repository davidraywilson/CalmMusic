# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Tooling, build, and test commands

This is a single-module Android app built with the Gradle Kotlin DSL and the Android Gradle Plugin.

- **Use the Gradle wrapper from the repo root**
  - General clean: `./gradlew clean`
  - All commands below assume they are run from the repository root.

- **JDK requirements**
  - `gradle.properties` pins `org.gradle.java.home` to a JDK 17 installation.
  - On machines where that path does not exist, either install JDK 17 at that location or update/remove `org.gradle.java.home` so Gradle can find a local JDK 17.

- **Build APKs**
  - Assemble a debug APK: `./gradlew :app:assembleDebug`
  - Assemble a release APK (unsigned unless you add signing configs): `./gradlew :app:assembleRelease`

- **Install/run on device or emulator**
  - Install the debug build on a connected device/emulator: `./gradlew :app:installDebug`
  - After install, launch the "CalmMusic" app from the launcher.

- **Android Lint / static checks**
  - Run Android Lint on the debug variant: `./gradlew :app:lintDebug`

- **Unit tests and instrumentation tests**
  - Run all unit tests for the app module: `./gradlew :app:testDebugUnitTest`
  - Run all connected (instrumentation) tests on a device/emulator: `./gradlew :app:connectedDebugAndroidTest`
  - Run a single unit test (replace the test class and/or method name as needed):
    - `./gradlew :app:testDebugUnitTest --tests "com.calmapps.calmmusic.YourTestClassName"`

## High-level architecture

### Project layout

- **Root Gradle build** (`build.gradle.kts`, `settings.gradle.kts`)
  - Single included module: `:app`.
  - Uses Android Gradle Plugin `8.2.0` and Kotlin `1.9.10`.
  - Global `clean` task deletes the root `build` directory.

- **Android app module** (`app`)
  - Uses Jetpack Compose, Navigation Compose, Material 3, and the Mudita Mindful Design (MMD) design system.
  - Depends on Apple MusicKit for Android via AARs in `app/libs/` and on AndroidX Media3 for local playback.
  - Uses Room as a local database and SharedPreferences for lightweight settings.

### Application and Apple Music wiring

- **`CalmMusic` `Application` class** (`app/src/main/java/com/calmapps/calmmusic/CalmMusicApplication.kt`)
  - Loads the native `appleMusicSDK` library in a companion object.
  - Creates and exposes (via `lateinit` properties) the core Apple Music integration objects:
    - `SimpleTokenProvider` – implements MusicKit's `TokenProvider` and owns the developer token and music user token.
    - `AuthenticationManager` – created via `AuthenticationFactory.createAuthenticationManager`.
    - `MediaPlayerController` – created via `MediaPlayerControllerFactory.createLocalController` and used by `AppleMusicPlayer`.
    - `AppleMusicAuthManager` – thin wrapper around `AuthenticationManager` and `SimpleTokenProvider` to build the sign-in intent and handle auth results.
    - `AppleMusicPlayer` – wrapper around `MediaPlayerController` that owns the Apple Music playback queue and exposes simple `playSongById` / `playQueueOfSongs` / `pause` / `resume` APIs.
    - `AppleMusicApiClient` – Retrofit-based Web API client returned from `AppleMusicApiClientImpl.create`.
    - `CalmMusicSettingsManager` – manages user settings (local music on/off, folders) via SharedPreferences + `StateFlow`s.
  - The `MainActivity` accesses these singletons by casting `application` to `CalmMusic`.

- **Token and auth helpers** (`AppleMusicCore.kt`)
  - `SimpleTokenProvider` stores the music user token in `SharedPreferences` and keeps the developer token in memory.
  - `AppleMusicAuthManager` builds the Apple Music sign-in intent and persists the returned music user token.
  - `AppleMusicPlayer` listens to `MediaPlayerController` callbacks and exposes a listener for the current Apple queue index so the UI can stay in sync with native playback.

### Apple Music Web API layer

- **Domain interface and models** (`AppleMusicApiClient.kt`)
  - Defines small domain models: `AppleMusicSong`, `AppleMusicPlaylist`, and `AppleMusicSearchResult`.
  - `AppleMusicApiClient` interface abstracts Apple Music Web API operations used by the app:
    - Catalog search for songs/playlists.
    - Fetching playlist tracks.
    - Fetching the user's library songs/playlists.
    - A combined `searchAll` convenience method used by the search UI.

- **Retrofit implementation** (`AppleMusicApiClientImpl.kt`)
  - `AppleMusicApiClientImpl` implements `AppleMusicApiClient` using Retrofit + Moshi + OkHttp.
  - `AppleMusicAuthInterceptor` injects the Apple developer token (`Authorization: Bearer ...`) and optional music user token (`Music-User-Token`) into every request using `SimpleTokenProvider`.
  - Internal Retrofit service interface (`AppleMusicService`) contains the minimal subset of endpoints used:
    - `v1/catalog/{storefront}/search` for catalog search.
    - `v1/catalog/{storefront}/playlists/{id}/tracks` for playlist tracks.
    - `v1/me/library/songs` and `v1/me/library/playlists` for library content.
  - DTOs map the subset of the Apple Music REST JSON needed by the app into the domain models via small extension functions.
  - `AppleMusicApiClientImpl.create` sets up the OkHttp client, interceptor, and Retrofit instance with a configurable base URL and storefront (default `https://api.music.apple.com/` and `us`).

### Local database and data model

All audio content (Apple Music + local files) is normalized into a small Room schema in `app/src/main/java/com/calmapps/calmmusic/data/`.

- **Database** (`CalmMusicDatabase.kt`)
  - Room `@Database` containing entities:
    - `SongEntity`
    - `AlbumEntity`
    - `ArtistEntity`
    - `PlaylistEntity`
    - `PlaylistTrackEntity`
  - Exposes DAOs: `SongDao`, `AlbumDao`, `ArtistDao`, `PlaylistDao`.
  - Uses `Room.databaseBuilder` with DB name `calmmusic.db` and `fallbackToDestructiveMigration()`.

- **Core entities and DAOs** (`SongModels.kt`)
  - `SongEntity` – single table for all songs, with:
    - `sourceType` distinguishing `APPLE_MUSIC` vs `LOCAL_FILE`.
    - `audioUri` pointing to the underlying resource (Apple catalog/library ID or local content URI).
    - Optional `albumId` and `artistId` for joining to canonical album/artist rows.
  - `AlbumEntity` and `ArtistEntity` – normalized albums and artists, keyed by IDs that often include a source prefix (e.g., `APPLE_MUSIC:...`, `LOCAL_FILE:...`).
  - `SongDao` – queries for all songs, by source type, by album, and artist/artistId; supports bulk upsert and deletion by `sourceType`.
  - `AlbumDao` and `ArtistDao` – similar upsert + delete-by-source and artist aggregation queries (`ArtistWithCounts`).

- **Playlists** (`PlaylistEntity.kt`, `PlaylistTrackEntity.kt`, `PlaylistDao.kt`)
  - `PlaylistEntity` – local playlists only (not Apple Music playlists), with IDs and metadata.
  - `PlaylistTrackEntity` – join table mapping `playlistId` to `songId` (supports both Apple and local songs) with an explicit `position` and index on both columns.
  - Foreign keys cascade deletes from playlists to `playlist_tracks` but deliberately do **not** cascade from `songs` to `playlist_tracks` so resyncing songs by `sourceType` does not silently remove playlist memberships.
  - `PlaylistDao` provides playlist listing with song counts, playlist CRUD, and a `getSongsForPlaylist` join query ordered by `position`.

- **Settings** (`CalmMusicSettingsManager.kt`)
  - Wraps `SharedPreferences` with `MutableStateFlow`/`StateFlow` to expose:
    - `includeLocalMusic` (Boolean toggle for indexing local files).
    - `localMusicFolders` (set of persisted SAF tree URIs).
  - Public methods update preferences and immediately push new values into the flows, which the Compose layer observes.

- **Local file scanning** (`LocalMusicScanner.kt`)
  - Given SAF tree URIs, recursively walks folders via `DocumentFile` and filters by file extension.
  - Uses `MediaMetadataRetriever` to read tags (title, artist, album artist, album, track number, duration).
  - Computes stable IDs and normalization:
    - `artistId` for local files is `"LOCAL_FILE:" + primaryArtist`.
    - `albumId` for local files is `"LOCAL_FILE:" + primaryArtist + ":" + albumName`.
  - Returns a list of `SongEntity` instances with `sourceType = "LOCAL_FILE"` and `audioUri` equal to the content URI string.
  - A helper `countAudioFiles` runs first to provide progress callbacks while scanning.

### Playback architecture

Playback is split between Apple Music (MusicKit) and local files (Media3), with a shared in-memory queue maintained in the Compose layer.

- **Local playback service** (`PlaybackService.kt`)
  - `MediaSessionService` that owns an `ExoPlayer` instance and a `MediaSession`.
  - Configures `AudioAttributes` for music, manages audio focus, and handles "becoming noisy" (e.g., headphones unplugged).
  - Creates a notification channel and uses `DefaultMediaNotificationProvider` for system media notifications and lockscreen/notification controls.
  - Exposes the session to clients; `MainActivity`/`CalmMusic` obtain a `MediaController` via `SessionToken` and use it as `localMediaController`.

- **Apple Music playback** (`AppleMusicCore.kt`, `CalmMusicApplication.kt`, `MainActivity.kt`)
  - `AppleMusicPlayer` encapsulates queue building via `CatalogPlaybackQueueItemProvider` and forwards current queue index changes to the UI.
  - The Compose layer treats Apple Music songs and local songs uniformly in the playback queue and maps the Apple queue index back into the global queue.
  - Repeat mode for Apple playback is applied via `MediaPlayerController.setRepeatMode` using the app's `RepeatMode` enum.

- **Shared playback queue and state** (`MainActivity.kt` – `CalmMusic` composable)
  - Maintains a unified `playbackQueue` of `SongUiModel` plus:
    - `playbackQueueIndex` (current index in that queue).
    - `originalPlaybackQueue` for restoring order when shuffle is turned off.
    - `nowPlayingSong`, `currentSongId`, playback position/duration, `repeatMode`, and `isShuffleOn`.
  - `startPlaybackFromQueue` and `startShuffledPlaybackFromQueue` bridge from high-level UI actions to the concrete players:
    - For `APPLE_MUSIC` songs, rebuilds an Apple-only queue and starts playback via `AppleMusicPlayer`.
    - For `LOCAL_FILE` songs, rebuilds a local-only queue of `MediaItem`s and controls the `MediaController`.
  - `toggleShuffleMode`, `playNextInQueue`, `playPreviousInQueue`, and `cycleRepeatMode` update both the in-memory queue state and the underlying players (Media3 or MusicKit) to stay consistent.
  - Long-running `LaunchedEffect`s keep UI state synchronized with:
    - The local `MediaController` (position, duration, queue index for local files).
    - The Apple Music player (queue index changes from native playback).

### UI and navigation

- **Entry point** (`MainActivity.kt`)
  - `MainActivity` hosts a single `CalmMusic` composable in `setContent`, wrapped with the Mudita MMD `ThemeMMD`.
  - Defines a sealed `Screen` hierarchy (Playlists, Artists, Songs, Albums, Search, Settings, and detail screens) and a bottom navigation bar (`navItems`).

- **`CalmMusic` composable**
  - Owns most app-wide state, tying together:
    - Apple Music auth status and library sync via `AppleMusicApiClient` and Room.
    - Local library sync via `LocalMusicScanner` + Room, driven by `CalmMusicSettingsManager` flows.
    - Playback queues, now-playing state, shuffle/repeat, and communication with both players.
    - In-memory projections of library content as UI models (`SongUiModel`, `AlbumUiModel`, `ArtistUiModel`, `PlaylistUiModel`).
  - Sets up a `NavHost` with destinations for playlist, artist, songs, albums, search, settings, and detail flows.
  - Routes user actions from the various screen composables back into database operations and playback functions.

- **Screen composables** (`app/src/main/java/com/calmapps/calmmusic/ui/`)
  - Individual screens (e.g., `SongsScreen`, `AlbumsScreen`, `ArtistsScreen`, `NowPlayingScreen`, `PlaylistsScreen`, `PlaylistDetailsScreen`, `PlaylistEditScreen`, `PlaylistAddSongsScreen`, `SearchScreen`, `SettingsScreen`) are primarily UI-only.
  - They receive data as UI models and callbacks for user actions; most side effects and data persistence are handled in `CalmMusic`.
  - Shared UI components such as `SongAndPlaylistItems`, `DashedDivider`, and MMD wrappers encapsulate repeated UI patterns.

## How features cross-cut multiple layers

When editing or adding features, be aware of the coupling between layers:

- **Changing how songs are identified or grouped**
  - IDs and `sourceType` conventions are defined and used in several places:
    - Apple Music Web API mapping (`AppleMusicApiClientImpl` → `SongEntity`/`AlbumEntity`/`ArtistEntity` construction in `MainActivity.kt`).
    - Local file scanning (`LocalMusicScanner`) and the way it builds `artistId`/`albumId` for `SongEntity`.
    - DAO queries that rely on `artistId`/`albumId` and `sourceType`.
  - If you change ID formats or introduce new `sourceType` values, you must update both the mapping code and the Room queries.

- **Modifying playlist behavior**
  - Playlist UI and actions live in `MainActivity.kt` and various `ui/*Playlist*Screen` composables.
  - Persistence for playlists and membership is handled by `PlaylistDao`, `PlaylistEntity`, and `PlaylistTrackEntity`.
  - Keep `position` ordering in `PlaylistTrackEntity` and the `getSongsForPlaylist` query in sync with how you display and reorder playlists.

- **Adjusting Apple Music vs local library sync**
  - Apple Music library sync logic and error handling live in `CalmMusic` (within `MainActivity.kt`), using `AppleMusicApiClient` and the DAOs.
  - Local library sync is triggered from `CalmMusic` based on `CalmMusicSettingsManager` flows and implemented via `LocalMusicScanner` and Room.
  - Deletion and upsert logic is organized by `sourceType`; changing this requires updating both the sync code and Room queries.
