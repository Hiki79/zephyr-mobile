package dev.zephyr.mobile

import android.app.Application

class ZephyrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The VPN service can be started by the system without the activity
        // ever existing, so the store has to be ready from the process up.
        ZephyrState.init(this)
    }
}
