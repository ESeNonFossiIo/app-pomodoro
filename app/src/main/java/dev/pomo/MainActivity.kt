package dev.pomo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCatalog.warmUp(this)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            var mode by remember { mutableStateOf(Timer.themeMode(this)) }
            // The phase is real state: it moves finished phases along, and anything that depends on
            // it (which screen, the palette) recomposes when it changes, from here, the watch or an alarm.
            var phase by remember { mutableStateOf(Timer.phase(this)) }
            LaunchedEffect(Unit) {
                while (true) {
                    Timer.settle(this@MainActivity)
                    phase = Timer.phase(this@MainActivity)
                    delay(500)
                }
            }
            // A finished focus session inverts the palette until the user taps.
            PomoTheme(mode, inverted = phase == Phase.DONE) {
                Surface(Modifier.fillMaxSize()) {
                    App(mode, phase, onMode = { mode = it; Timer.setThemeMode(this, it) })
                }
            }
        }
    }
}

private fun listenerEnabled(c: Context) =
    Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        ?.contains(c.packageName) == true

@Composable
private fun App(mode: ThemeMode, phase: Phase, onMode: (ThemeMode) -> Unit) {
    val c = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val full = screen == Screen.Home && (phase == Phase.FOCUS || phase == Phase.DONE) && Timer.fullscreenFocus(c)
    when {
        full -> {
            BackHandler { } // the only way out is "Give up", or tapping through the end of the session
            FullscreenScreen()
        }
        screen == Screen.Apps -> {
            BackHandler { screen = Screen.Settings }
            AppsScreen(onBack = { screen = Screen.Settings })
        }
        screen == Screen.Stats -> {
            BackHandler { screen = Screen.Settings }
            StatsScreen(onBack = { screen = Screen.Settings })
        }
        screen == Screen.Settings -> {
            BackHandler { screen = Screen.Home }
            SettingsScreen(
                onBack = { screen = Screen.Home },
                onStats = { screen = Screen.Stats },
                onApps = { screen = Screen.Apps },
                mode = mode,
                onMode = onMode,
            )
        }
        else -> HomeScreen(onSettings = { screen = Screen.Settings })
    }
}

private enum class Screen { Home, Apps, Settings, Stats }

private fun pinningEnabled(c: Context) = runCatching {
    Settings.System.getInt(c.contentResolver, "lock_to_app_enabled", 0) == 1 ||
        Settings.Secure.getInt(c.contentResolver, "lock_to_app_enabled", 0) == 1
}.getOrDefault(false)

/** Back arrow, title and optional actions, over a thin divider. */
@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            actions()
        }
        HorizontalDivider()
    }
}

/** Small coloured label that starts a group of rows. */
@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Suppress("DEPRECATION")
private fun appVersion(c: Context) = runCatching {
    val p = c.packageManager.getPackageInfo(c.packageName, 0)
    "v${p.versionName} (${p.versionCode})"
}.getOrDefault("")

@Composable
private fun SettingsScreen(
    onBack: () -> Unit, onStats: () -> Unit, onApps: () -> Unit,
    mode: ThemeMode, onMode: (ThemeMode) -> Unit,
) {
    val c = LocalContext.current
    var fullscreen by remember { mutableStateOf(Timer.fullscreenFocus(c)) }
    var hidePinWarning by remember { mutableStateOf(Timer.hidePinWarning(c)) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Menu", onBack)
        MenuItem("Stats and session log", "Totals, categories, history and CSV export", onStats)
        MenuItem("Apps allowed during focus", "Their notifications are never snoozed", onApps)
        SectionHeader("Focus mode", Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Fullscreen and lock during focus")
                Text(
                    "Hide the system bars, keep the screen on and pin the app so you can't " +
                        "switch to other apps while a focus session runs.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = fullscreen, onCheckedChange = {
                fullscreen = it
                Timer.setFullscreenFocus(c, it)
            })
        }
        if (fullscreen && !pinningEnabled(c) && !hidePinWarning) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Locking needs Android's app pinning, which is off. Turn on \"App pinning\" in Security settings.")
                    TextButton(onClick = {
                        c.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }) { Text("Open security settings") }
                    Row(
                        Modifier.clickable {
                            hidePinWarning = true
                            Timer.setHidePinWarning(c, true)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = false, onCheckedChange = {
                            hidePinWarning = true
                            Timer.setHidePinWarning(c, true)
                        })
                        Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        SectionHeader("Appearance", Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == ThemeMode.COLOR, onClick = { onMode(ThemeMode.COLOR) }, label = { Text("Colour") })
            FilterChip(selected = mode == ThemeMode.MONO, onClick = { onMode(ThemeMode.MONO) }, label = { Text("Black and white") })
        }
    }
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Text("Pomodoro ${appVersion(c)}", style = MaterialTheme.typography.labelMedium)
    }
}

