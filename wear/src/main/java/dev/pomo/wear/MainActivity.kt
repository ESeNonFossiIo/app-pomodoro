package dev.pomo.wear

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay

private const val STATE = "/pomo/state"
private const val START = "/pomo/start"
private const val STOP = "/pomo/stop"
private const val BREAK = "/pomo/break"
private const val SYNC = "/pomo/sync"
private const val PHONE_CAPABILITY = "pomodoro_phone"

/** What the phone last told us. The phone owns the timer. */
private data class PomoState(
    val phase: String,
    val endAt: Long,
    val totalMs: Long,
    val category: String,
    val minutes: Int,
)

private fun DataMap.toState() = PomoState(
    phase = getString("phase", "IDLE"),
    endAt = getLong("endAt"),
    totalMs = getLong("totalMs"),
    category = getString("category", ""),
    minutes = getInt("minutes", 25),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Watch() } }
    }
}

private fun send(c: Context, nodeIds: Set<String>, path: String) {
    nodeIds.forEach { Wearable.getMessageClient(c).sendMessage(it, path, ByteArray(0)) }
}

@Composable
private fun Watch() {
    val c = LocalContext.current
    var state by remember { mutableStateOf<PomoState?>(null) }
    var phoneNodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val connected = phoneNodes.isNotEmpty()

    DisposableEffect(Unit) {
        val data = Wearable.getDataClient(c)
        val dataListener = DataClient.OnDataChangedListener { events ->
            events.forEach { e ->
                if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == STATE) {
                    state = DataMapItem.fromDataItem(e.dataItem).dataMap.toState()
                }
            }
        }
        data.addListener(dataListener)
        // Show the last state we have, even before the phone answers.
        data.getDataItems(Uri.parse("wear://*$STATE")).addOnSuccessListener { items ->
            items.forEach { state = DataMapItem.fromDataItem(it).dataMap.toState() }
            items.release()
        }

        // Is the phone app installed and reachable right now?
        val capabilities = Wearable.getCapabilityClient(c)
        fun refresh() {
            capabilities.getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { info -> phoneNodes = info.nodes.map { it.id }.toSet() }
                .addOnFailureListener { phoneNodes = emptySet() }
        }
        val capabilityListener = CapabilityClient.OnCapabilityChangedListener { refresh() }
        capabilities.addListener(capabilityListener, PHONE_CAPABILITY)
        refresh()

        onDispose {
            data.removeListener(dataListener)
            capabilities.removeListener(capabilityListener)
        }
    }
    // Every time the phone (re)appears, ask it for fresh state.
    LaunchedEffect(phoneNodes) { if (connected) send(c, phoneNodes, SYNC) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val s = state
    val phase = s?.phase ?: "IDLE"
    val done = phase == "DONE"
    val running = phase == "FOCUS" || phase == "BREAK"
    // DONE counts up from the moment the focus ended; the others count down.
    val ms = when {
        s == null -> 0L
        done -> (now - s.endAt).coerceAtLeast(0)
        running -> (s.endAt - now).coerceAtLeast(0)
        else -> s.minutes * 60_000L
    }
    val secs = if (done) ms / 1000 else (ms + 999) / 1000
    val clock = (if (done) "+" else "") + "%02d:%02d".format(secs / 60, secs % 60)
    val progress = when {
        done -> 1f
        running && s!!.totalMs > 0 -> (1f - ms.toFloat() / s.totalMs).coerceIn(0f, 1f)
        else -> 0f
    }
    // A finished focus session inverts the palette until you tap.
    val bg = if (done) Color.White else Color.Black
    val fg = if (done) Color.Black else Color.White

    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        // The ring runs around the whole bezel.
        BezelRing(progress, dim = !connected, ringColor = fg, trackColor = if (done) Color(0xFFDDDDDD) else MaterialTheme.colors.surface)

        Column(
            Modifier.width(120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                s == null && !connected -> Text("No phone", color = fg, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
                s == null -> Text("Syncing…", color = fg, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
                else -> {
                    Text(
                        when {
                            !connected -> "Phone offline"
                            phase == "BREAK" -> "Break"
                            phase == "DONE" -> "Done · " + s.category.ifEmpty { "focus" }
                            phase == "FOCUS" -> s.category.ifEmpty { "Focus" }
                            else -> s.category.ifEmpty { "Ready" }
                        },
                        style = MaterialTheme.typography.caption1,
                        color = if (connected) fg else Color(0xFFFF8A80),
                        maxLines = 1,
                    )
                    Text(
                        clock,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        color = if (connected) fg else fg.copy(alpha = 0.5f),
                    )
                    Chip(
                        onClick = {
                            send(c, phoneNodes, when (phase) { "IDLE" -> START; "DONE" -> BREAK; else -> STOP })
                        },
                        enabled = connected,
                        label = {
                            Text(
                                when (phase) { "IDLE" -> "Start"; "FOCUS" -> "Give up"; "DONE" -> "Break"; else -> "Skip" },
                                Modifier.fillMaxSize(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        colors = when (phase) {
                            "IDLE" -> ChipDefaults.primaryChipColors()
                            "DONE" -> ChipDefaults.chipColors(backgroundColor = Color.Black, contentColor = Color.White)
                            else -> ChipDefaults.secondaryChipColors()
                        },
                        modifier = Modifier.height(36.dp).width(104.dp).padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** A ring hugging the edge of the round display; it fills as the phase progresses. */
@Composable
private fun BezelRing(progress: Float, dim: Boolean, ringColor: Color, trackColor: Color) {
    val track = trackColor
    val bar = ringColor.copy(alpha = if (dim) 0.4f else 1f)
    Canvas(Modifier.fillMaxSize()) {
        val w = 9.dp.toPx()
        val topLeft = Offset(w / 2, w / 2)
        val arc = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
        drawArc(bar, -90f, 360f * progress, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
    }
}
