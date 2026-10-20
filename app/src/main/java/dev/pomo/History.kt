package dev.pomo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Stats(val today: Int, val streak: Int, val total: Int, val week: List<Pair<LocalDate, Int>>)

/** One row per completed focus session. */
object History {
    private class Db(c: Context) : SQLiteOpenHelper(c, "pomo.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE sessions(ts INTEGER NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }

    fun add(c: Context, ts: Long) {
        Db(c).writableDatabase.insert("sessions", null, ContentValues().apply { put("ts", ts) })
    }

    fun stats(c: Context): Stats {
        val zone = ZoneId.systemDefault()
        val perDay = HashMap<LocalDate, Int>()
        var total = 0
        Db(c).readableDatabase.rawQuery("SELECT ts FROM sessions", null).use {
            while (it.moveToNext()) {
                val day = Instant.ofEpochMilli(it.getLong(0)).atZone(zone).toLocalDate()
                perDay.merge(day, 1, Int::plus)
                total++
            }
        }
        val today = LocalDate.now()
        var day = if (perDay.containsKey(today)) today else today.minusDays(1)
        var streak = 0
        while (perDay.containsKey(day)) {
            streak++
            day = day.minusDays(1)
        }
        val week = (6L downTo 0L).map { today.minusDays(it).let { d -> d to (perDay[d] ?: 0) } }
        return Stats(perDay[today] ?: 0, streak, total, week)
    }
}