/** Settles finished phases and returns the current time, refreshed twice a second. */
@Composable
private fun rememberNow(): Long {
    val c = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            Timer.settle(c)
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return now
}

/** Label, "−5", value, "+5". */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onChange((value - 5).coerceAtLeast(min)) }) { Text("−5") }
        Text("$value min", Modifier.width(72.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChange((value + 5).coerceAtMost(max)) }) { Text("+5") }
    }
}

/** The time inside a ring that fills as the phase progresses. */
@Composable
private fun TimerRing(progress: Float, size: Dp, text: String, fontSize: TextUnit) {
    val p by animateFloatAsState(progress, tween(500, easing = LinearEasing), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            drawArc(bar, -90f, 360f * p, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun PhaseButton(phase: Phase) {
    val c = LocalContext.current
    when (phase) {
        Phase.IDLE -> Button(onClick = { Timer.start(c) }) { Text("Start focus") }
        Phase.FOCUS -> OutlinedButton(onClick = { Timer.stop(c) }) { Text("Give up") }
        Phase.BREAK -> OutlinedButton(onClick = { Timer.stop(c) }) { Text("Skip break") }
        Phase.DONE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { Timer.startBreak(c) }) { Text("Start ${Timer.currentBreakMinutes(c)} min break") }
            TextButton(onClick = { Timer.stop(c) }) { Text("Done") }
        }
    }
}

/** mm:ss on the clock; "+mm:ss" counting up once a focus session is over. */
private fun clockText(c: Context, phase: Phase, now: Long, idleMinutes: Int): String {
    val ms = when (phase) {
        Phase.IDLE -> idleMinutes * 60_000L
        Phase.DONE -> (now - Timer.endAt(c)).coerceAtLeast(0)
        else -> (Timer.endAt(c) - now).coerceAtLeast(0)
    }
    // The countdown rounds up so it never shows 00:00 early; the overtime counter rounds down.
    val secs = if (phase == Phase.DONE) ms / 1000 else (ms + 999) / 1000
    return (if (phase == Phase.DONE) "+" else "") + "%02d:%02d".format(secs / 60, secs % 60)
}

private fun phaseLabel(c: Context, phase: Phase) = when (phase) {
    Phase.IDLE -> "Ready"
    Phase.FOCUS -> "Focus · ${Timer.currentCategory(c).ifEmpty { "Uncategorized" }}"
    Phase.BREAK -> "Break"
    Phase.DONE -> "Focus complete · ${Timer.currentCategory(c).ifEmpty { "Uncategorized" }}"
}

/**
 * Just the timer: system bars hidden, screen kept on and the app pinned (screen pinning), so
 * swipes can't switch apps. Pinning only works if "App pinning" is enabled in system settings.
 */
@Composable
private fun FullscreenScreen() {
    val c = LocalContext.current
    val view = LocalView.current
    val now = rememberNow()
    DisposableEffect(Unit) {
        val activity = view.context as android.app.Activity
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        runCatching { activity.startLockTask() }
        onDispose {
            runCatching { activity.stopLockTask() }
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    val phase = Timer.phase(c)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(phaseLabel(c, phase), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        TimerRing(Timer.progress(c, now), 300.dp, clockText(c, phase, now, Timer.minutes(c)), 80.sp)
        Spacer(Modifier.height(24.dp))
        PhaseButton(phase)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(onSettings: () -> Unit) {
    val c = LocalContext.current
    val now = rememberNow()
    var minutes by remember { mutableIntStateOf(Timer.minutes(c)) }
    var breakMin by remember { mutableIntStateOf(Timer.breakMinutes(c)) }
    var category by remember { mutableStateOf(Timer.category(c)) }
    var categories by remember { mutableStateOf(Timer.categories(c)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val phase = Timer.phase(c)
    val stats = remember(phase) { History.stats(c) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Menu, contentDescription = "Settings") }
        }
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
        Text(phaseLabel(c, phase), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        TimerRing(Timer.progress(c, now), 240.dp, clockText(c, phase, now, minutes), 60.sp)
        Spacer(Modifier.height(8.dp))
        if (phase == Phase.IDLE) {
            Stepper("Focus", minutes, Timer.MIN_MINUTES, Timer.MAX_MINUTES) {
                minutes = it; Timer.setMinutes(c, it)
            }
            Stepper("Break", breakMin, Timer.MIN_BREAK, Timer.MAX_BREAK) {
                breakMin = it; Timer.setBreakMinutes(c, it)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                categories.forEach { name ->
                    FilterChip(
                        selected = !editing && name == category,
                        onClick = {
                            if (editing) {
                                Timer.removeCategory(c, name)
                                categories = Timer.categories(c)
                                category = Timer.category(c)
                            } else {
                                category = name; Timer.setCategory(c, name)
                            }
                        },
                        label = { Text(if (editing) "$name  ✕" else name) },
                    )
                }
                AssistChip(onClick = { adding = true }, label = { Text("+ New") })
                AssistChip(
                    onClick = { editing = !editing },
                    label = { Text(if (editing) "Done" else "Edit") },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PhaseButton(phase)
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Today", stats.today)
            Stat("Streak", stats.streak)
            Stat("Total", stats.total)
        }
        if (stats.categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stats.categories.take(4).joinToString("  ·  ") { "${it.first} ${it.second}" },
                style = MaterialTheme.typography.labelMedium,
            )
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
        if (adding) {
            NewCategoryDialog(
                onDismiss = { adding = false },
                onAdd = {
                    Timer.addCategory(c, it)
                    categories = Timer.categories(c)
                    category = Timer.category(c)
                    adding = false
                },
            )
        }
    }
}

private fun fmtMinutes(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Focus minutes per day as bars; today is solid, empty days are a faint stub. */
@Composable
private fun ActivityChart(daily: List<Pair<java.time.LocalDate, Int>>) {
    val max = daily.maxOf { it.second }.coerceAtLeast(1)
    val today = java.time.LocalDate.now()
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        daily.forEach { (day, min) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((min.toFloat() / max).coerceAtLeast(0.04f))
                            .background(
                                when {
                                    min == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    day == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                },
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            )
                    )
                }
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(s: CategoryStat, maxMinutes: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(fmtMinutes(s.minutes), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(
                Modifier.fillMaxWidth((s.minutes.toFloat() / maxMinutes).coerceIn(0.02f, 1f)).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
        Text(
            "${s.completed} done" + if (s.abandoned > 0) " · ${s.abandoned} gave up" else "",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SessionItem(r: SessionRow, time: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(
                if (r.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (r.completed) "✓" else "✗",
                color = if (r.completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text("${r.category}  ✎", fontWeight = FontWeight.Medium)
            Text(
                time + " · " + if (r.completed) "${r.planned} min" else "${r.actualSec / 60} of ${r.planned} min",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsScreen(onBack: () -> Unit) {
    val c = LocalContext.current
    var d by remember { mutableStateOf(History.detail(c)) }
    var editing by remember { mutableStateOf<SessionRow?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                c.contentResolver.openOutputStream(uri)?.use { it.write(History.csv(c).toByteArray()) }
            }
        }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now()
    fun dayLabel(day: java.time.LocalDate) = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayFmt)
    }
    val total = d.completed + d.abandoned
    val maxMinutes = d.byCategory.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    val byDay = d.recent.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Stats", onBack) {
            TextButton(onClick = { export.launch("pomodoro-sessions.csv") }) { Text("Export CSV") }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("${d.completed}", "Completed", Modifier.weight(1f))
                    StatTile("${d.abandoned}", "Gave up", Modifier.weight(1f))
                    StatTile(fmtMinutes(d.focusMinutes), "Focused", Modifier.weight(1f))
                }
                if (total > 0) {
                    Text(
                        "${d.completed * 100 / total}% of sessions finished",
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (total == 0) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🍅", fontSize = 56.sp)
                        Text("No sessions yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Finish a focus session and it will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                return@LazyColumn
            }
            item {
                SectionHeader("Last 14 days")
                ActivityChart(d.daily)
                Text(
                    "Best day: ${fmtMinutes(d.daily.maxOf { it.second })}",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (d.byCategory.isNotEmpty()) {
                item { SectionHeader("By category") }
                items(d.byCategory) { CategoryRow(it, maxMinutes) }
            }
            item { SectionHeader("Recent sessions") }
            byDay.forEach { (day, rows) ->
                item(key = "day-$day") {
                    Text(
                        dayLabel(day),
                        Modifier.padding(top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(rows, key = { it.id }) { r ->
                    SessionItem(
                        r,
                        Instant.ofEpochMilli(r.ts).atZone(zone).format(timeFmt),
                        onEdit = { editing = r },
                        onDelete = {
                            History.delete(c, r.id)
                            d = History.detail(c)
                        },
                    )
                }
            }
        }
    }
    editing?.let { row ->
        CategoryPickerDialog(
            current = row.category,
            onDismiss = { editing = null },
            onPick = {
                History.setCategory(c, row.id, it)
                d = History.detail(c)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = LocalContext.current
    // A category that was deleted from the list can still be on old entries.
    val options = remember { (Timer.categories(c) + current).distinct() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change category") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { name ->
                    FilterChip(selected = name == current, onClick = { onPick(name) }, label = { Text(name) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    val apps by produceState(AppCatalog.cachedApps()) {
        value = withContext(Dispatchers.Default) { AppCatalog.load(c) }
    }
    var excluded by remember { mutableStateOf(Timer.excluded(c)) }
    var query by remember { mutableStateOf("") }
    // The first load seeds the essential apps as allowed; pick that up.
    LaunchedEffect(apps) { excluded = Timer.excluded(c) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Apps allowed during focus", onBack)
        OutlinedTextField(
            query, { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps") },
            singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") } },
        )
        val list = apps?.filter { it.second.contains(query.trim(), ignoreCase = true) }
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val essential = AppCatalog.cachedEssential()
        val (top, rest) = list.partition { it.first in essential }
        val row: @Composable (Pair<String, String>) -> Unit = { (pkg, label) ->
            AppRow(pkg, label, pkg in excluded) { on ->
                excluded = if (on) excluded + pkg else excluded - pkg
                Timer.setExcluded(c, excluded)
            }
        }
        LazyColumn {
            if (top.isNotEmpty()) {
                item { SectionHeader("Calls and messages", Modifier.padding(horizontal = 16.dp)) }
                items(top, key = { "t-" + it.first }) { row(it) }
            }
            if (rest.isNotEmpty()) {
                item { SectionHeader("Other apps", Modifier.padding(horizontal = 16.dp)) }
                items(rest, key = { it.first }) { row(it) }
            }
            if (top.isEmpty() && rest.isEmpty()) {
                item { Text("No apps match.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun AppRow(pkg: String, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val c = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon by produceState<ImageBitmap?>(AppCatalog.cachedIcon(pkg), pkg) {
            if (value == null) value = withContext(Dispatchers.Default) { AppCatalog.icon(c, pkg) }
        }
        Box(Modifier.size(40.dp)) {
            icon?.let { Image(it, contentDescription = null, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
