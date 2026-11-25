package za.co.galvatech.timemotion.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import za.co.galvatech.timemotion.export.XlsxWriter.Bold
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.EventType
import za.co.galvatech.timemotion.model.PersonResult
import za.co.galvatech.timemotion.model.Study
import za.co.galvatech.timemotion.model.crew
import za.co.galvatech.timemotion.model.fmt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Exporter {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun dateText(study: Study): String =
        SimpleDateFormat("d MMM yyyy HH:mm", Locale.UK).format(Date(study.startedAtWall))

    private fun baseName(study: Study): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(study.startedAtWall))
        val name = study.analysis.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40)
        return "${name}_$date"
    }

    private fun min(ms: Long): Double = ms / 60000.0
    private fun sec(ms: Long): Long = (ms + 500) / 1000
    private fun r1(d: Double): Double = (d * 10).roundToInt() / 10.0

    // ---------- Excel ----------
    fun xlsx(ctx: Context, study: Study, results: List<PersonResult>): File {
        val c = crew(results)
        val unit = study.analysis.unitName
        val w = XlsxWriter()

        val summary = mutableListOf<List<Any?>>(
            listOf(Bold("Time & motion study"), study.analysis.name),
            listOf("Description", study.analysis.description),
            listOf("Date", dateText(study)),
            listOf("Observer", study.observer),
            listOf("Shift", study.shift),
            listOf("Study length (min)", min(study.endT ?: 0L)),
            listOf("Unit counted", unit),
            listOf(),
            listOf(
                Bold("Labourer"), Bold("Clock no"), Bold("Observed (min)"),
                Bold("VA (min)"), Bold("NNVA (min)"), Bold("NVA (min)"),
                Bold("VA %"), Bold("NNVA %"), Bold("NVA %"),
                Bold("$unit count"), Bold("Min per $unit"), Bold("VA min per $unit")
            )
        )
        results.forEach { r ->
            summary.add(listOf(
                r.labourer.name, r.labourer.clockNo, min(r.totalMs),
                min(r.ms(Category.VA)), min(r.ms(Category.NNVA)), min(r.ms(Category.NVA)),
                r1(r.pct(Category.VA)), r1(r.pct(Category.NNVA)), r1(r.pct(Category.NVA)),
                r.units, r.msPerUnit?.let { min(it) }, r.vaMsPerUnit?.let { min(it) }
            ))
        }
        summary.add(listOf(
            Bold("Crew"), "", min(c.totalMs),
            min(c.ms(Category.VA)), min(c.ms(Category.NNVA)), min(c.ms(Category.NVA)),
            r1(c.pct(Category.VA)), r1(c.pct(Category.NNVA)), r1(c.pct(Category.NVA)),
            c.units, c.msPerUnit?.let { min(it) }, null
        ))
        w.sheet("Summary", summary)

        val motions = mutableListOf<List<Any?>>(
            listOf(Bold("Labourer"), Bold("Motion"), Bold("Category"), Bold("Count"),
                Bold("Total (min)"), Bold("Average (sec)"), Bold("% of observed"))
        )
        results.forEach { r ->
            r.motions.forEach { m ->
                motions.add(listOf(
                    r.labourer.name, m.motion.name, m.motion.category.label, m.count,
                    min(m.totalMs), sec(m.avgMs),
                    if (r.totalMs > 0) r1(m.totalMs * 100.0 / r.totalMs) else 0.0
                ))
            }
        }
        w.sheet("Motions", motions)

        val timeline = mutableListOf<List<Any?>>(
            listOf(Bold("Labourer"), Bold("Event"), Bold("Motion"), Bold("Category"),
                Bold("Start (sec)"), Bold("End (sec)"), Bold("Duration (sec)"), Bold("Start (mm:ss)"))
        )
        results.forEach { r ->
            val rows = mutableListOf<Pair<Long, List<Any?>>>()
            r.segments.forEach { s ->
                rows.add(s.start to listOf(
                    r.labourer.name, "Motion", s.motion.name, s.motion.category.label,
                    sec(s.start), sec(s.end), sec(s.dur), fmt(s.start)
                ))
            }
            study.events.filter { it.type == EventType.UNIT && it.labourer == r.index }.forEach { e ->
                rows.add(e.t to listOf(r.labourer.name, "+1 $unit", "", "", sec(e.t), null, null, fmt(e.t)))
            }
            timeline.addAll(rows.sortedBy { it.first }.map { it.second })
        }
        w.sheet("Timeline", timeline)

        val f = File(dir(ctx), baseName(study) + ".xlsx")
        w.write(f)
        return f
    }

    // ---------- Study file for the PC app ----------
    fun studyJson(ctx: Context, study: Study): File {
        val f = File(dir(ctx), baseName(study) + ".tmstudy.json")
        f.writeText(study.toJson().toString(2))
        return f
    }

    // ---------- PDF ----------
    private fun catColor(c: Category) = when (c) {
        Category.VA -> Color.rgb(15, 118, 110)
        Category.NNVA -> Color.rgb(232, 163, 23)
        Category.NVA -> Color.rgb(194, 65, 12)
    }

    fun pdf(ctx: Context, study: Study, results: List<PersonResult>): File {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val m = 40f
        val contentW = pageW - 2 * m
        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var cv: Canvas
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            cv = p.canvas
            y = m
        }

        fun need(h: Float) {
            if (y + h > pageH - m) newPage()
        }

        val ink = Color.rgb(17, 24, 39)
        val muted = Color.rgb(75, 85, 99)
        val title = Paint().apply { isAntiAlias = true; color = ink; textSize = 18f; isFakeBoldText = true }
        val h2 = Paint().apply { isAntiAlias = true; color = ink; textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { isAntiAlias = true; color = ink; textSize = 9.5f }
        val small = Paint().apply { isAntiAlias = true; color = muted; textSize = 8.5f }
        val bold = Paint(body).apply { isFakeBoldText = true }
        val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

        fun line(text: String, p: Paint, gap: Float = 4f) {
            need(p.textSize + gap)
            y += p.textSize
            cv.drawText(text, m, y, p)
            y += gap
        }

        fun bar(r: PersonResult, h: Float) {
            need(h + 4)
            var x = m
            Category.entries.forEach { c ->
                val w = contentW * r.share(c)
                if (w > 0) {
                    fill.color = catColor(c)
                    cv.drawRect(RectF(x, y, x + w, y + h), fill)
                    x += w
                }
            }
            if (r.totalMs == 0L) {
                fill.color = Color.rgb(229, 231, 235)
                cv.drawRect(RectF(m, y, m + contentW, y + h), fill)
            }
            y += h + 4
        }

        fun timeline(r: PersonResult, h: Float, end: Long) {
            need(h + 4)
            if (end > 0) r.segments.forEach { s ->
                val x0 = m + contentW * s.start / end
                val x1 = m + contentW * s.end / end
                fill.color = catColor(s.motion.category)
                cv.drawRect(RectF(x0, y, x1, y + h), fill)
            }
            y += h + 4
        }

        newPage()
        val c = crew(results)
        val unit = study.analysis.unitName
        line("Time & motion study – ${study.analysis.name}", title, 6f)
        if (study.analysis.description.isNotBlank()) line(study.analysis.description, small)
        line("${dateText(study)}   ·   Study length ${fmt(study.endT ?: 0L)}   ·   Observer: ${study.observer.ifBlank { "-" }}   ·   Shift: ${study.shift}", small, 10f)

        line("Crew summary", h2, 6f)
        line(
            "VA ${c.pct(Category.VA).roundToInt()}%   NNVA ${c.pct(Category.NNVA).roundToInt()}%   NVA ${c.pct(Category.NVA).roundToInt()}%" +
                "   ·   $unit count: ${c.units}" +
                (c.msPerUnit?.let { "   ·   Labour time per $unit: ${fmt(it)}" } ?: ""),
            body, 6f
        )
        line("Colours: teal = VA (value add), amber = NNVA (necessary non-value add), orange-red = NVA (waste)", small, 12f)

        val end = study.endT ?: 0L
        results.forEach { r ->
            need(150f)
            line("${r.labourer.name}${if (r.labourer.clockNo.isNotBlank()) " (${r.labourer.clockNo})" else ""}", h2, 5f)
            line(
                "Observed ${fmt(r.totalMs)}   ·   VA ${r.pct(Category.VA).roundToInt()}% (${fmt(r.ms(Category.VA))})" +
                    "   NNVA ${r.pct(Category.NNVA).roundToInt()}% (${fmt(r.ms(Category.NNVA))})" +
                    "   NVA ${r.pct(Category.NVA).roundToInt()}% (${fmt(r.ms(Category.NVA))})",
                body
            )
            line(
                "$unit count: ${r.units}" + (r.msPerUnit?.let { "   ·   ${fmt(it)} per $unit" } ?: "") +
                    (r.vaMsPerUnit?.let { "   ·   VA ${fmt(it)} per $unit" } ?: ""),
                body, 5f
            )
            bar(r, 14f)
            line("Timeline", small, 2f)
            timeline(r, 9f, end)

            // Motion table
            val cols = floatArrayOf(m, m + 230f, m + 290f, m + 340f, m + 410f, m + 470f)
            need(16f)
            y += 11f
            listOf("Motion", "Cat.", "Count", "Total", "Average", "% time").forEachIndexed { i, s ->
                cv.drawText(s, cols[i], y, bold)
            }
            y += 4f
            r.motions.forEach { ms ->
                need(13f)
                y += 11f
                fill.color = catColor(ms.motion.category)
                cv.drawRect(RectF(m, y - 7f, m + 6f, y - 1f), fill)
                cv.drawText(ms.motion.name, m + 10f, y, body)
                cv.drawText(ms.motion.category.label, cols[1], y, body)
                cv.drawText(ms.count.toString(), cols[2], y, body)
                cv.drawText(fmt(ms.totalMs), cols[3], y, body)
                cv.drawText(fmt(ms.avgMs), cols[4], y, body)
                val p = if (r.totalMs > 0) (ms.totalMs * 1000 / r.totalMs) / 10.0 else 0.0
                cv.drawText(String.format(Locale.US, "%.1f%%", p), cols[5], y, body)
                y += 2f
            }
            y += 16f
        }

        page?.let { doc.finishPage(it) }
        val f = File(dir(ctx), baseName(study) + ".pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    // ---------- Share via WhatsApp, email, Drive, etc. ----------
    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Send ${file.name}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
