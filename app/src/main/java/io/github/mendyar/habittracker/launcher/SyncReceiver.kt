package io.github.mendyar.habittracker.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mendyar.habittracker.ui.Async

/** Re-publishes shortcuts after an app update or a language change. */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync()
        Async.io {
            try {
                LauncherSync.refresh(context)
            } finally {
                pending?.finish()
            }
        }
    }
}
