<img align="left" src="logo.svg" width="100" height="100" alt="CalmMusic Logo">
<a href="https://www.buymeacoffee.com/davidraywilson" target="_blank">
  <img align="right" src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174">
</a>

<br clear="all" />

# CalmMusic

A calm, E‑ink‑friendly music player that puts your attention and privacy first.

CalmMusic brings together your **local files**, **YouTube Music** search/streaming, and **optional Apple Music** support into one quiet, distraction‑free place to listen.

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

## What makes CalmMusic different?

CalmMusic is for people who want **less noise and more music**—especially on de‑googled phones and E‑ink devices.

- **Built for E‑ink and low‑distraction screens**  
  Large text, high contrast, minimal animations, and layouts that still feel good at slow refresh rates.
- **Mindful by design**  
  No feeds, badges, or engagement tricks—just simple screens that do one job well.
- **Privacy‑respecting**  
  No tracking, no analytics SDKs, no ads. Your listening stays on your device.
- **You stay in control**  
  You choose which folders to scan, which streaming source to use, and what ends up in your library.

## What you can do with CalmMusic

### 1. Listen to your local music

- Choose exactly which folders on your device CalmMusic is allowed to scan.
- The app indexes supported audio files and builds a clean library of **songs, albums, artists, and playlists**.
- Local songs work fully **offline**—perfect for slow or no‑signal moments.

### 2. Stream from YouTube Music (no account required)

When you pick **YouTube Music** as your streaming source:

- Search YouTube Music for songs and albums from inside CalmMusic.
- Add YouTube tracks to the same queue as your local music.
- Optionally let CalmMusic **fill in missing tracks on local albums** using YouTube search results.
- See and manage active and recent **YouTube downloads** in a dedicated Downloads screen.

> Please respect artists’ rights and your local laws when streaming or downloading from YouTube.

### 3. Use Apple Music (optional)

If Apple Music is available in your build and you connect your account:

- Browse and play from your **Apple Music library**.
- Mix Apple Music songs into the same unified queue as your other sources.
- Playback uses Apple’s official tools for stability and audio quality.

Apple Music support is **optional** and may require extra setup when building from source.

### 4. One calm queue for everything

Regardless of where your music comes from:

- Build a single **now‑playing queue** that can mix local files, YouTube tracks, and (optionally) Apple Music.
- Use **shuffle** and **repeat** without losing your place.
- Move naturally between songs with simple previous/next controls.

### 5. Mindful playback & overlays

- A quiet **Now Playing** screen with big typography and simple controls—easy on the eyes and on E‑ink.
- Minimal chrome so the artwork, title, and basic actions are all you see.
- Optional system overlay support so you can see what’s playing without reopening the full app (implementation may vary by device).

## Getting started

1. **Install CalmMusic** on an Android device (Android 9 / API 28 or newer is recommended).  
2. **Open the app** – you’ll start with an empty library.
3. **Add local music**
   - Go to **Settings → Local music**.
   - Pick the folders that contain your audio files.
   - CalmMusic will scan and build your library of songs, albums, and artists.
4. **Pick a streaming source (optional)**
   - Go to **Settings → Streaming source**.
   - Choose **YouTube Music** to enable YouTube search and streaming.
   - If available, enable **Apple Music** and connect your account to use your Apple library.

You can change these choices at any time.

## Privacy & data

CalmMusic is designed to stay out of your business:

- **No accounts required** for local music or YouTube search.
- **No ads, no analytics, no tracking SDKs.**
- Your settings and local library live **only on your device**.
- When you use online features:
  - YouTube‑related features talk only to YouTube/YouTube Music (and supporting streaming APIs) as needed to search and stream audio.
  - Apple Music features, when enabled, talk only to Apple’s services using tokens stored on your device.

You can always remove folders, clear local data, or turn streaming features off if you prefer a fully offline experience.

## For developers

If you want to hack on CalmMusic or build your own APK:

- **Requirements**
  - Android Studio (Giraffe / Hedgehog or newer)
  - JDK 17
  - Android SDK Platform 35+
  - Device or emulator running Android 9 (API 28) or newer

- **Quick start**
  1. Clone this repository.
  2. Open the root folder in Android Studio.
  3. Let Gradle sync and download dependencies.
  4. (Optional) Configure Apple Music developer credentials (see `SimpleTokenProvider` and Apple’s MusicKit documentation) if you want Apple Music to work in your build.
  5. Select the `app` configuration and press **Run**.

- **Useful Gradle commands (from repo root)**
  - Assemble debug APK: `./gradlew :app:assembleDebug`
  - Install debug build on a connected device: `./gradlew :app:installDebug`
  - Run unit tests: `./gradlew :app:testDebugUnitTest`
  - Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest`
  - Run Android Lint: `./gradlew :app:lintDebug`

## Contributing

Contributions are welcome—as long as they respect the core principles of **simplicity**, **privacy**, and **focus**.

If you open a pull request:

- Keep UI changes friendly to E‑ink devices (contrast, motion, density).
- Avoid adding tracking, ads, or dark patterns.
- Test on at least one real or virtual device on a supported Android version.

## License

GPL‑3.0 (see `LICENSE`).
