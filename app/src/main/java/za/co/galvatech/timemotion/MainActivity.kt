package za.co.galvatech.timemotion

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import za.co.galvatech.timemotion.data.Repository
import za.co.galvatech.timemotion.model.EventType
import za.co.galvatech.timemotion.model.JobAnalysis
import za.co.galvatech.timemotion.model.Study
import za.co.galvatech.timemotion.model.StudyEvent
import za.co.galvatech.timemotion.ui.AppTheme
import za.co.galvatech.timemotion.ui.CaptureScreen
import za.co.galvatech.timemotion.ui.EditAnalysisScreen
import za.co.galvatech.timemotion.ui.HomeScreen
import za.co.galvatech.timemotion.ui.ResultsScreen
import za.co.galvatech.timemotion.ui.SetupScreen

sealed interface Screen {
    data object Home : Screen
    data class Edit(val analysis: JobAnalysis?) : Screen
    data class Setup(val analysis: JobAnalysis) : Screen
    data class Capture(val studyId: String) : Screen
    data class Results(val studyId: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        val repo = Repository(applicationContext).also { it.seedIfEmpty() }
        setContent {
            AppTheme { App(repo) }
        }
    }
}

@Composable
fun App(repo: Repository) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = screen is Screen.Edit || screen is Screen.Setup || screen is Screen.Results) {
        screen = Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            repo = repo,
            onNew = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) },
            onStart = { screen = Screen.Setup(it) },
            onResume = { screen = Screen.Capture(it.id) },
            onResults = { screen = Screen.Results(it.id) }
        )

        is Screen.Edit -> EditAnalysisScreen(
            initial = s.analysis,
            onSave = { repo.save(it); screen = Screen.Home },
            onDelete = { repo.deleteAnalysis(it); screen = Screen.Home },
            onBack = { screen = Screen.Home }
        )

        is Screen.Setup -> SetupScreen(
            analysis = s.analysis,
            initialObserver = repo.lastObserver,
            onBack = { screen = Screen.Home },
            onStart = { observer, shift, labourers ->
                repo.lastObserver = observer
                val start = s.analysis.startMotion()
                val study = Study(
                    analysis = s.analysis,
                    observer = observer,
                    shift = shift,
                    labourers = labourers,
                    startedAtWall = System.currentTimeMillis(),
                    startElapsed = SystemClock.elapsedRealtime(),
                    bootCount = Repository.bootCount(context),
                    events = labourers.indices.map { StudyEvent(EventType.MOTION, it, start.id, 0L) }
                )
                repo.save(study)
                screen = Screen.Capture(study.id)
            }
        )

        is Screen.Capture -> CaptureScreen(
            repo = repo,
            studyId = s.studyId,
            onFinished = { screen = Screen.Results(it) },
            onLeave = { screen = Screen.Home }
        )

        is Screen.Results -> ResultsScreen(
            repo = repo,
            studyId = s.studyId,
            onBack = { screen = Screen.Home }
        )
    }
}
