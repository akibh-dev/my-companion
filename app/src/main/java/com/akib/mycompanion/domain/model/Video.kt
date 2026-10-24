package com.akib.mycompanion.domain.model

/** A YouTube video, as much as the player and playlists need to know about it. */
data class Video(
    val id: String,
    val title: String,
    val author: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

/** A saved, named list of videos. [itemCount] and [totalSeconds] are summaries for the list screen. */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val itemCount: Int = 0,
    val totalSeconds: Long = 0,
)

data class PlaylistItem(
    val id: Long = 0,
    val playlistId: Long,
    val video: Video,
    val position: Int,
)

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the UI shows about the player. */
data class PlaybackState(
    val connected: Boolean = false,
    val queue: List<Video> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val error: String? = null,
) {
    val current: Video? get() = queue.getOrNull(currentIndex)
}
