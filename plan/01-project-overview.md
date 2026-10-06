# Project Overview: my-companion

## Vision
A personal Android app that gives me control over features typically locked behind paid subscriptions (YouTube Premium, etc.), built as a learning vehicle for Android development.

## Core Features (MVP Priority)

| Priority | Feature | Description | Android Concepts |
|----------|---------|-------------|------------------|
| P0 | **Custom Notifications** | Create/manage notification channels, scheduled notifications, action buttons | NotificationManager, NotificationChannel, PendingIntent, AlarmManager/WorkManager |
| P1 | **YouTube Background Playback** | Play YouTube audio with screen off / in background | Foreground Service, MediaSession, Media3 ExoPlayer, Picture-in-Picture |
| P2 | **Notification Listener** | Read/dismiss/respond to notifications from other apps | NotificationListenerService, Permission handling |
| P3 | **Custom Quick Settings Tile** | Toggle features from quick settings panel | TileService |
| P4 | **Overlay/Popup Widget** | Floating controls for playback | SYSTEM_ALERT_WINDOW, WindowManager |

## Non-Functional Requirements
- **Privacy-first**: No analytics, no network calls unless user-initiated
- **Battery-friendly**: Proper foreground service lifecycle, WorkManager for periodic tasks
- **Maintainable**: Clean architecture, well-tested, documented for learning
- **Personal use only**: Not for Play Store distribution (avoids policy issues with YouTube)

## Technical Decisions to Document (in Docs/)
1. Why Kotlin + Compose vs XML
2. Hilt vs Koin for DI
3. Media3 vs legacy MediaSession
4. WorkManager vs AlarmManager vs Foreground Service
5. Room vs DataStore vs SharedPreferences

## Success Criteria for MVP
- [ ] App installs and runs on physical device (API 24+)
- [ ] Can create a custom notification with actions
- [ ] Can play YouTube audio in background (screen off)
- [ ] Basic settings screen to toggle features
- [ ] All code documented with learning notes in Docs/

## Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| YouTube API changes / ToS | Use NewPipeExtractor or similar; keep personal use only |
| Foreground service restrictions (Android 14+) | Use proper Media3 session, request FOREGROUND_SERVICE_MEDIA_PLAYBACK |
| Notification listener permission | Guide user to enable in settings; handle gracefully if denied |
| Battery optimization killing service | Request exemption, use proper foreground service type |
| Learning curve overwhelming | Incremental commits, document everything in Docs/, one feature at a time |

## Timeline (Rough)
- Week 1-2: Project setup, Compose basics, first notification
- Week 3-4: Foreground service + Media3 basics
- Week 5-6: YouTube extraction + background playback
- Week 7-8: Notification listener + quick settings tile
- Ongoing: Refactor, test, document in Docs/