package za.co.galvatech.timemotion.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import za.co.galvatech.timemotion.data.Repository
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.EventType
import za.co.galvatech.timemotion.model.Motion
import za.co.galvatech.timemotion.model.Study
import za.co.galvatech.timemotion.model.StudyEvent
import za.co.galvatech.timemotion.model.fmt

private val Tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable
fun CaptureScreen(
    repo: Repository,
    studyId: String,
    onFinished: (String) -> Unit,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val loaded = remember {
        repo.loadStudy(studyId)?.let {
            val s = it.afterReboot(SystemClock.elapsedRealtime(), System.currentTimeMillis(), Repository.bootCount(context))
            if (s != it) repo.save(s)
            s
        }
    }
    if (loaded == null) {
        LaunchedEffect(Unit) { onLeave() }
        return
    }

    var study by remember { mutableStateOf(loaded) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Landscape + keep the screen on while capturing.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = prev
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    BackHandler { confirmLeave = true }

    fun commit(s: Study) {
        val t = SystemClock.elapsedRealtime()
        val saved = s.copy(lastWall = System.currentTimeMillis(), lastClock = s.clock(t))
        study = saved
        repo.save(saved)
        now = t
    }

    fun tap(l: Int, m: Motion) {
        if (study.paused) return
        if (study.lastMotion(l)?.motionId == m.id) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val t = study.clock(SystemClock.elapsedRealtime())
        commit(study.copy(events = study.events + StudyEvent(EventType.MOTION, l, m.id, t)))
    }

    fun addUnit(l: Int) {
        if (study.paused) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val t = study.clock(SystemClock.elapsedRealtime())
        commit(study.copy(events = study.events + StudyEvent(EventType.UNIT, l, null, t)))
    }

    fun undo(l: Int) {
        val idx = study.events.indexOfLast { it.labourer == l }
        if (idx < 0) return
        val e = study.events[idx]
        val motionCount = study.events.count { it.labourer == l && it.type == EventType.MOTION }
        if (e.type == EventType.MOTION && motionCount <= 1) return   // keep the starting motion
        commit(study.copy(events = study.events.toMutableList().apply { removeAt(idx) }))
    }

    fun togglePause() {
        val t = SystemClock.elapsedRealtime()
        val p = study.pausedAt
        commit(
            if (p != null) study.copy(pausedTotalMs = study.pausedTotalMs + (t - p), pausedAt = null)
            else study.copy(pausedAt = t)
        )
    }

    fun finish() {
        val t = SystemClock.elapsedRealtime()
        commit(study.copy(endT = study.clock(t), pausedAt = null))
        onFinished(study.id)
    }

    val clockNow = study.clock(now)
    val motions = study.analysis.motions

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (study.paused) Color(0xFF9CA3AF) else Color(0xFFEF4444)))
            Text(
                study.analysis.name + if (study.paused) " · PAUSED" else " · recording",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(fmt(clockNow), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = Tabular)
            OutlinedButton(
                onClick = { togglePause() },
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(40.dp)
            ) { Text(if (study.paused) "Resume" else "Pause", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { confirmFinish = true },
                colors = ButtonDefaults.buttonColors(containerColor = Rust),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(40.dp)
            ) { Text("Finish", fontWeight = FontWeight.Bold) }
        }

        // One column per labourer
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFE9EBE6))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            study.labourers.forEachIndexed { l, lab ->
                val last = study.lastMotion(l)
                val cur = study.analysis.motion(last?.motionId)
                val cat = cur?.category ?: Category.NVA
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(cat.color()).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lab.name, color = cat.onColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cur?.name ?: "-", color = cat.onColor(), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            fmt(clockNow - (last?.t ?: 0L)),
                            color = cat.onColor(), fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { addUnit(l) },
                            enabled = !study.paused,
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text(
                                "+1 ${study.analysis.unitName} · ${study.units(l)}",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { undo(l) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(42.dp)
                        ) { Text("Undo", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
                    }
                    MotionGrid(
                        motions = motions,
                        currentId = cur?.id,
                        cols = if (study.labourers.size <= 2 && motions.size > 8) 3 else 2,
                        enabled = !study.paused,
                        onTap = { m -> tap(l, m) },
                        modifier = Modifier.weight(1f).fillMaxWidth().alpha(if (study.paused) 0.5f else 1f)
                    )
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish the study?") },
            text = { Text("The timer stops at ${fmt(clockNow)} and the results are calculated. You can't add more to this study afterwards.") },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; finish() }) {
                    Text("Finish", color = Rust, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this screen?") },
            text = { Text("The study stays open and the timer keeps running. Use Resume study on the home screen to come back, or Finish to stop it.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } }
        )
    }
}

@Composable
private fun MotionGrid(
    motions: List<Motion>,
    currentId: String?,
    cols: Int,
    enabled: Boolean,
    onTap: (Motion) -> Unit,
    modifier: Modifier
) {
    val rows = motions.chunked(cols)
    val scroll = rows.size > 6
    Column(
        modifier
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                (if (scroll) Modifier.height(50.dp) else Modifier.weight(1f)).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { m ->
                    val sel = m.id == currentId
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) m.category.color() else Color.White)
                            .border(if (sel) 2.dp else 1.dp, if (sel) Ink else BorderGrey, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onTap(m) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            m.category.label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (sel) m.category.onColor() else m.category.textColor()
                        )
                        Text(
                            m.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp,
                            color = if (sel) m.category.onColor() else Ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
