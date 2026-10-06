package org.cognisense.app.task

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.config.JObj
import org.cognisense.core.engine.ChoiceDisplay
import org.cognisense.core.engine.ChoiceOption
import org.cognisense.core.engine.ChoiceStage
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.tasks.SpatialSpanTask
import org.cognisense.core.tasks.TimeReproductionTask

/**
 * Draws every task-screen element as flat vector shapes. EVERY size, colour and layout value comes
 * from config/tasks.json ("display" and "stimuli"); nothing visual is hard-coded here. Stimulus ids
 * resolve to a definition by exact id, else by the prefix before the first '_' (e.g. "flanker_...").
 */
class StimulusPainter(ctx: Context, cfg: BatteryConfig) {
    val density = ctx.resources.displayMetrics.density
    private val d = density
    private val display = cfg.display
    private val stimuli = cfg.stimuli

    private fun rgb(o: JObj, k: String): Int = o.intList(k).let { Color.rgb(it[0], it[1], it[2]) }
    private fun dp(o: JObj, k: String): Float = o.num(k).toFloat() * d
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    val background: Int = rgb(display, "background_rgb")
    val textColor: Int = rgb(display, "text_rgb")
    val textSizePx: Float = dp(display, "text_sp")

    private val fix = display.obj("fixation")
    private val fixPaint = fill(rgb(fix, "rgb"))
    private val pads = display.obj("pads")
    val padRadius: Float = dp(pads, "radius_dp")
    val padSideMargin: Float = dp(pads, "side_margin_dp")
    val padBottomMargin: Float = dp(pads, "bottom_margin_dp")
    val padHitFactor: Float = pads.num("hit_radius_factor").toFloat()
    private val padFill = fill(rgb(pads, "fill_rgb"))
    private val padPressedFill = fill(rgb(pads, "pressed_rgb"))
    private val padRing = stroke(rgb(pads, "ring_rgb"), dp(pads, "ring_dp"))
    private val padCueRing = stroke(rgb(pads, "cue_ring_rgb"), dp(pads, "cue_ring_dp"))
    private val fb = display.obj("feedback")
    private val fbPaint = stroke(rgb(fb, "rgb"), dp(fb, "stroke_dp"))
    private val marker = display.obj("onset_marker")
    private val markerPaint = Paint().apply { style = Paint.Style.FILL }
    private val missing = fill(Color.MAGENTA)

    private fun def(id: String): JObj? = when {
        stimuli.has(id) -> stimuli.obj(id)
        stimuli.has(id.substringBefore('_')) -> stimuli.obj(id.substringBefore('_'))
        else -> null
    }

    private fun defByShape(shape: String): JObj? =
        stimuli.keys().map { stimuli.obj(it) }.firstOrNull { it.str("shape") == shape }

    fun fixation(c: Canvas, cx: Float, cy: Float) {
        val half = dp(fix, "size_dp") / 2; val w = dp(fix, "stroke_dp") / 2
        c.drawRect(cx - half, cy - w, cx + half, cy + w, fixPaint)
        c.drawRect(cx - w, cy - half, cx + w, cy + half, fixPaint)
    }

    fun stimulus(c: Canvas, id: String, cx: Float, cy: Float) {
        val s = def(id) ?: return missingAsset(c, cx, cy)
        val paint = fill(rgb(s, "rgb"))
        when (s.str("shape")) {
            "circle" -> c.drawCircle(cx, cy, dp(s, "diameter_dp") / 2, paint)
            "square" -> { val h = dp(s, "side_dp") / 2; c.drawRect(cx - h, cy - h, cx + h, cy + h, paint) }
            "arrow_row" -> arrowRow(c, s, id, cx, cy, paint)
            else -> missingAsset(c, cx, cy)
        }
    }

    /** Missing or unknown asset: an obvious magenta block, never a silent blank. */
    private fun missingAsset(c: Canvas, cx: Float, cy: Float) =
        c.drawRect(cx - 40 * d, cy - 40 * d, cx + 40 * d, cy + 40 * d, missing)

