package za.co.galvatech.timemotion.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.galvatech.timemotion.data.Repository
import za.co.galvatech.timemotion.export.Exporter
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.PersonResult
import za.co.galvatech.timemotion.model.analyse
import za.co.galvatech.timemotion.model.crew
import za.co.galvatech.timemotion.model.fmt
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(repo: Repository, studyId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val study = remember { repo.loadStudy(studyId) }
    if (study == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val results = remember(study) { analyse(study, SystemClock.elapsedRealtime()) }
    val crewSum = remember(results) { crew(results) }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val unit = study.analysis.unitName

    fun export(kind: String) {
        try {
            val (file: File, mime: String) = when (kind) {
                "xlsx" -> Exporter.xlsx(context, study, results) to Exporter.XLSX_MIME
                "pdf" -> Exporter.pdf(context, study, results) to "application/pdf"
                else -> Exporter.studyJson(context, study) to "application/json"
            }
            Exporter.share(context, file, mime)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Header(
            study.analysis.name,
            "Results · ${Exporter.dateText(study)} · ${fmt(study.endT ?: 0L)}",
            onBack = onBack
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("Crew VA", "${crewSum.pct(Category.VA).roundToInt()}%", Teal, Modifier.weight(1f))
                    Tile("$unit count", "${crewSum.units}", Ink, Modifier.weight(1f))
                    Tile("Labour per unit", crewSum.msPerUnit?.let { fmt(it) } ?: "-", Ink, Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend()
                    Text("Tap a labourer for their timeline and motion breakdown.", fontSize = 12.sp, color = Muted)
                }
            }
            items(results, key = { it.index }) { r ->
                PersonCard(
                    r = r,
                    unit = unit,
                    studyEnd = study.endT ?: 0L,
                    expanded = r.index in expanded,
                    onToggle = { expanded = if (r.index in expanded) expanded - r.index else expanded + r.index }
                )
            }
            item {
                Text(
                    "Observer: ${study.observer.ifBlank { "-" }} · Shift: ${study.shift}" +
                        if (study.analysis.description.isNotBlank()) "\n${study.analysis.description}" else "",
                    fontSize = 12.sp, color = Muted
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { export("xlsx") },
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("Excel", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { export("pdf") },
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("PDF", fontWeight = FontWeight.Bold, color = Ink) }
            OutlinedButton(
                onClick = { export("json") },
                modifier = Modifier.weight(1f).height(52.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("Study file", fontWeight = FontWeight.Bold, color = Ink, maxLines = 1) }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, LineGrey, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 24.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PersonCard(r: PersonResult, unit: String, studyEnd: Long, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, LineGrey, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.labourer.name + if (r.labourer.clockNo.isNotBlank()) " (${r.labourer.clockNo})" else "",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Text(
                "${r.units} × $unit" + (r.msPerUnit?.let { " · ${fmt(it)} each" } ?: ""),
                fontSize = 13.sp, color = Muted
            )
        }
        StackedBar(r)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Category.entries.forEach { c ->
                Text("${c.label} ${fmt(r.ms(c))}", fontSize = 12.sp, color = c.textColor(), fontWeight = FontWeight.SemiBold)
            }
        }
        if (expanded) {
            Text("Timeline", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(4.dp)).background(LineGrey)
            ) {
                if (studyEnd > 0) r.segments.forEach { s ->
                    val w = s.dur.toFloat() / studyEnd
                    if (w > 0f) Box(Modifier.weight(w).fillMaxHeight().background(s.motion.category.color()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("00:00", fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                Text(fmt(studyEnd), fontSize = 11.sp, color = Muted)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Motion", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Count", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                Text("Total", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                Text("Avg", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
                Text("%", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            }
            r.motions.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(m.motion.category.color()))
                    Text(
                        m.motion.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    )
                    Text("${m.count}", fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    Text(fmt(m.totalMs), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
                    Text(fmt(m.avgMs), fontSize = 13.sp, modifier = Modifier.width(46.dp))
                    Text(
                        if (r.totalMs > 0) "${(m.totalMs * 100.0 / r.totalMs).roundToInt()}%" else "0%",
                        fontSize = 13.sp, modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
    }
}
