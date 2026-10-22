package com.hermes.companion

import android.app.Application
import com.hermes.companion.alarm.NotificationHelper
import com.hermes.companion.data.Store
import com.hermes.companion.sync.SyncScheduler

class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.get(this)
        NotificationHelper.createChannels(this)
        SyncScheduler.ensurePeriodic(this)
    }
}
