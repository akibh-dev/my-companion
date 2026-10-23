package com.akib.mycompanion.domain.port

import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.Video
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow

/** Looks up a video's title, channel, length and thumbnail. */
interface VideoInfoSource {
    @Throws(IOException::class)
    suspend fun fetch(videoId: String): Video
}

/** The background audio player, as the screens see it. */
interface Playback {
    val state: StateFlow<PlaybackState>

    /** Connects to the player service so [state] is live (playing starts it anyway). */
    fun connect()

    /** Replaces the queue and starts playing. */
    fun playNow(videos: List<Video>, startIndex: Int = 0, shuffle: Boolean = false)

    /** Adds to the end of the queue; starts nothing if idle (the item is ready when play is pressed). */
    fun addToQueue(video: Video)

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun skipTo(index: Int)

    fun removeAt(index: Int)

    fun cycleRepeat()

    fun toggleShuffle()
}
