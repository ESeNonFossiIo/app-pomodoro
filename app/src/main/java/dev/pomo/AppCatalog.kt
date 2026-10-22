package dev.pomo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Launchable apps and their icons. Loading labels and icons is slow, so it happens off the main
 * thread (warmed at app start) and is cached for the life of the process.
 */
object AppCatalog {
    @Volatile
    private var apps: List<Pair<String, String>>? = null
    @Volatile
    private var essential: Set<String> = emptySet()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    /** Popular messengers, matched only if installed. */
    private val knownMessengers = setOf(
        "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.google.android.apps.messaging", "com.google.android.dialer",
        "com.samsung.android.messaging", "com.samsung.android.dialer",
    )

    fun cachedApps() = apps

    /** Packages for calls and messaging (dialer, SMS and common messengers). */
    fun cachedEssential(): Set<String> = essential
    fun cachedIcon(pkg: String): ImageBitmap? = icons[pkg]

    /** (package, label) pairs sorted by label. Call off the main thread. */
    fun load(c: Context): List<Pair<String, String>> = apps ?: synchronized(this) {
        apps ?: run {
            val pm = c.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != c.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
                .also {
                    essential = findEssential(c, it.map { app -> app.first }.toSet())
                    Timer.seedExcluded(c, essential)
                    apps = it
                }
        }
    }

    private fun findEssential(c: Context, installed: Set<String>): Set<String> {
        val pm = c.packageManager
        val found = HashSet<String>()
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        ).forEach { i -> pm.queryIntentActivities(i, 0).forEach { found += it.activityInfo.packageName } }
        runCatching { c.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let { found += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(c) }.getOrNull()?.let { found += it }
        found += knownMessengers
        return found.intersect(installed)
    }

    /** Call off the main thread. */
    fun icon(c: Context, pkg: String): ImageBitmap? = icons[pkg]
        ?: runCatching { c.packageManager.getApplicationIcon(pkg).toImageBitmap() }.getOrNull()
            ?.also { icons[pkg] = it }

    /** Loads the list and every icon in the background so the apps screen opens instantly. */
    fun warmUp(c: Context) {
        val app = c.applicationContext
        Thread {
            load(app).forEach { icon(app, it.first) }
        }.start()
    }

    private fun Drawable.toImageBitmap(size: Int = 80): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }
}