    /** "<prefix>_<congruent|incongruent>_<left|right>". */
    private fun arrowRow(c: Canvas, s: JObj, id: String, cx: Float, cy: Float, paint: Paint) {
        val parts = id.split("_")
        val targetLeft = parts.getOrNull(2) == "left"
        val flankLeft = if (parts.getOrNull(1) == "congruent") targetLeft else !targetLeft
        val n = s.int("count"); val w = dp(s, "arrow_width_dp"); val gap = dp(s, "gap_dp"); val h = dp(s, "arrow_height_dp")
        val total = n * w + (n - 1) * gap
        for (i in 0 until n) {
            val left = cx - total / 2 + i * (w + gap)
            arrow(c, left + w / 2, cy, w, h, s.num("shaft_height_fraction").toFloat(), if (i == n / 2) targetLeft else flankLeft, paint)
        }
    }

    private fun arrow(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, shaft: Float, pointsLeft: Boolean, paint: Paint) {
        val sgn = if (pointsLeft) -1f else 1f
        val sh = h * shaft
        val p = Path().apply {
            moveTo(cx - sgn * w / 2, cy - sh / 2); lineTo(cx + sgn * w * 0.02f, cy - sh / 2)
            lineTo(cx + sgn * w * 0.02f, cy - h / 2); lineTo(cx + sgn * w / 2, cy)
            lineTo(cx + sgn * w * 0.02f, cy + h / 2); lineTo(cx + sgn * w * 0.02f, cy + sh / 2)
            lineTo(cx - sgn * w / 2, cy + sh / 2); close()
        }
        c.drawPath(p, paint)
    }

    // --- spatial span board -----------------------------------------------------------------------

    private fun boardGeometry(boardId: String, cx: Float, cy: Float): Triple<JObj, Float, Float> {
        val b = stimuli.obj(boardId)
        return Triple(b, cx - dp(b, "width_dp") / 2, cy - dp(b, "height_dp") / 2)
    }

    private fun squareCentre(b: JObj, left: Float, top: Float, i: Int): Pair<Float, Float> {
        val pos = b.arr("positions")[i] as List<*>
        return (left + (pos[0] as Double).toFloat() * dp(b, "width_dp")) to (top + (pos[1] as Double).toFloat() * dp(b, "height_dp"))
    }

    fun board(c: Canvas, boardId: String, cx: Float, cy: Float, highlight: Int?, echo: Int?, cue: Boolean) {
        val (b, left, top) = boardGeometry(boardId, cx, cy)
        val half = dp(b, "square_dp") / 2
        val idle = fill(rgb(b, "idle_rgb")); val lit = fill(rgb(b, "lit_rgb")); val echoP = fill(rgb(b, "echo_rgb"))
        for (i in 0 until b.arr("positions").size) {
            val (x, y) = squareCentre(b, left, top, i)
            c.drawRect(x - half, y - half, x + half, y + half, when (i) { highlight -> lit; echo -> echoP; else -> idle })
        }
        if (cue) {
            val f = dp(b, "cue_frame_dp")
            c.drawRect(left - f, top - f, left + dp(b, "width_dp") + f, top + dp(b, "height_dp") + f,
                stroke(rgb(b, "cue_frame_rgb"), f))
        }
    }

    /** Index of the board square containing (x, y), with a small tolerance; null if none. */
    fun boardHit(boardId: String, cx: Float, cy: Float, x: Float, y: Float): Int? {
        val (b, left, top) = boardGeometry(boardId, cx, cy)
        val half = dp(b, "square_dp") / 2 + 8 * d
        return (0 until b.arr("positions").size).firstOrNull { i ->
            val (sx, sy) = squareCentre(b, left, top, i)
            x in (sx - half)..(sx + half) && y in (sy - half)..(sy + half)
        }
    }

