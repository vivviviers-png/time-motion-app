package za.co.galvatech.timemotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import za.co.galvatech.timemotion.model.JobAnalysis
import za.co.galvatech.timemotion.model.Labourer

const val MAX_LABOURERS = 4

@Composable
fun SetupScreen(
    analysis: JobAnalysis,
    initialObserver: String,
    onBack: () -> Unit,
    onStart: (observer: String, shift: String, labourers: List<Labourer>) -> Unit
) {
    var observer by remember { mutableStateOf(initialObserver) }
    var shift by remember { mutableStateOf("Day") }
    val labourers = remember { mutableStateListOf(Labourer("")) }
    val canStart = labourers.isNotEmpty() && labourers.all { it.name.isNotBlank() }

    Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
        Header(analysis.name, "New study", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = observer, onValueChange = { observer = it },
                label = { Text("Observer") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Shift")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Day", "Night").forEach { s ->
                        val sel = shift == s
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Ink else Color.White)
                                .border(1.dp, if (sel) Ink else BorderGrey, RoundedCornerShape(10.dp))
                                .selectable(selected = sel, role = Role.RadioButton) { shift = s },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(s, color = if (sel) Color.White else Ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Labourers to observe", Modifier.weight(1f))
                Text("${labourers.size} of $MAX_LABOURERS", color = Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            labourers.forEachIndexed { i, lab ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lab.name, onValueChange = { labourers[i] = lab.copy(name = it) },
                        label = { Text("Name ${i + 1}") }, singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = lab.clockNo, onValueChange = { labourers[i] = lab.copy(clockNo = it) },
                        label = { Text("Clock no") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { labourers.removeAt(i) }, enabled = labourers.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove labourer ${i + 1}", tint = Muted)
                    }
                }
            }
            if (labourers.size < MAX_LABOURERS) {
                OutlinedButton(
                    onClick = { labourers.add(Labourer("")) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add labourer", fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Everyone starts on \"${analysis.startMotion().name}\". Turn the phone sideways when the timer starts. " +
                    "The timer keeps running if the screen locks or you leave the app; reopen it to carry on.",
                fontSize = 13.sp, color = Color(0xFF134E4A),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE6F4F2))
                    .padding(12.dp)
            )
        }
        Button(
            onClick = { onStart(observer.trim(), shift, labourers.map { Labourer(it.name.trim(), it.clockNo.trim()) }) },
            enabled = canStart,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(56.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (canStart) "Start timer" else "Enter labourer names", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
