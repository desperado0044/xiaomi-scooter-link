package com.scooterre.client

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns the view model store of the whole app. The scooter connection lives in the view model; if that were tied to
 * the activity, Android destroying the activity in the background (memory pressure, "don't keep activities") would
 * clear the view model and drop the connection with it - although the foreground service still keeps the process
 * alive. Tied to the application, the connection outlives the screen and ends only with the process or on disconnect.
 */
class ScooterApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