    // --- choice-delay -----------------------------------------------------------------------------

    /** Options are drawn directly above their pads ([leftX], [rightX]); reward tokens at [cx]. */
    fun choice(c: Canvas, cd: ChoiceDisplay, leftX: Float, rightX: Float, cx: Float, cy: Float) {
        val s = defByShape("choice_panels") ?: return missingAsset(c, cx, cy)
        if (cd.stage == ChoiceStage.REWARD) { tokens(c, s, cd.rewardTokens, cx, cy, filled = true, active = true); return }
        cd.left?.let { option(c, s, it, leftX, cy, cd.stage) }
        cd.right?.let { option(c, s, it, rightX, cy, cd.stage) }
    }

    private fun option(c: Canvas, s: JObj, o: ChoiceOption, x: Float, cy: Float, stage: ChoiceStage) {
        tokens(c, s, o.tokens, x, cy - 20 * d, filled = stage == ChoiceStage.CHOOSE, active = o.active)
        val len = dp(s, if (o.longDelay) "long_bar_dp" else "short_bar_dp"); val h = dp(s, "bar_height_dp")
        val barPaint = fill(if (o.active) rgb(s, "bar_rgb") else rgb(s, "inactive_rgb"))
        c.drawRect(x - len / 2, cy + 30 * d, x + len / 2, cy + 30 * d + h, barPaint)
    }

    /** Tokens: filled when offered or received; outlined while waiting (not yet received). */
    private fun tokens(c: Canvas, s: JObj, n: Int, cx: Float, cy: Float, filled: Boolean, active: Boolean) {
        val size = dp(s, "token_dp"); val gap = dp(s, "token_gap_dp")
        val color = if (active) rgb(s, "token_rgb") else rgb(s, "inactive_rgb")
        val paint = if (filled) fill(color) else stroke(color, dp(s, "token_outline_dp"))
        val total = n * size + (n - 1) * gap
        for (i in 0 until n) c.drawCircle(cx - total / 2 + size / 2 + i * (size + gap), cy, size / 2, paint)
    }

    // --- time reproduction practice bars -----------------------------------------------------------

    fun durationBars(c: Canvas, target: Double, reproduced: Double, cx: Float, cy: Float) {
        val s = defByShape("duration_bars") ?: return missingAsset(c, cx, cy)
        val max = dp(s, "max_bar_dp"); val h = dp(s, "bar_height_dp"); val gap = dp(s, "gap_dp")
        val scale = max / maxOf(target, reproduced, 1.0).toFloat()
        val left = cx - max / 2
        c.drawRect(left, cy - h - gap / 2, left + target.toFloat() * scale, cy - gap / 2, fill(rgb(s, "target_rgb")))
        c.drawRect(left, cy + gap / 2, left + reproduced.toFloat() * scale, cy + gap / 2 + h, fill(rgb(s, "reproduced_rgb")))
    }

    // --- shared elements --------------------------------------------------------------------------

    /** Practice-only informational feedback: tick or cross, same colour (no reward colouring). */
    fun feedback(c: Canvas, correct: Boolean, cx: Float, cy: Float) {
        val r = dp(fb, "half_size_dp")
        if (correct) {
            c.drawPath(Path().apply { moveTo(cx - r, cy); lineTo(cx - r / 4, cy + r * 0.85f); lineTo(cx + r * 1.15f, cy - r * 0.9f) }, fbPaint)
        } else {
            c.drawLine(cx - r, cy - r, cx + r, cy + r, fbPaint); c.drawLine(cx - r, cy + r, cx + r, cy - r, fbPaint)
        }
    }

    fun pad(c: Canvas, x: Float, y: Float, r: Float, pressed: Boolean, cue: Boolean) {
        c.drawCircle(x, y, r, if (pressed) padPressedFill else padFill)
        c.drawCircle(x, y, r, if (cue) padCueRing else padRing)
    }

