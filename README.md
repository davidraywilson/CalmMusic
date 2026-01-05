<img align="left" src="logo.svg" width="100" height="100" alt="CalmMusic Logo">
<a href="https://www.buymeacoffee.com/davidraywilson" target="_blank">
  <img align="right" src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174">
</a>

<br clear="all" />

# CalmMusic

CalmMusic is a minimal, mindful music player built for de‑googled and E‑ink Android devices. It unifies your Apple Music catalog and your local audio files into a calm, distraction‑free listening experience powered by the Mudita Mindful Design library.

"Let's make technology useful again."

## Screenshots

<table>
<tr>
  <td><img src="CalmMusic Screens/screen_1.png" alt="CalmMusic screenshot 1"></td>
  <td><img src="CalmMusic Screens/screen_2.png" alt="CalmMusic screenshot 2"></td>
  <td><img src="CalmMusic Screens/screen_3.png" alt="CalmMusic screenshot 3"></td>
  <td><img src="CalmMusic Screens/screen_4.png" alt="CalmMusic screenshot 4"></td>
</tr>
<tr>
  <td><img src="CalmMusic Screens/screen_5.png" alt="CalmMusic screenshot 5"></td>
  <td><img src="CalmMusic Screens/screen_6.png" alt="CalmMusic screenshot 6"></td>
  <td><img src="CalmMusic Screens/screen_7.png" alt="CalmMusic screenshot 7"></td>
  <td><img src="CalmMusic Screens/screen_8.png" alt="CalmMusic screenshot 8"></td>
</tr>
<tr>
  <td><img src="CalmMusic Screens/screen_9.png" alt="CalmMusic screenshot 9"></td>
  <td><img src="CalmMusic Screens/screen_10.png" alt="CalmMusic screenshot 10"></td>
  <td><img src="CalmMusic Screens/screen_11.png" alt="CalmMusic screenshot 11"></td>
  <td><img src="CalmMusic Screens/screen_12.png" alt="CalmMusic screenshot 12"></td>
</tr>
<tr>
  <td><img src="CalmMusic Screens/screen_13.png" alt="CalmMusic screenshot 13"></td>
  <td><img src="CalmMusic Screens/screen_14.png" alt="CalmMusic screenshot 14"></td>
  <td><img src="CalmMusic Screens/screen_15.png" alt="CalmMusic screenshot 15"></td>
  <td><img src="CalmMusic Screens/screen_16.png" alt="CalmMusic screenshot 16"></td>
</tr>
<tr>
  <td><img src="CalmMusic Screens/screen_17.png" alt="CalmMusic screenshot 17"></td>
  <td><img src="CalmMusic Screens/screen_18.png" alt="CalmMusic screenshot 18"></td>
</tr>
</table>

## What is CalmMusic?

CalmMusic is a simple music player for people who want less distraction and more music. There are no accounts to create, no tracking, and no clutter—just your songs and albums in a calm interface.

It brings together:

- Your Apple Music catalog and playlists (if you choose to sign in)
- Your local audio files from folders you pick on your device
- One place to browse, search, and play your music

## Core principles (Mudita Mindful Design)

- **Simplicity:** Each screen has one clear job and stays lightweight
- **Privacy:** Your listening stays on your device; nothing is sold or tracked
- **Intention:** Designed to support relaxed, intentional listening
- **Focus:** A clean interface that doesn’t compete for your attention
- **Ownership:** You choose what music is included and how it’s organized

## Highlights

- **One library for everything**
  - See songs, albums, artists, and playlists together
  - Apple Music tracks and local files appear side by side

- **Apple Music, when you want it**
  - Sign in with Apple Music to search the catalog
  - Browse your Apple Music library songs and playlists
  - Play full tracks using Apple’s official player

- **Local music made easy**
  - Pick the folders on your device that hold your music
  - CalmMusic scans them for audio files and reads basic song info
  - Your local songs are grouped by artist and album

