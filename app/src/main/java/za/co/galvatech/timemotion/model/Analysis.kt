package za.co.galvatech.timemotion.model

data class Segment(val motion: Motion, val start: Long, val end: Long) {
    val dur: Long get() = end - start
}

data class MotionStat(val motion: Motion, val count: Int, val totalMs: Long) {
    val avgMs: Long get() = if (count > 0) totalMs / count else 0
}

data class PersonResult(
    val index: Int,
    val labourer: Labourer,
    val totalMs: Long,
    val byCat: Map<Category, Long>,
    val motions: List<MotionStat>,
    val units: Int,
    val segments: List<Segment>
) {
    fun ms(c: Category): Long = byCat[c] ?: 0L
    fun share(c: Category): Float = if (totalMs > 0) ms(c).toFloat() / totalMs else 0f
    fun pct(c: Category): Double = if (totalMs > 0) ms(c) * 100.0 / totalMs else 0.0
    val msPerUnit: Long? get() = if (units > 0) totalMs / units else null
    val vaMsPerUnit: Long? get() = if (units > 0) ms(Category.VA) / units else null
}

data class CrewSummary(
    val totalMs: Long,
    val byCat: Map<Category, Long>,
    val units: Int
) {
    fun ms(c: Category): Long = byCat[c] ?: 0L
    fun pct(c: Category): Double = if (totalMs > 0) ms(c) * 100.0 / totalMs else 0.0
    val msPerUnit: Long? get() = if (units > 0) totalMs / units else null
}

/** Turns the tap log into continuous segments per labourer and totals them. */
fun analyse(study: Study, nowElapsed: Long): List<PersonResult> {
    val end = study.clock(nowElapsed)
    return study.labourers.mapIndexed { i, lab ->
        val evs = study.events
            .filter { it.type == EventType.MOTION && it.labourer == i }
            .sortedBy { it.t }
        val segs = evs.mapIndexedNotNull { k, e ->
            val m = study.analysis.motion(e.motionId) ?: return@mapIndexedNotNull null
            val stop = if (k + 1 < evs.size) evs[k + 1].t else end
            if (stop > e.t) Segment(m, e.t, stop) else null
        }
        val byCat = Category.entries.associateWith { c ->
            segs.filter { it.motion.category == c }.sumOf { it.dur }
        }
        val stats = study.analysis.motions.map { m ->
            val s = segs.filter { it.motion.id == m.id }
            MotionStat(m, s.size, s.sumOf { it.dur })
        }
        PersonResult(i, lab, segs.sumOf { it.dur }, byCat, stats, study.units(i), segs)
    }
}

fun crew(results: List<PersonResult>): CrewSummary = CrewSummary(
    totalMs = results.sumOf { it.totalMs },
    byCat = Category.entries.associateWith { c -> results.sumOf { it.ms(c) } },
    units = results.sumOf { it.units }
)
