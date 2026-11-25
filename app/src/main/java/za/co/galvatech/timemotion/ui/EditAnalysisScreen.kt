package za.co.galvatech.timemotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.JobAnalysis
import za.co.galvatech.timemotion.model.Motion
import za.co.galvatech.timemotion.model.newId

@Composable
fun EditAnalysisScreen(
    initial: JobAnalysis?,
    onSave: (JobAnalysis) -> Unit,
    onDelete: (JobAnalysis) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var unit by remember { mutableStateOf(initial?.unitName ?: "Unit") }
    val motions = remember {
        mutableStateListOf<Motion>().apply {
            addAll(initial?.motions ?: listOf(Motion(name = "Idle / away", category = Category.NVA)))
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        error = when {
            name.isBlank() -> "Give the analysis a name."
            motions.isEmpty() -> "Add at least one motion."
            motions.any { it.name.isBlank() } -> "Every motion needs a name."
            else -> null
        }
        if (error != null) return
        val now = System.currentTimeMillis()
        onSave(
            JobAnalysis(
                id = initial?.id ?: newId(),
                name = name.trim(),
                description = desc.trim(),
                unitName = unit.trim().ifBlank { "Unit" },
                motions = motions.map { it.copy(name = it.name.trim()) },
                createdAt = initial?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
        Header(if (initial == null) "New job analysis" else "Edit job analysis", onBack = onBack) {
            TextButton(onClick = { save() }) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Analysis name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text("Unit counted with +1") }, singleLine = true,
                    supportingText = { Text("e.g. Jig loaded, Item blasted, Bundle wired") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Column {
                    SectionLabel("Motions (${motions.size})", Modifier.padding(top = 4.dp))
                    Text(
                        "VA = value add · NNVA = necessary but not value adding · NVA = waste",
                        fontSize = 12.sp, color = Muted
                    )
                }
            }
            itemsIndexed(motions, key = { _, m -> m.id }) { i, m ->
                MotionEditor(
                    motion = m,
                    onChange = { motions[i] = it },
                    onUp = if (i > 0) ({ val t = motions[i]; motions[i] = motions[i - 1]; motions[i - 1] = t }) else null,
                    onDown = if (i < motions.size - 1) ({ val t = motions[i]; motions[i] = motions[i + 1]; motions[i + 1] = t }) else null,
                    onDelete = { motions.removeAt(i) }
                )
            }
            item {
                OutlinedButton(
                    onClick = { motions.add(Motion(name = "", category = Category.VA)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add motion", fontWeight = FontWeight.SemiBold)
                }
            }
            error?.let { e -> item { Text(e, color = Rust, fontWeight = FontWeight.SemiBold) } }
            if (initial != null) {
                item {
                    Column {
                        Text(
                            "Changes apply to new studies. Completed studies keep the motions they were recorded with.",
                            fontSize = 12.sp, color = Muted
                        )
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete this analysis", color = Rust)
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("Completed studies stay on the phone and can still be viewed and exported.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial) }) {
                    Text("Delete", color = Rust, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MotionEditor(
    motion: Motion,
    onChange: (Motion) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, LineGrey, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = motion.name,
                onValueChange = { onChange(motion.copy(name = it)) },
                placeholder = { Text("Motion name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete motion", tint = Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                val sel = motion.category == c
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) c.color() else Color.White)
                        .border(1.dp, if (sel) c.color() else BorderGrey, RoundedCornerShape(8.dp))
                        .selectable(selected = sel, role = Role.RadioButton) { onChange(motion.copy(category = c)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(c.label, color = if (sel) c.onColor() else Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