- **Playlists & queue**
  - Create your own playlists inside CalmMusic
  - Mix Apple Music tracks and local files in the same queue
  - Use shuffle and repeat to listen the way you like

- **Mindful playback**
  - A quiet Now Playing screen with large text and simple controls
  - Designed to work well on E‑ink and low‑distraction devices

## Why it matters

- **Respect for your privacy**
  - No ads, no tracking, no hidden analytics
  - Your settings and library details stay on your device
  - Apple Music features talk only to Apple’s own servers

- **Less noise, more music**
  - No “engagement” feeds or attention‑grabbing tricks
  - No badges, streaks, or pop‑ups pushing you to listen more
  - Just enough interface to quickly get to what you want to hear

- **Works even when you’re offline**
  - Your local music is always available
  - Apple Music playback uses Apple’s official tools for stability

## Tech stack (for the curious)

- **Language:** Kotlin (Android, JVM target 1.8)
- **UI:** Jetpack Compose + Material 3 + Mudita Mindful Design (MMD)
- **Navigation:** Jetpack Navigation Compose
- **Architecture:** Compose‑first UI with state hoisted into `MainActivity` and exposed via UI models and flows
- **Persistence:** Room (songs, albums, artists, playlists) + SharedPreferences for lightweight settings
- **Local files:** Storage Access Framework (`DocumentFile`) for folder access and recursive scanning
- **Playback:**
  - Apple MusicKit for Android AARs (`appleMusicSDK` native library)
  - AndroidX Media3 ExoPlayer for local files in a foreground playback service
- **Networking:** Retrofit + Moshi + OkHttp for Apple Music Web API
- **Android:** Min SDK 28, Target/Compile SDK 35
- **Build system:** Gradle Kotlin DSL, single `:app` module with Compose enabled

## Privacy & data

- You can use local music without creating any account
- There is no tracking, advertising, or data selling
- The app does not send your listening data to the developer
- When you turn on Apple Music features:
  - CalmMusic uses your Apple Music tokens only to talk to Apple’s services
  - Those tokens are stored on your device and used only for Apple Music

### Local music behavior

- You choose which folders CalmMusic is allowed to scan
- The app looks only in those folders for supported audio files
- Basic song info (like title, artist, album) is saved in a local database
- You can clear and rescan your local library whenever you like

## Roadmap

Some ideas being explored (these may change over time):

- Finish Apple Music Support (currently not able to set up Apple Music)
- Deeper Apple Music library integration (for example: more ways to sort and filter)

## For developers

Want to build from source?

- **Requirements:**
  - Android Studio (Giraffe / Hedgehog or newer)
  - JDK 17 (Gradle is configured to use Java 17)
  - Android SDK Platform 35+
  - Device or emulator running Android 9 (API 28) or newer

- **Quick start:**
  1. Clone this repository
  2. Open the root folder in Android Studio
  3. Let Gradle sync and download dependencies
  4. Configure an Apple Music developer token (see `SimpleTokenProvider` and Apple’s MusicKit documentation) if you want Apple Music features
  5. Select the `app` run configuration and press **Run**

- **Useful Gradle commands (from repo root):**
  - Assemble debug APK: `./gradlew :app:assembleDebug`
  - Install debug build on a connected device: `./gradlew :app:installDebug`
  - Run unit tests: `./gradlew :app:testDebugUnitTest`
  - Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest`
  - Run Android Lint: `./gradlew :app:lintDebug`

Apple Music integration will not function correctly unless you provide valid Apple Music credentials and follow Apple’s developer policies.

## Contributing

Contributions are welcome—as long as they respect the core principles of simplicity, privacy, and focus.

If you open a pull request:

- Keep UI changes mindful of E‑ink devices (contrast, motion, density)
- Avoid adding tracking, ads, or dark patterns
- Test on at least one real or virtual device on a supported Android version

## License

License details are not finalized yet. Until a license file is added, please treat this repository as **all rights reserved** by the author and ask before redistributing or publishing modified builds.
