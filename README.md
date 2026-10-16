# Music Player

An offline-first Android music player. It plays the music already on your phone, with no account and no streaming.
The internet is used for one thing only: fetching lyrics (from [LRCLIB](https://lrclib.net)), which are then saved on the phone.

## Features
- **Home:** greeting, filter chips, curated mixes (shuffle all, recently added, most played, liked), your playlists
- **My Music:** All / Liked / Albums / Artists / Genres, sort by title, artist, album, recently added or most played, A-Z fast scroll
- **Playlists:** create, rename, delete, add songs, save the current queue, and save any album / artist / genre / search result as a playlist
- **Now Playing:** round cover on an art-tinted backdrop, three-line lyrics teaser (tap for full Apple Music style synced lyrics), shuffle, repeat, queue, sleep timer, favorites
- **Queue:** shows the real shuffled play order, drag to reorder (shuffle off), tap to jump, remove
- **Quality:** Now Playing shows the format and quality of the current file, for example `FLAC • 16-bit • 44.1 kHz` or `MP3 • 320 kbps • 44.1 kHz`
- **Themes:** Dark, Pure black or Light, plus nine accent colors or an accent that follows the album art
- **Settings:** headphone and audio-focus behavior, playback speed, skip silence, volume normalization (ReplayGain), equalizer and bass boost, lyrics size/blur/keep-awake, 120 Hz toggle, backup and restore, reset
- **Resume:** reopens with your queue and position where you left off
- Background playback, notification and lock-screen controls, gapless playback, 120 Hz where supported

## Build the APK with GitHub
1. Upload this whole project to the repository (keep the `.github` folder and `app/release.jks`).
2. Open the **Actions** tab. The build runs on every push (or press Run workflow).
3. Download the **music-player-apk** artifact and install `app-release.apk`.

Builds are signed with the key in `app/release.jks`, so each new build installs over the previous one and keeps your data.
Keep the repository **private**: anyone with that file could sign updates as you.

## Notes
- 120 Hz: set Settings > Display > Refresh rate to High on your phone.
- Background playback on OriginOS: set the app's battery usage to **Unrestricted**.

## Credits
Icons are [Phosphor Icons](https://phosphoricons.com) (MIT). Font is Plus Jakarta Sans (SIL OFL).
