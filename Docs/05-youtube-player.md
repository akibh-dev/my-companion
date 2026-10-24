# YouTube background player

Play YouTube audio like a music app: screen off, other apps open, controls in the notification and on the lock screen.

## Using it

| Where | What you do | What happens |
|-------|-------------|--------------|
| YouTube app | **Share → My Companion** ("Play in My Companion") | A small sheet slides up over YouTube |
| Share sheet | **Play now** | Starts playing and opens the Player tab |
| | **Add to queue** | Added after the current queue; nothing interrupts |
| | **Add to playlist** | Pick a playlist, or type a name → **Create & add** |
| Browser / chat | Long-press a YouTube link → **Open with** My Companion | Same sheet |
| Player tab | Paste a link (📋 button pastes and opens it) | Same three choices, inline |
| Player tab | Seek bar, ⏮ ⏯ ⏭, shuffle, repeat (off → all → one) | Same as the notification controls |
| Up next | Tap a song to jump, ✕ to remove, **Save as playlist** | Saved playlists are permanent (Room) |
| Playlists | **Play all**, **Shuffle**, tap an item to start there, ⋮ Rename / Delete | |

Playback keeps going when you leave the app or lock the phone, pauses when headphones are unplugged and for phone calls, and resumes from the notification. Swiping the app away keeps music playing; if it was paused, the player shuts down.

## How it works

```
Share / Open with / pasted link
        │  YouTubeLinks.videoId()  (youtu.be, watch?v=, shorts, live, embed, music…)
        ▼
LoadVideoUseCase ──▶ NewPipeYouTube.fetch()  (title, channel, length, thumbnail)
        │
        ├─ Play now / Add to queue ─▶ Playback (MediaController) ─▶ PlaybackService
        └─ Add to playlist ─────────▶ RoomPlaylistRepository (playlists, playlist_items)

PlaybackService (Media3 MediaSessionService + ExoPlayer)
  queue items are "ytaudio:<videoId>"
        │ only when ExoPlayer is about to load an item:
        ▼
  ResolvingDataSource ─▶ YouTubeSpecResolver ─▶ CachingStreamResolver ─▶ NewPipeYouTube.audioStream()
                                               (cached until ~10 min before the URL expires)
```

- **Why NewPipeExtractor:** YouTube has no official API for audio streams. The extractor (used by the NewPipe app) reads the same data the YouTube website does. No API key, no Google account.
- **Stream choice** (`AudioStreamPicker`): a plain HTTP audio file, original-language track, highest bitrate, m4a on ties.
- **Headers** (`StreamHeaders`): googlevideo URLs are bound to the client that requested them (`c=ANDROID`, `c=IOS`, `c=WEB`…); requests must use a matching User-Agent or YouTube answers 403.
- **Expired URLs:** URLs last a few hours. On a playback error the cached URL is dropped and the item retried once (`RetryExpiredStream`).
- **Media3** handles the foreground service, media notification, lock screen, Bluetooth/headset buttons and audio focus. The session callback restores the `ytaudio:` URIs, because Media3 strips URIs coming from controllers on purpose.

## Storage

Room database version 2 adds `playlists` and `playlist_items` (cascade delete). `MIGRATION_1_2` upgrades existing installs without touching contacts or reminders; `PlaylistStorageTest` builds a real version-1 database and upgrades it.

## Limits and caveats

- **Personal use.** Playing YouTube audio without YouTube's app goes against YouTube's terms. That's why this app isn't for the Play Store (see plan/01-project-overview.md).
- **YouTube changes break extraction now and then.** Symptom: "Couldn't load the video" or playback errors for every video. Fix: bump `newpipeExtractor` in `gradle/libs.versions.toml` to the latest release (https://github.com/TeamNewPipe/NewPipeExtractor/releases).
- The queue lives in the player; it's lost when the player service stops. Use **Save as playlist** to keep it.
- Age-restricted and members-only videos usually can't be played.

## Concepts used

MediaSessionService · MediaSession / MediaController · ExoPlayer and data sources · foreground service type `mediaPlayback` · audio focus and becoming-noisy · share targets (`ACTION_SEND`) and `ACTION_VIEW` filters · Room migrations and foreign keys · OkHttp · ListenableFuture.

## Tests

`YouTubeLinksTest`, `AudioStreamPickerTest`, `VideoUseCasesTest`, `YouTubeDataTest` (cache, headers, HTTP client against MockWebServer), `PlaylistStorageTest` (Room + migration), `PlayerPlumbingTest` (MediaItem mapping, data-spec resolution, retry, state mirroring), `VideoActionsViewModelTest`, `PlayerViewModelTest`, `PlayerScreenTest` (screen, share sheet), `MainActivityTest`.
