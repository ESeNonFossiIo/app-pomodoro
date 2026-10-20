package dev.pomo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private fun listenerEnabled(c: Context) =
    Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        ?.contains(c.packageName) == true

@Composable
private fun App() {
    var showApps by remember { mutableStateOf(false) }
    if (showApps) {
        BackHandler { showApps = false }
        AppsScreen(onBack = { showApps = false })
    } else {
        HomeScreen(onApps = { showApps = true })
    }
}

@Composable
private fun HomeScreen(onApps: () -> Unit) {
    val c = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            Timer.settle(c)
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val phase = Timer.phase(c)
    val remaining = when (phase) {
        Phase.IDLE -> Timer.FOCUS_MS
        else -> (Timer.endAt(c) - now).coerceAtLeast(0)
    }
    val stats = remember(phase) { History.stats(c) }
    val secs = (remaining + 999) / 1000

    Column(
        Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!listenerEnabled(c)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notification access is off, so nothing will be blocked.")
                    TextButton(onClick = {
                        c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text("Grant access") }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            when (phase) { Phase.IDLE -> "Ready"; Phase.FOCUS -> "Focus"; Phase.BREAK -> "Break" },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "%02d:%02d".format(secs / 60, secs % 60),
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(16.dp))
        when (phase) {
            Phase.IDLE -> Button(onClick = { Timer.start(c) }) { Text("Start focus") }
            Phase.FOCUS -> OutlinedButton(onClick = { Timer.stop(c) }) { Text("Give up") }
            Phase.BREAK -> OutlinedButton(onClick = { Timer.stop(c) }) { Text("Skip break") }
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Today", stats.today)
            Stat("Streak", stats.streak)
            Stat("Total", stats.total)
        }
        Spacer(Modifier.height(24.dp))
        val max = stats.week.maxOf { it.second }.coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.week.forEach { (day, n) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(24.dp).height((60.dp * n / max).coerceAtLeast(4.dp))
                            .background(
                                if (n > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                    )
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        TextButton(onClick = onApps) { Text("Apps allowed during focus") }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AppsScreen(onBack: () -> Unit) {
    val c = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        c.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(c.packageManager).toString() }
            .filter { it.first != c.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    var excluded by remember { mutableStateOf(Timer.excluded(c)) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Allowed during focus", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn {
            items(apps, key = { it.first }) { (pkg, label) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f))
                    Checkbox(checked = pkg in excluded, onCheckedChange = { on ->
                        excluded = if (on) excluded + pkg else excluded - pkg
                        Timer.setExcluded(c, excluded)
                    })
                }
            }
        }
    }
}
