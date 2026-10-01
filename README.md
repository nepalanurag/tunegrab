# TuneGrab

TuneGrab is a free offline music player for Android. It plays the music
already on your phone: clean library, quick picks on Home, a Now Playing
screen that shows the codec, and a mini player that stays out of the way.

No ads. No tracking. No account needed.

## Features

- Home with greeting, quick picks, and genre shelves
- Library with filter chips for songs, albums, and artists
- Now Playing screen with FLAC/MP3 codec badge and queue
- Mini player with progress bar
- Silence skipping (Settings → Playback, off by default)
- Song info cleanup for your local files

## Get the app

The easiest way is Google Play (link coming). If you want downloads,
YouTube search, Radio, the sleep timer, and the equalizer, those are in
TuneGrab Pro, sold separately: https://ko-fi.com/anuragnepal

## Build it yourself

You need JDK 17 and the Android SDK with API 36.

```
./gradlew :app:assemblePlaystoreRelease -x lint
```

The APK lands in `app/build/outputs/apk/playstore/release/`. This builds the
free flavor only. The Pro build is not in this repo.

## Tech

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer for playback.
minSdk 26, targetSdk 36.

## License

MIT (proposed — the author may change this). See LICENSE.
