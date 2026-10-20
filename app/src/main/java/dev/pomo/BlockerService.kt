package dev.pomo

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * During a focus session, snoozes every notification until the session ends,
 * except those from excluded apps, calls, alarms and ongoing notifications.
 */
class BlockerService : NotificationListenerService() {
    private val passCategories = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_NAVIGATION,
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Timer.isBlocking(this)) return
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        if (sbn.packageName in Timer.excluded(this)) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category in passCategories) return
        // Comes back right after the focus session ends.
        snoozeNotification(sbn.key, Timer.endAt(this) - System.currentTimeMillis() + 1000)
    }
}
