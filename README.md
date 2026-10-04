# TuneGrab: Free Offline Music Player for Android

TuneGrab is a free offline music player for Android. It plays the music
already on your phone: clean library, quick picks on Home, a Now Playing
screen that shows the codec, and a mini player that stays out of the way.

No account needed. No tracking. The free version unlocks the equalizer
and song-info cleanup with a short rewarded ad; a fully ad-free FOSS
build is available for F-Droid (see below).

## Features

- Home with greeting, quick picks, and albums shelf
- Library with filter chips for songs, albums, and artists
- Now Playing screen with FLAC/MP3/Opus codec badge and queue
- Mini player with progress bar
- 10-band equalizer with presets (unlock with a short ad)
- Song info cleanup: fix artist/title tags on your local files, with backup and undo (unlock with a short ad)
- Lyrics view with retry and matching
- Silence skipping (Settings → Playback, off by default)
- Shuffle and repeat that survive app restarts
- Notification, lock-screen, Bluetooth, and Android Auto controls

## Get the app

Download the latest APK from [Releases](https://github.com/nepalanurag/tunegrab/releases).
Install it on your phone (you may need to allow "install unknown apps").

Want downloads, YouTube search, Radio, and the sleep timer? Those are in
TuneGrab Pro, sold separately: https://ko-fi.com/anuragnepal

## Build it yourself

You need JDK 17 and the Android SDK with API 36.

Free version (Play Store / GitHub flavor, with rewarded ads):

```
./gradlew :app:assemblePlaystoreRelease -x lint
```

The APK lands in `app/build/outputs/apk/playstore/release/`.

F-Droid flavor (100% free and open source, no ads or proprietary SDKs,
ad-gated features hidden, no Pro branding):

```
./gradlew :app:assembleFdroidRelease -x lint
```

The APK lands in `app/build/outputs/apk/fdroid/release/`.

This repo builds the free player only. The Pro build is not in this repo.

## Tech

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer for playback.
minSdk 26, targetSdk 36.

## License

MIT. See LICENSE.
