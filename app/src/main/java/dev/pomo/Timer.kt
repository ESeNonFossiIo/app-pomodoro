package dev.pomo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

enum class Phase { IDLE, FOCUS, BREAK }

/** Timer state lives in SharedPreferences so the UI, alarm and listener all agree. */
object Timer {
    const val FOCUS_MS = 25 * 60_000L
    const val BREAK_MS = 5 * 60_000L

    private fun prefs(c: Context) = c.getSharedPreferences("pomo", Context.MODE_PRIVATE)

    fun phase(c: Context) = Phase.valueOf(prefs(c).getString("phase", "IDLE")!!)
    fun endAt(c: Context) = prefs(c).getLong("endAt", 0)

    fun isBlocking(c: Context) = phase(c) == Phase.FOCUS && System.currentTimeMillis() < endAt(c)

    fun excluded(c: Context): Set<String> = prefs(c).getStringSet("excluded", emptySet())!!
    fun setExcluded(c: Context, pkgs: Set<String>) =
        prefs(c).edit().putStringSet("excluded", pkgs).apply()

    @Synchronized
    fun start(c: Context) = set(c, Phase.FOCUS, System.currentTimeMillis() + FOCUS_MS)

    /** Stopping a focus session early does not count it. */
    @Synchronized
    fun stop(c: Context) = set(c, Phase.IDLE, 0)

    /**
     * Advances finished phases (focus -> break -> idle), recording completed focus sessions.
     * Returns the first phase that ended, or null if nothing did.
     */
    @Synchronized
    fun settle(c: Context): Phase? {
        var phase = phase(c)
        var end = endAt(c)
        val now = System.currentTimeMillis()
        var ended: Phase? = null
        while (phase != Phase.IDLE && now >= end) {
            if (ended == null) ended = phase
            if (phase == Phase.FOCUS) {
                History.add(c, end)
                phase = Phase.BREAK
                end += BREAK_MS
            } else {
                phase = Phase.IDLE
                end = 0
            }
        }
        if (ended != null) set(c, phase, end)
        return ended
    }

    private fun set(c: Context, phase: Phase, end: Long) {
        prefs(c).edit().putString("phase", phase.name).putLong("endAt", end).apply()
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (end > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi) else am.cancel(pi)
    }
}
