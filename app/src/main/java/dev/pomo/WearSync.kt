package dev.pomo

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Keeps a paired watch in sync. The phone owns the timer: it publishes its state as a data item,
 * and the watch sends start/stop/sync messages back. Every call is best effort, so a phone
 * without Play Services or without a watch just does nothing.
 */
object WearSync {
    const val STATE = "/pomo/state"
    const val START = "/pomo/start"
    const val STOP = "/pomo/stop"
    const val BREAK = "/pomo/break"
    const val SYNC = "/pomo/sync"

    fun publish(c: Context) {
        runCatching {
            val phase = Timer.phase(c)
            val total = when (phase) {
                Phase.FOCUS -> Timer.currentFocusMs(c)
                Phase.BREAK -> Timer.currentBreakMinutes(c) * 60_000L
                Phase.DONE, Phase.IDLE -> 0L
            }
            val request = PutDataMapRequest.create(STATE).apply {
                dataMap.putString("phase", phase.name)
                dataMap.putLong("endAt", Timer.endAt(c))
                dataMap.putLong("totalMs", total)
                dataMap.putString("category", if (phase == Phase.IDLE) Timer.category(c) else Timer.currentCategory(c))
                dataMap.putInt("minutes", Timer.minutes(c))
                dataMap.putInt("breakMinutes", Timer.breakMinutes(c))
                dataMap.putLong("sentAt", System.currentTimeMillis()) // makes every publish a change
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(c.applicationContext).putDataItem(request)
        }
    }
}

/** Commands from the watch. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Timer.settle(this)
        when (event.path) {
            WearSync.START -> if (Timer.phase(this) == Phase.IDLE) Timer.start(this)
            WearSync.STOP -> Timer.stop(this)
            WearSync.BREAK -> Timer.startBreak(this)
            WearSync.SYNC -> WearSync.publish(this)
        }
    }
}
