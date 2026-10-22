package dev.pomo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val today: Int,
    val streak: Int,
    val total: Int,
    val week: List<Pair<LocalDate, Int>>,
    val categories: List<Pair<String, Int>>,
)

data class CategoryStat(val name: String, val completed: Int, val abandoned: Int, val minutes: Int)

/** One logged focus session. `ts` is when it ended; `minutes` is the planned length. */
data class SessionRow(val id: Long, val ts: Long, val category: String, val planned: Int, val actualSec: Int, val completed: Boolean)

data class Detail(
    val completed: Int,
    val abandoned: Int,
    val focusMinutes: Int,
    val byCategory: List<CategoryStat>,
    val recent: List<SessionRow>,
    val daily: List<Pair<LocalDate, Int>>, // focus minutes per day, oldest first
)

/** One row per focus session, finished or given up. Home stats count finished ones only. */
object History {
    private class Db(c: Context) : SQLiteOpenHelper(c, "pomo.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE sessions(ts INTEGER NOT NULL, " +
                    "category TEXT NOT NULL DEFAULT '', minutes INTEGER NOT NULL DEFAULT 25, " +
                    "actual_sec INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 1)"
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
            if (o < 2) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN minutes INTEGER NOT NULL DEFAULT 25")
            }
            if (o < 3) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN actual_sec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN completed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE sessions SET actual_sec = minutes * 60")
            }
        }
    }

    fun add(c: Context, ts: Long, category: String, minutes: Int, actualSec: Long, completed: Boolean) {
        Db(c).writableDatabase.insert(
            "sessions", null,
            ContentValues().apply {
                put("ts", ts)
                put("category", category)
                put("minutes", minutes)
                put("actual_sec", actualSec)
                put("completed", if (completed) 1 else 0)
            },
        )
    }

    fun stats(c: Context): Stats {
        val zone = ZoneId.systemDefault()
        val perDay = HashMap<LocalDate, Int>()
        var total = 0
        Db(c).readableDatabase.rawQuery("SELECT ts FROM sessions WHERE completed = 1", null).use {
            while (it.moveToNext()) {
                val day = Instant.ofEpochMilli(it.getLong(0)).atZone(zone).toLocalDate()
                perDay.merge(day, 1, Int::plus)
                total++
            }
        }
        val categories = ArrayList<Pair<String, Int>>()
        Db(c).readableDatabase.rawQuery(
            "SELECT category, COUNT(*) n FROM sessions WHERE completed = 1 " +
                "GROUP BY category ORDER BY n DESC", null,
        ).use {
            while (it.moveToNext()) {
                categories += (it.getString(0).ifEmpty { "Uncategorized" }) to it.getInt(1)
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
        return Stats(perDay[today] ?: 0, streak, total, week, categories)
    }

    /** Everything, finished and given-up, for the stats screen. */
    fun detail(c: Context): Detail {
        val db = Db(c).readableDatabase
        var completed = 0
        var abandoned = 0
        var seconds = 0L
        val byCat = ArrayList<CategoryStat>()
        db.rawQuery(
            "SELECT category, SUM(completed), SUM(1 - completed), SUM(actual_sec) FROM sessions " +
                "GROUP BY category ORDER BY SUM(actual_sec) DESC", null,
        ).use {
            while (it.moveToNext()) {
                val done = it.getInt(1)
                val gaveUp = it.getInt(2)
                val sec = it.getLong(3)
                completed += done
                abandoned += gaveUp
                seconds += sec
                byCat += CategoryStat(it.getString(0).ifEmpty { "Uncategorized" }, done, gaveUp, (sec / 60).toInt())
            }
        }
        val recent = ArrayList<SessionRow>()
        db.rawQuery(
            "SELECT rowid, ts, category, minutes, actual_sec, completed FROM sessions ORDER BY ts DESC LIMIT 50", null,
        ).use {
            while (it.moveToNext()) {
                recent += SessionRow(
                    it.getLong(0), it.getLong(1), it.getString(2).ifEmpty { "Uncategorized" },
                    it.getInt(3), it.getInt(4), it.getInt(5) == 1,
                )
            }
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val perDay = HashMap<LocalDate, Int>()
        db.rawQuery(
            "SELECT ts, actual_sec FROM sessions WHERE ts >= ?",
            arrayOf(today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli().toString()),
        ).use {
            while (it.moveToNext()) {
                val day = Instant.ofEpochMilli(it.getLong(0)).atZone(zone).toLocalDate()
                perDay.merge(day, it.getInt(1) / 60, Int::plus)
            }
        }
        val daily = (13L downTo 0L).map { today.minusDays(it).let { d -> d to (perDay[d] ?: 0) } }
        return Detail(completed, abandoned, (seconds / 60).toInt(), byCat, recent, daily)
    }

    fun setCategory(c: Context, id: Long, category: String) {
        Db(c).writableDatabase.update(
            "sessions", ContentValues().apply { put("category", category) }, "rowid = ?", arrayOf(id.toString()),
        )
    }

    fun delete(c: Context, id: Long) {
        Db(c).writableDatabase.delete("sessions", "rowid = ?", arrayOf(id.toString()))
    }

    /** All sessions, oldest first, as CSV (RFC 4180 quoting). */
    fun csv(c: Context): String {
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder("ended_at,category,planned_min,actual_sec,completed\n")
        Db(c).readableDatabase.rawQuery(
            "SELECT ts, category, minutes, actual_sec, completed FROM sessions ORDER BY ts", null,
        ).use {
            while (it.moveToNext()) {
                val at = Instant.ofEpochMilli(it.getLong(0)).atZone(zone).toOffsetDateTime()
                val cat = it.getString(1).replace("\"", "\"\"")
                sb.append(at).append(",\"").append(cat).append("\",")
                    .append(it.getInt(2)).append(',').append(it.getInt(3)).append(',')
                    .append(if (it.getInt(4) == 1) "yes" else "no").append('\n')
            }
        }
        return sb.toString()
    }
}
