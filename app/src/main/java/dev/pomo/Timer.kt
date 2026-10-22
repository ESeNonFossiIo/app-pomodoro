package dev.pomo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

enum class Phase { IDLE, FOCUS, BREAK }

/** Timer state lives in SharedPreferences so the UI, alarm and listener all agree. */
object Timer {
    const val MIN_BREAK = 5
    const val MAX_BREAK = 30
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 120
    val DEFAULT_CATEGORIES = listOf("Work", "Study", "Reading", "Other")

    private fun prefs(c: Context) = c.getSharedPreferences("pomo", Context.MODE_PRIVATE)

    fun phase(c: Context) = Phase.valueOf(prefs(c).getString("phase", "IDLE")!!)
    fun endAt(c: Context) = prefs(c).getLong("endAt", 0)

    /** 0..1 through the running phase (0 when idle). */
    fun progress(c: Context, now: Long): Float {
        val total = when (phase(c)) {
            Phase.FOCUS -> prefs(c).getLong("curMs", 25 * 60_000L)
            Phase.BREAK -> currentBreakMinutes(c) * 60_000L
            Phase.IDLE -> return 0f
        }
        return (1f - (endAt(c) - now).toFloat() / total).coerceIn(0f, 1f)
    }

    fun isBlocking(c: Context) = phase(c) == Phase.FOCUS && System.currentTimeMillis() < endAt(c)

    fun excluded(c: Context): Set<String> = prefs(c).getStringSet("excluded", emptySet())!!
    fun setExcluded(c: Context, pkgs: Set<String>) =
        prefs(c).edit().putStringSet("excluded", pkgs).apply()

    /** Selected focus length, used by the next session. */
    fun minutes(c: Context) = prefs(c).getInt("minutes", 25)
    fun setMinutes(c: Context, m: Int) =
        prefs(c).edit().putInt("minutes", m.coerceIn(MIN_MINUTES, MAX_MINUTES)).apply()

    /** Whether a focus session takes over the screen. On by default. */
    fun fullscreenFocus(c: Context) = prefs(c).getBoolean("fullscreenFocus", true)
    fun setFullscreenFocus(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean("fullscreenFocus", on).apply()

    fun themeMode(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs(c).getString("theme", null)!!) }.getOrDefault(ThemeMode.COLOR)
    fun setThemeMode(c: Context, m: ThemeMode) = prefs(c).edit().putString("theme", m.name).apply()

    /** The user ticked "Don't show again" on the app-pinning warning. */
    fun hidePinWarning(c: Context) = prefs(c).getBoolean("hidePinWarning", false)
    fun setHidePinWarning(c: Context, hide: Boolean) =
        prefs(c).edit().putBoolean("hidePinWarning", hide).apply()

    /** Selected break length, used after the next focus session. */
    fun breakMinutes(c: Context) = prefs(c).getInt("breakMinutes", 5)
    fun setBreakMinutes(c: Context, m: Int) =
        prefs(c).edit().putInt("breakMinutes", m.coerceIn(MIN_BREAK, MAX_BREAK)).apply()

    /** Length of the break that follows the session in progress. */
    fun currentBreakMinutes(c: Context) = prefs(c).getInt("curBreakMinutes", 5)

    /** Selected category, used by the next session. */
    fun category(c: Context) = prefs(c).getString("category", DEFAULT_CATEGORIES[0])!!
    fun setCategory(c: Context, name: String) = prefs(c).edit().putString("category", name).apply()

    fun categories(c: Context): List<String> =
        prefs(c).getString("categories", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: DEFAULT_CATEGORIES

    fun addCategory(c: Context, name: String) {
        val n = name.trim()
        if (n.isEmpty() || categories(c).any { it.equals(n, ignoreCase = true) }) return
        prefs(c).edit().putString("categories", (categories(c) + n).joinToString("\n")).apply()
        setCategory(c, n)
    }

    /** Removes a category (at least one is always kept). Past sessions keep their label. */
    fun removeCategory(c: Context, name: String) {
        val left = categories(c) - name
        if (left.isEmpty()) return
        prefs(c).edit().putString("categories", left.joinToString("\n")).apply()
        if (category(c) == name) setCategory(c, left[0])
    }

    /** Category and length of the session in progress (what gets recorded on completion). */
    fun currentCategory(c: Context) = prefs(c).getString("curCategory", "")!!

    /**
     * Once, the first time the essential apps (calls and messaging) are known, adds them to the
     * allowed list. Afterwards the user's own choices win, including un-ticking them.
     */
    @Synchronized
    fun seedExcluded(c: Context, essential: Set<String>) {
        if (prefs(c).getBoolean("essentialSeeded", false)) return
        prefs(c).edit()
            .putStringSet("excluded", excluded(c) + essential)
            .putBoolean("essentialSeeded", true)
            .apply()
    }

    @Synchronized
    fun start(c: Context) {
        val ms = minutes(c) * 60_000L
        prefs(c).edit().putString("curCategory", category(c)).putLong("curMs", ms)
            .putInt("curBreakMinutes", breakMinutes(c)).apply()
        set(c, Phase.FOCUS, System.currentTimeMillis() + ms)
    }

    /** Giving up logs the session as abandoned; it does not count towards the home stats. */
    @Synchronized
    fun stop(c: Context) {
        if (phase(c) == Phase.FOCUS) {
            // Time already ran out: settle() records it as completed (and starts the break).
            if (settle(c) != null) return
            val now = System.currentTimeMillis()
            val ms = prefs(c).getLong("curMs", 25 * 60_000L)
            val elapsed = (now - (endAt(c) - ms)) / 1000
            History.add(c, now, currentCategory(c), (ms / 60_000).toInt(), elapsed.coerceAtLeast(0), false)
        }
        set(c, Phase.IDLE, 0)
    }

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
                val minutes = (prefs(c).getLong("curMs", 25 * 60_000L) / 60_000).toInt()
                History.add(c, end, currentCategory(c), minutes, minutes * 60L, true)
                phase = Phase.BREAK
                end += currentBreakMinutes(c) * 60_000L
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
