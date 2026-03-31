package ch.threema.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

inline fun broadcastReceiver(crossinline handleBroadcast: (Context, Intent) -> Unit) = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handleBroadcast(context, intent)
    }
}
