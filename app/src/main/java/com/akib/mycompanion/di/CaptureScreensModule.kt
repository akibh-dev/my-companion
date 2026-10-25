package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.capture.ui.CaptureDetailRoute
import com.akib.mycompanion.capture.ui.CaptureSpaceRoute
import com.akib.mycompanion.presentation.home.HomeLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Quick capture's screens (Phase 4: FR-4.3): the capture space and one capture. */
@Module
@InstallIn(SingletonComponent::class)
object CaptureScreensModule {

    @Provides @IntoSet
    fun captureSpace() = Screen("capture", "Capture space") { s ->
        CaptureSpaceRoute(onOpenCapture = { id -> s.navigate("capture/$id") }, modifier = s.modifier)
    }

    @Provides @IntoSet
    fun captureDetail() = Screen("capture/{id}", "Capture") { s ->
        CaptureDetailRoute(onDone = s.back, modifier = s.modifier)
    }

    @Provides @IntoSet
    fun captureLink() = HomeLink("Capture space", "Notes, voice memos, photos and screenshots you captured", "capture")
}
