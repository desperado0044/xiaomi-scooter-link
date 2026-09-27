package com.scooterre.client.wear

import android.app.Application
import com.scooterre.client.protocol.DeviceRegistry
import com.scooterre.client.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Restore what a previous sync from the phone left on disk, so the UI has something to
        // show immediately - StatusListenerService keeps these current from here on.
        WatchState.devices.value = DeviceRegistry(this).list()
        WatchState.settings.value = WatchSettingsStore.load(this)
        UpdateInstaller.cleanup(this)
        // Cheap, JSON-only, throttled to once a day - see WearUpdate's doc comment.
        scope.launch { WearUpdate.autoCheckIfDue(this@WearApplication) }
    }
}
