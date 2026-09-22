package com.sanchit.contestpilot

import android.app.Application
import com.sanchit.contestpilot.notification.ContestSyncWorker
import com.sanchit.contestpilot.notification.NotificationChannels
import com.sanchit.contestpilot.notification.ReminderMaintenanceWorker

class ContestPilotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        NotificationChannels.ensureCreated(this)
        ContestSyncWorker.enqueuePeriodicSync(this)

        // Covers the case where the process was killed with alarms still pending and the
        // boot broadcast was missed (for example after a force stop).
        ReminderMaintenanceWorker.enqueueRestore(this)
    }
}
