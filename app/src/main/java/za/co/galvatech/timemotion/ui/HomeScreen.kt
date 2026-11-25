package za.co.galvatech.timemotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.galvatech.timemotion.data.Repository
import za.co.galvatech.timemotion.export.Exporter
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.JobAnalysis
import za.co.galvatech.timemotion.model.Study
import za.co.galvatech.timemotion.model.fmt

@Composable
fun HomeScreen(
    repo: Repository,
    onNew: () -> Unit,
    onEdit: (JobAnalysis) -> Unit,
    onStart: (JobAnalysis) -> Unit,
    onResume: (Study) -> Unit,
    onResults: (Study) -> Unit
) {
    var version by remember { mutableIntStateOf(0) }
    val analyses = remember(version) { repo.analyses() }
    val studies = remember(version) { repo.studies() }
    var toDelete by remember { mutableStateOf<Study?>(null) }

    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize()) {
            Header("Job analyses", "Galvatech · Time & Motion")
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val open = studies.filter { !it.finished }
                if (open.isNotEmpty()) {
                    item { SectionLabel("Study in progress") }
                    items(open, key = { "open-" + it.id }) { s ->
                        Card {
                            Text(s.analysis.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Started ${Exporter.dateText(s)} · ${s.labourers.size} labourer(s)" + if (s.paused) " · paused" else " · timer running",
                                fontSize = 13.sp, color = Muted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onResume(s) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Rust),
                                    modifier = Modifier.height(48.dp)
                                ) { Text("Resume study", fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { toDelete = s }, modifier = Modifier.height(48.dp)) {
                                    Text("Discard", color = Muted)
                                }
                            }
                        }
                    }
                }

                item { SectionLabel("Your analyses") }
                if (analyses.isEmpty()) {
                    item { Text("No analyses yet. Tap New analysis to set one up.", color = Muted) }
                }
                items(analyses, key = { it.id }) { a ->
                    Card {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(a.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onEdit(a) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${a.name}", tint = Muted)
                            }
                        }
                        if (a.description.isNotBlank()) Text(a.description, fontSize = 14.sp, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Category.entries.forEach { c ->
                                if (a.count(c) > 0) CategoryChip(c, "${c.label} ${a.count(c)}")
                            }
                        }
                        val n = studies.count { it.analysis.id == a.id && it.finished }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (n == 0) "No studies yet" else "$n stud${if (n == 1) "y" else "ies"}",
                                fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onStart(a) },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start study", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                val done = studies.filter { it.finished }
                if (done.isNotEmpty()) {
                    item { SectionLabel("Completed studies", Modifier.padding(top = 8.dp)) }
                    items(done, key = { "done-" + it.id }) { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, LineGrey, RoundedCornerShape(12.dp))
                                .clickable { onResults(s) }
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.analysis.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Exporter.dateText(s)} · ${s.labourers.size} labourer(s) · ${fmt(s.endT ?: 0L)}",
                                    fontSize = 13.sp, color = Muted
                                )
                            }
                            IconButton(onClick = { toDelete = s }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete study", tint = Muted)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            containerColor = Teal,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New analysis", fontWeight = FontWeight.SemiBold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        )
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (s.finished) "Delete this study?" else "Discard this study?") },
            text = { Text("${s.analysis.name}, ${Exporter.dateText(s)}. This can't be undone. Export it first if you need the data.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteStudy(s)
                    toDelete = null
                    version++
                }) { Text("Delete", color = Rust, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, LineGrey, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
