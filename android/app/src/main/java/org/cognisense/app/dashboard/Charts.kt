package org.cognisense.app.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.TrialRecord
import kotlin.math.ceil
import kotlin.math.max

/**
 * Dashboard charts. Deliberately neutral: one ink colour, no red/green, no reference lines,
 * shaded bands, norms or cut-offs. They show what was recorded, not how it compares to anyone.
 */
@SuppressLint("ViewConstructor")
abstract class Chart(ctx: Context) : View(ctx) {
    protected val d = ctx.resources.displayMetrics.density
    protected val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(38, 70, 83); style = Paint.Style.FILL }
    protected val hollow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 70, 83); style = Paint.Style.STROKE; strokeWidth = 1.5f * d
    }
    protected val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 120, 120); strokeWidth = 1f * d }
    protected val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 80, 80); textSize = 11f * d }
    protected val left get() = 44f * d
    protected val bottom get() = height - 22f * d
    protected val top get() = 8f * d
    protected val right get() = width - 8f * d

    init { setBackgroundColor(Color.WHITE) }

    protected fun axes(c: Canvas, yMaxLabel: String, yMinLabel: String, xLabel: String) {
        c.drawLine(left, top, left, bottom, axis); c.drawLine(left, bottom, right, bottom, axis)
        c.drawText(yMaxLabel, 4f * d, top + 10f * d, label)
        c.drawText(yMinLabel, 4f * d, bottom, label)
        c.drawText(xLabel, right - label.measureText(xLabel), height - 4f * d, label)
    }
}

/** RT by trial order. Filled = correct response; hollow = any other outcome with an RT. */
class RtSeriesChart(ctx: Context, private val records: List<TrialRecord>) : Chart(ctx) {
    override fun onDraw(c: Canvas) {
        val pts = records.filter { it.rtMs != null }
        val yMax = max(1000.0, ceil((pts.maxOfOrNull { it.rtMs!! } ?: 0.0) / 250.0) * 250.0)
        axes(c, "${yMax.toInt()} ms", "0", "trial ${records.size}")
        if (records.isEmpty()) return
        val n = records.size
        records.forEachIndexed { i, r ->
            val rt = r.rtMs ?: return@forEachIndexed
            val x = left + (right - left) * (i + 0.5f) / n
            val y = bottom - (bottom - top) * (rt / yMax).toFloat()
            c.drawCircle(x, y, 3f * d, if (r.outcome == Outcome.CORRECT) ink else hollow)
        }
    }
}

/** Proportion correct per trial condition (interrupted trials excluded). */
class AccuracyChart(ctx: Context, private val records: List<TrialRecord>) : Chart(ctx) {
    override fun onDraw(c: Canvas) {
        axes(c, "1.0", "0", "")
        val groups = records.filter { !it.interrupted }.groupBy { it.plan.condition }.toSortedMap()
        if (groups.isEmpty()) return
        val slot = (right - left) / groups.size
        groups.entries.forEachIndexed { i, (cond, rs) ->
            val p = rs.count { it.outcome == Outcome.CORRECT }.toFloat() / rs.size
            val x0 = left + slot * i + slot * 0.25f; val x1 = left + slot * (i + 1) - slot * 0.25f
            c.drawRect(x0, bottom - (bottom - top) * p, x1, bottom, ink)
            c.drawText("$cond (n=${rs.size})", x0, height - 4f * d, label)
        }
    }
}

/** Histogram of all recorded RTs in 50 ms bins. */
class RtHistogram(ctx: Context, private val records: List<TrialRecord>) : Chart(ctx) {
    override fun onDraw(c: Canvas) {
        val rts = records.mapNotNull { it.rtMs }
        val maxRt = max(1000.0, ceil((rts.maxOrNull() ?: 0.0) / 50.0) * 50.0)
        val bins = IntArray((maxRt / 50).toInt())
        rts.forEach { rt -> bins[(rt / 50).toInt().coerceIn(0, bins.size - 1)]++ }
        val maxCount = max(1, bins.maxOrNull() ?: 1)
        axes(c, "$maxCount", "0", "${maxRt.toInt()} ms")
        val w = (right - left) / bins.size
        bins.forEachIndexed { i, k ->
            if (k > 0) c.drawRect(left + i * w + 1, bottom - (bottom - top) * k / maxCount, left + (i + 1) * w - 1, bottom, ink)
        }
    }
}