    /** Photodiode marker: white while a stimulus is visible, black otherwise; inset so rounded corners cannot clip it. */
    fun marker(c: Canvas, on: Boolean) {
        val inset = dp(marker, "inset_dp"); val size = dp(marker, "size_dp")
        markerPaint.color = if (on) Color.WHITE else Color.BLACK
        c.drawRect(inset, inset, inset + size, inset + size, markerPaint)
    }
}

/**
 * Instruction illustration (shell screens): two wordless panels showing the required action.
 * Drawn in code from the same painter as the task; placeholders until designed illustrations exist.
 */
@SuppressLint("ViewConstructor")
class IllustrationView(
    ctx: Context, private val partId: String,
    private val painter: StimulusPainter, private val def: TaskDefinition,
) : View(ctx) {
    private val panelBg = Paint().apply { color = painter.background }
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5 * painter.density
    }

    override fun onDraw(canvas: Canvas) {
        val d = painter.density
        val gap = 16 * d
        val pw = (width - gap) / 2f
        for (panel in 0..1) {
            val left = panel * (pw + gap)
            canvas.drawRect(left, 0f, left + pw, height.toFloat(), panelBg)
            canvas.save()
            canvas.clipRect(left, 0f, left + pw, height.toFloat())
            val cx = left + pw / 2
            val r = 20 * d; val py = height * 0.80f
            when (partId) {
                "GNG" -> {
                    scaled(canvas, 0.6f, cx, height * 0.35f) { painter.stimulus(canvas, if (panel == 0) "gng_go_circle" else "gng_nogo_square", cx, height * 0.35f) }
                    painter.pad(canvas, cx, py, r, pressed = panel == 0, cue = false)
                    if (panel == 1) canvas.drawLine(cx - r, py + r, cx + r, py - r, slash) // "do not touch"
                }
                "FLK" -> {
                    scaled(canvas, 0.6f, cx, height * 0.35f) {
                        painter.stimulus(canvas, if (panel == 0) "flanker_congruent_right" else "flanker_incongruent_left", cx, height * 0.35f)
                    }
                    val dx = pw * 0.3f; val pressLeft = panel == 1
                    painter.pad(canvas, cx - dx, py, r, pressed = pressLeft, cue = false)
                    painter.pad(canvas, cx + dx, py, r, pressed = !pressLeft, cue = false)
                }
                "SPF", "SPB" -> {
                    val board = (def as SpatialSpanTask).config.board
                    scaled(canvas, 0.4f, cx, height / 2f) {
                        if (panel == 0) painter.board(canvas, board, cx, height / 2f, highlight = 4, echo = null, cue = false)
                        else painter.board(canvas, board, cx, height / 2f, highlight = null, echo = 4, cue = true)
                    }
                }
                "TRP" -> {
                    if (panel == 0) scaled(canvas, 0.6f, cx, height * 0.4f) {
                        painter.stimulus(canvas, (def as TimeReproductionTask).config.stimulus, cx, height * 0.4f)
                    } else painter.pad(canvas, cx, height * 0.55f, 28 * d, pressed = true, cue = true)
                }
                "CDT" -> scaled(canvas, 0.5f, cx, height / 2f) {
                    val span = pw * 0.7f
                    if (panel == 0) painter.choice(canvas, ChoiceDisplay(ChoiceOption(1, false, true), ChoiceOption(2, true, true),
                        ChoiceStage.CHOOSE), cx - span / 2, cx + span / 2, cx, height / 2f)
                    else painter.choice(canvas, ChoiceDisplay(null, null, ChoiceStage.REWARD, rewardTokens = 2), 0f, 0f, cx, height / 2f)
                }
            }
            canvas.restore()
        }
    }

    private inline fun scaled(c: Canvas, s: Float, px: Float, py: Float, block: () -> Unit) {
        c.save(); c.scale(s, s, px, py); block(); c.restore()
    }
}
