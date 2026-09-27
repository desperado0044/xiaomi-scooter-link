package com.scooterre.client.wear

import android.app.Application
import com.scooterre.client.protocol.DeviceRegistry

class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore what a previous sync from the phone left on disk, so the UI has something to
        // show immediately - StatusListenerService keeps these current from here on.
        WatchState.devices.value = DeviceRegistry(this).list()
        WatchState.settings.value = WatchSettingsStore.load(this)
    }
}
