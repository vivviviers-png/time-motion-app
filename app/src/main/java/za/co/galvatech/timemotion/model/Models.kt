package za.co.galvatech.timemotion.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class Category(val label: String, val description: String) {
    VA("VA", "Value add"),
    NNVA("NNVA", "Necessary non-value add"),
    NVA("NVA", "Non-value add (waste)")
}

fun newId(): String = UUID.randomUUID().toString()

/** mm:ss, or h:mm:ss when an hour or longer. */
fun fmt(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x)
    else String.format(Locale.US, "%02d:%02d", m, x)
}

data class Motion(
    val id: String = newId(),
    val name: String,
    val category: Category
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("category", category.name)

    companion object {
        fun fromJson(o: JSONObject) = Motion(
            id = o.getString("id"),
            name = o.getString("name"),
            category = Category.valueOf(o.getString("category"))
        )
    }
}

data class JobAnalysis(
    val id: String = newId(),
    val name: String,
    val description: String,
    val unitName: String,
    val motions: List<Motion>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun motion(id: String?): Motion? = motions.firstOrNull { it.id == id }
    fun count(c: Category): Int = motions.count { it.category == c }

    /** The motion every labourer starts on: "Idle" if there is one, else the first NVA motion, else the first. */
    fun startMotion(): Motion =
        motions.firstOrNull { it.category == Category.NVA && it.name.contains("idle", ignoreCase = true) }
            ?: motions.firstOrNull { it.category == Category.NVA }
            ?: motions.first()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("description", description)
        .put("unitName", unitName).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("motions", JSONArray().apply { motions.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): JobAnalysis {
            val arr = o.getJSONArray("motions")
            return JobAnalysis(
                id = o.getString("id"),
                name = o.getString("name"),
                description = o.optString("description", ""),
                unitName = o.optString("unitName", "Unit"),
                motions = (0 until arr.length()).map { Motion.fromJson(arr.getJSONObject(it)) },
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt")
            )
        }
    }
}

data class Labourer(val name: String, val clockNo: String = "") {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("clockNo", clockNo)

    companion object {
        fun fromJson(o: JSONObject) = Labourer(o.getString("name"), o.optString("clockNo", ""))
    }
}

enum class EventType { MOTION, UNIT }

/** t = study-clock milliseconds (pauses excluded) since the study started. */
data class StudyEvent(val type: EventType, val labourer: Int, val motionId: String?, val t: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name).put("labourer", labourer)
        .put("motionId", motionId ?: JSONObject.NULL).put("t", t)

    companion object {
        fun fromJson(o: JSONObject) = StudyEvent(
            type = EventType.valueOf(o.getString("type")),
            labourer = o.getInt("labourer"),
            motionId = if (o.isNull("motionId")) null else o.getString("motionId"),
            t = o.getLong("t")
        )
    }
}

/**
 * One observation session. The study clock is based on SystemClock.elapsedRealtime(),
 * which keeps counting while the phone sleeps, so timing continues if the screen locks
 * or the app is closed and reopened.
 */
data class Study(
    val id: String = newId(),
    val analysis: JobAnalysis,          // snapshot, so later edits don't change old results
    val observer: String,
    val shift: String,
    val labourers: List<Labourer>,
    val startedAtWall: Long,
    val startElapsed: Long,
    val bootCount: Int,
    val pausedTotalMs: Long = 0,
    val pausedAt: Long? = null,         // elapsedRealtime when paused
    val endT: Long? = null,             // study clock at finish
    val events: List<StudyEvent> = emptyList(),
    val lastWall: Long = startedAtWall,
    val lastClock: Long = 0
) {
    val finished: Boolean get() = endT != null
    val paused: Boolean get() = pausedAt != null && endT == null

    fun clock(nowElapsed: Long): Long =
        endT ?: ((pausedAt ?: nowElapsed) - startElapsed - pausedTotalMs).coerceAtLeast(0)

    fun lastMotion(l: Int): StudyEvent? =
        events.lastOrNull { it.type == EventType.MOTION && it.labourer == l }

    fun units(l: Int): Int = events.count { it.type == EventType.UNIT && it.labourer == l }

    /** If the phone restarted mid-study, continue the clock from the last save plus the wall-clock gap. */
    fun afterReboot(nowElapsed: Long, nowWall: Long, currentBoot: Int): Study {
        if (endT != null) return this
        val rebooted = (currentBoot != -1 && bootCount != -1 && currentBoot != bootCount) ||
            nowElapsed < startElapsed
        if (!rebooted) return this
        val clockNow = if (pausedAt != null) lastClock else lastClock + (nowWall - lastWall).coerceAtLeast(0)
        return copy(
            startElapsed = nowElapsed - clockNow,
            pausedTotalMs = 0,
            pausedAt = if (pausedAt != null) nowElapsed else null,
            bootCount = currentBoot
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("format", "galvatech-time-motion/1")
        .put("id", id).put("analysis", analysis.toJson())
        .put("observer", observer).put("shift", shift)
        .put("labourers", JSONArray().apply { labourers.forEach { put(it.toJson()) } })
        .put("startedAtWall", startedAtWall).put("startElapsed", startElapsed)
        .put("bootCount", bootCount).put("pausedTotalMs", pausedTotalMs)
        .put("pausedAt", pausedAt ?: JSONObject.NULL)
        .put("endT", endT ?: JSONObject.NULL)
        .put("lastWall", lastWall).put("lastClock", lastClock)
        .put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Study {
            val labs = o.getJSONArray("labourers")
            val evs = o.getJSONArray("events")
            return Study(
                id = o.getString("id"),
                analysis = JobAnalysis.fromJson(o.getJSONObject("analysis")),
                observer = o.optString("observer", ""),
                shift = o.optString("shift", ""),
                labourers = (0 until labs.length()).map { Labourer.fromJson(labs.getJSONObject(it)) },
                startedAtWall = o.getLong("startedAtWall"),
                startElapsed = o.getLong("startElapsed"),
                bootCount = o.optInt("bootCount", -1),
                pausedTotalMs = o.optLong("pausedTotalMs", 0),
                pausedAt = if (o.isNull("pausedAt")) null else o.getLong("pausedAt"),
                endT = if (o.isNull("endT")) null else o.getLong("endT"),
                events = (0 until evs.length()).map { StudyEvent.fromJson(evs.getJSONObject(it)) },
                lastWall = o.optLong("lastWall", 0),
                lastClock = o.optLong("lastClock", 0)
            )
        }
    }
}
