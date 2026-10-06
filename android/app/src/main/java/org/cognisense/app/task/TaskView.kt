package org.cognisense.app.task

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.MotionEvent
import android.view.View
import org.cognisense.app.PadLayout
import org.cognisense.app.ui.Lang
import org.cognisense.app.ui.Strings
import org.cognisense.app.ui.Ui
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.ResponseKey
import kotlin.math.hypot

/**
 * The only view shown during trials. It draws exactly the FrameContent the task returns, on the
 * configured neutral background, and turns touches into timestamped ResponseEvents.
 *
 * - Layout direction forced to LTR: LEFT is always the physical left pad, in any language.
 * - No click sounds, no haptics, no animation.
 * - Touch-downs and finger lifts are both reported (time reproduction needs lifts); each carries the
 *   MotionEvent timestamp (ns on API 34+, ms before) and the pointer id.
 * - Geometry (pads, board, stimuli) comes from config/tasks.json via [StimulusPainter].
 */
@SuppressLint("ViewConstructor")
class TaskView(
    ctx: Context,
    private val lang: Lang,
    private val pads: PadLayout,
    private val markerEnabled: Boolean,
    private val breakTotalMs: Long,
    private val painter: StimulusPainter,
    /** Board id for spatial span; null for other tasks. */
    private val boardId: String?,
) : View(ctx) {
    var onResponse: ((ResponseEvent) -> Unit)? = null
    var onContinueTap: (() -> Unit)? = null
    var onResumeTap: (() -> Unit)? = null

    private val d = painter.density
    private var content = FrameContent(fixation = true, showPads = false)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = painter.textColor; textSize = painter.textSizePx; textAlign = Paint.Align.CENTER; textLocale = Ui.locale(lang)
    }
    private val barBg = Paint().apply { color = Color.rgb(105, 105, 105) }
    private val barFg = Paint().apply { color = Color.rgb(40, 40, 40) }

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        setBackgroundColor(painter.background)
        keepScreenOn = true
    }

    /** Called from the frame callback; invalidating here draws in the same frame. */
    fun render(c: FrameContent) {
        if (c != content) { content = c; invalidate() }
    }

    private fun padCentres(): List<Triple<ResponseKey, Float, Float>> {
        val r = painter.padRadius
        val y = height - painter.padBottomMargin - r
        return when (pads) {
            PadLayout.SINGLE -> listOf(Triple(ResponseKey.SINGLE, width / 2f, y))
            PadLayout.LEFT_RIGHT -> listOf(
                Triple(ResponseKey.LEFT, painter.padSideMargin + r, y),
                Triple(ResponseKey.RIGHT, width - painter.padSideMargin - r, y),
            )
            PadLayout.BOARD -> emptyList()
        }
    }

    /** Stimulus centre: screen centre for the board; otherwise midway between the top edge and the pads. */
    private fun stimulusY(): Float =
        if (pads == PadLayout.BOARD) height / 2f else (height - painter.padBottomMargin - 2 * painter.padRadius) / 2f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = stimulusY()
        when {
            content.paused -> canvas.drawText(Strings.get("paused", lang), cx, cy, text)
            content.awaitingContinue -> canvas.drawText(Strings.get("tap_continue", lang), cx, cy, text)
            content.breakRemainingMs != null -> {
                canvas.drawText(Strings.get("rest", lang), cx, cy - 30 * d, text)
                val frac = 1f - (content.breakRemainingMs!!.toFloat() / breakTotalMs).coerceIn(0f, 1f)
                val bw = width * 0.5f; val left = cx - bw / 2; val top = cy + 10 * d
                canvas.drawRect(left, top, left + bw, top + 12 * d, barBg)
                canvas.drawRect(left, top, left + bw * frac, top + 12 * d, barFg)
            }
            content.finished -> {}
            else -> {
                if (content.showPads) for ((_, x, y) in padCentres())
                    painter.pad(canvas, x, y, painter.padRadius, pressed = content.padPressed, cue = content.responseCue)
                content.board?.let { painter.board(canvas, it, cx, cy, content.highlight, content.echo, content.responseCue) }
                if (content.fixation) painter.fixation(canvas, cx, cy)
                content.stimulus?.let { painter.stimulus(canvas, it, cx, cy) }
                content.choice?.let { ch ->
                    val centres = padCentres()
                    val lx = centres.firstOrNull { it.first == ResponseKey.LEFT }?.second ?: (width * 0.25f)
                    val rx = centres.firstOrNull { it.first == ResponseKey.RIGHT }?.second ?: (width * 0.75f)
                    painter.choice(canvas, ch, lx, rx, cx, cy)
                }
                content.durationFeedback?.let { (target, reproduced) -> painter.durationBars(canvas, target, reproduced, cx, cy) }
                content.feedback?.let { painter.feedback(canvas, it, cx, cy) }
            }
        }
        if (markerEnabled) painter.marker(canvas, content.markerOn)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val i = ev.actionIndex
        val x = ev.getX(i); val y = ev.getY(i)
        val pointer = ev.getPointerId(i)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> when {
                content.paused -> onResumeTap?.invoke()
                content.awaitingContinue -> onContinueTap?.invoke()
                else -> onResponse?.invoke(ResponseEvent(eventTimeNanos(ev), padHit(x, y), x, y,
                    target = boardId?.let { painter.boardHit(it, width / 2f, stimulusY(), x, y) }, pointerId = pointer))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                onResponse?.invoke(ResponseEvent(eventTimeNanos(ev), null, x, y, release = true, pointerId = pointer))
            // ACTION_CANCEL is deliberately not converted into a release: a cancelled gesture is not a
            // child's lift. In time reproduction it ends the trial at the maximum hold, flagged.
        }
        return true
    }

    /** Touches within hit_radius_factor x the pad radius count as that pad; anything else is off-target. */
    private fun padHit(x: Float, y: Float): ResponseKey? =
        padCentres().firstOrNull { (_, px, py) -> hypot(x - px, y - py) <= painter.padRadius * painter.padHitFactor }?.first

    companion object {
        /** MotionEvent timestamp on CLOCK_MONOTONIC: ns resolution on API 34+, ms resolution before. */
        fun eventTimeNanos(ev: MotionEvent): Long =
            if (Build.VERSION.SDK_INT >= 34) ev.eventTimeNanos else ev.eventTime * 1_000_000L
    }
}
