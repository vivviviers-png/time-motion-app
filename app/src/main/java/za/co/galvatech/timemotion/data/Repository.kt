package za.co.galvatech.timemotion.data

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.JobAnalysis
import za.co.galvatech.timemotion.model.Motion
import za.co.galvatech.timemotion.model.Study
import java.io.File

/** Stores everything as small JSON files in the app's private storage. No server. */
class Repository(private val context: Context) {
    private val analysesDir = File(context.filesDir, "analyses").apply { mkdirs() }
    private val studiesDir = File(context.filesDir, "studies").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun analyses(): List<JobAnalysis> =
        (analysesDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { JobAnalysis.fromJson(JSONObject(it.readText())) }.getOrNull() }
            .sortedByDescending { it.updatedAt }

    fun save(a: JobAnalysis) = write(File(analysesDir, "${a.id}.json"), a.toJson().toString(2))

    fun deleteAnalysis(a: JobAnalysis) {
        File(analysesDir, "${a.id}.json").delete()
    }

    fun studies(): List<Study> =
        (studiesDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { Study.fromJson(JSONObject(it.readText())) }.getOrNull() }
            .sortedByDescending { it.startedAtWall }

    fun loadStudy(id: String): Study? {
        val f = File(studiesDir, "$id.json")
        if (!f.exists()) return null
        return runCatching { Study.fromJson(JSONObject(f.readText())) }.getOrNull()
    }

    fun save(s: Study) = write(File(studiesDir, "${s.id}.json"), s.toJson().toString())

    fun deleteStudy(s: Study) {
        File(studiesDir, "${s.id}.json").delete()
    }

    var lastObserver: String
        get() = prefs.getString("observer", "") ?: ""
        set(v) = prefs.edit().putString("observer", v).apply()

    fun seedIfEmpty() {
        if (prefs.getBoolean("seeded", false)) return
        if (analyses().isEmpty()) {
            save(
                JobAnalysis(
                    name = "Jigging – Galv line (example)",
                    description = "Wiring items onto jigs before pre-treatment, from material bay to crane pick-up. Edit or delete this example.",
                    unitName = "Jig loaded",
                    motions = listOf(
                        Motion(name = "Wire item to jig", category = Category.VA),
                        Motion(name = "Hang jig on crane", category = Category.VA),
                        Motion(name = "Fetch wire / tools", category = Category.NNVA),
                        Motion(name = "Walk to material bay", category = Category.NNVA),
                        Motion(name = "PPE / safety check", category = Category.NNVA),
                        Motion(name = "Wait for crane", category = Category.NVA),
                        Motion(name = "Idle / away", category = Category.NVA)
                    )
                )
            )
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }

    /** Writes to a temp file first so a crash mid-write can't corrupt a study. */
    private fun write(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        fun bootCount(context: Context): Int = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (e: Exception) {
            -1
        }
    }
}
