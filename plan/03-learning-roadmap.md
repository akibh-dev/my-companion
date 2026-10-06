# Learning Roadmap

## Phase 0: Foundations (Week 1)
- [ ] Kotlin refresher: coroutines, flow, sealed classes, extension functions
- [ ] Android basics: Activity/Fragment lifecycle, Context, Intent, Manifest
- [ ] Gradle: project vs module, dependencies, version catalog, build variants
- [ ] Git: conventional commits, branching, rebasing

**Resources**:
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course)
- [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html)

## Phase 1: Project Setup & First Compose Screen (Week 1-2)
- [ ] Create Android module with Compose
- [ ] Hilt setup + Application class
- [ ] Theme (Material 3), Typography, Colors
- [ ] Navigation (Compose Navigation)
- [ ] First screen: Settings / Home
- [ ] ktlint + detekt + CI

**Learning Output**: `Docs/01-project-setup.md`, `Docs/02-compose-basics.md`

## Phase 2: Custom Notifications (Week 2-3)
- [ ] NotificationChannel creation (boot-complete receiver or app start)
- [ ] NotificationBuilder with actions (PendingIntent → BroadcastReceiver)
- [ ] Schedule notifications: WorkManager (periodic) vs AlarmManager (exact)
- [ ] Notification permission (Android 13+ POST_NOTIFICATIONS)
- [ ] Settings UI to configure notifications

**Key Concepts**: NotificationManager, PendingIntent flags (mutable/immutable), WorkManager constraints, BroadcastReceiver lifecycle

**Learning Output**: `Docs/03-notifications.md`, `Docs/04-workmanager.md`

## Phase 3: Foreground Service & Media3 Basics (Week 3-4)
- [ ] Foreground Service setup (mediaPlayback type)
- [ ] MediaSession + MediaController
- [ ] Media3 ExoPlayer basic playback (local audio file first)
- [ ] MediaStyle notification with playback controls
- [ ] Lock screen / Bluetooth controls
- [ ] Handle audio focus, phone calls, headphone unplug

**Key Concepts**: Service lifecycle, MediaSessionCompat vs Media3, MediaController, PlaybackState, MediaMetadata

**Learning Output**: `Docs/05-foreground-service.md`, `Docs/06-media3-exoplayer.md`

## Phase 4: YouTube Background Playback (Week 5-6)
- [ ] Research: NewPipeExtractor / Piped API / yt-dlp
- [ ] Extract audio-only stream URL from YouTube video ID/URL
- [ ] Feed stream to ExoPlayer (HLS/DASH support)
- [ ] Handle stream expiration, errors, buffering
- [ ] Search / URL input UI
- [ ] Queue / playlist management

**Key Concepts**: MediaItem, MediaSource, ProgressiveMediaSource vs HlsMediaSource, DRM (if needed), caching

**Learning Output**: `Docs/07-youtube-extraction.md`, `Docs/08-streaming-media.md`

## Phase 5: Advanced Features (Week 7+)
- [ ] NotificationListenerService: read/dismiss other notifications
- [ ] Quick Settings Tile: toggle background playback
- [ ] Picture-in-Picture mode (if video)
- [ ] Widget (Glance) for playback control
- [ ] Backup/restore settings (DataStore → file)

**Learning Output**: `Docs/09-notification-listener.md`, `Docs/10-tiles-widgets.md`

## Ongoing: Documentation Habit
After each phase, write a **learning note** in `Docs/`:
```
## What I Built
## Key Classes / APIs Used
## Problems Faced & Solutions
## Official Docs Links
## Articles / Videos That Helped
## Code Snippets to Remember
## Questions for Later
```

## Recommended Learning Resources

### Official
- [Android Developers - Compose](https://developer.android.com/jetpack/compose)
- [Android Developers - Media3](https://developer.android.com/media/media3)
- [Android Developers - Notifications](https://developer.android.com/develop/ui/views/notifications)
- [Android Developers - Services](https://developer.android.com/guide/components/services)
- [Android Developers - WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)

### Video Courses
- [Philipp Lackner - Android Clean Architecture](https://www.youtube.com/@PhilippLackner)
- [Rebecca Franks - Compose](https://www.youtube.com/@RebeccaFranks)
- [Google I/O / Android Dev Summit talks on Media3, Foreground Services]

### Articles / Blogs
- [ProAndroidDev - Medium](https://proandroiddev.com/)
- [Android Developers Blog](https://android-developers.googleblog.com/)
- [Fragmented Podcast](https://fragmentedpodcast.com/) - deep dives

### Reference Projects
- [Google Samples - Compose](https://github.com/android/compose-samples)
- [Google Samples - Media3](https://github.com/android/media-samples)
- [NewPipe](https://github.com/TeamNewPipe/NewPipe) - for extractor reference