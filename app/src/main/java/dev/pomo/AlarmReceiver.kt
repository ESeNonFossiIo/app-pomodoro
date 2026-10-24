package dev.pomo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val text = when (Timer.settle(c)) {
            Phase.FOCUS -> "Focus complete. Tap to start your ${Timer.currentBreakMinutes(c)} minute break."
            Phase.BREAK -> "Break over."
            else -> return
        }
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("alerts", "Timer alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            1,
            android.app.Notification.Builder(c, "alerts")
                .setSmallIcon(R.drawable.ic_stat_pomo)
                .setContentTitle("Pomodoro")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
