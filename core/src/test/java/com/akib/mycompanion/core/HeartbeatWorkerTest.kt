package com.akib.mycompanion.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.akib.mycompanion.core.health.HeartbeatWorker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeartbeatWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `the heartbeat is scheduled once, however often the app starts`() {
        HeartbeatWorker.schedule(context)
        HeartbeatWorker.schedule(context)
        HeartbeatWorker.schedule(context)

        // The test WorkManager runs it at once (and it fails here, outside Hilt), so only the schedule is checked.
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("heartbeat").get()
        assertThat(work).hasSize(1)
        assertThat(work.single().tags).contains(HeartbeatWorker::class.java.name)
        assertThat(work.single().periodicityInfo?.repeatIntervalMillis).isEqualTo(15 * 60 * 1000L)
    }
}
