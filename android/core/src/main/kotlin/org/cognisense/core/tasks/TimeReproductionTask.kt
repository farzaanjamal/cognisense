package org.cognisense.core.tasks

import org.cognisense.core.engine.CustomRunner
import org.cognisense.core.engine.EndMode
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.RunContext
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialPlan
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import org.cognisense.core.scoring.Stats
import org.cognisense.core.scoring.TimingClass
import kotlin.math.abs

/**
 * T9 — Time reproduction. Specification: docs/task_specifications.md §5 T9.
 * Trial: fixation -> circle shown for target duration D -> pause -> pad appears -> child presses and
 * holds for the same duration -> releases. Reproduced duration R = release - press, both MotionEvent
 * timestamps, so a constant touch latency cancels if press and release latencies are equal (untested).
 * The pad shows a static pressed state while held — never a progress display, which would give
 * duration feedback. Practice only: two bars compare D and R.
 *
 * Outcome mapping (task-specific): CORRECT = completed reproduction; ANTICIPATION = released before
 * the premature-release threshold; INCORRECT = held beyond the maximum hold; OMISSION = no press.
 */
data class TrpConfig(
    val id: String,
    val version: String,
    val stimulus: String,
    val durationsMs: List<Int>,
    val repetitions: Int,
    val maxConsecutiveSame: Int,
    val fixationBeforeMs: Int,
    val pauseBeforeResponseMs: Int,
    val pressTimeoutMs: Int,
    val maxHoldMs: Int,
    val prematureReleaseMs: Int,
    val itiMs: Int,
    val practiceDurationsMs: List<Int>,
    val practiceFeedbackMs: Int,
    val practiceMinRatio: Double,
    val practiceMaxRatio: Double,
) {
    init {
        require(durationsMs.isNotEmpty() && durationsMs.all { it > 0 } && repetitions >= 1)
        require(practiceDurationsMs.isNotEmpty() && practiceMinRatio < practiceMaxRatio)
        require(maxHoldMs > (durationsMs + practiceDurationsMs).max()) { "max hold must exceed the longest duration" }
    }
}

class TimeReproductionTask(val config: TrpConfig) : TaskDefinition {
    override val id = config.id
    override val version = config.version

    override fun createRun(ctx: RunContext): TaskRun {
        val durations = if (ctx.phase == Phase.PRACTICE) config.practiceDurationsMs else {
            val rng = SeededRandom.derive(ctx.sessionSeed, rngLabel(ctx.phase, ctx.attempt))
            val all = config.durationsMs.flatMap { d -> List(config.repetitions) { d } }
            SeededRandom.constrainedShuffle(all, rng) { s -> maxRunOf(s) <= config.maxConsecutiveSame }
        }
        return TrpRunner(this, ctx, durations)
    }

    override fun practiceCriterionMet(practice: List<TrialRecord>): Boolean {
        val valid = practice.filter { !it.interrupted }
        return valid.isNotEmpty() && valid.size == config.practiceDurationsMs.size && valid.all {
            val ratio = it.details["ratio"]?.toDoubleOrNull()
            it.outcome == Outcome.CORRECT && ratio != null && ratio in config.practiceMinRatio..config.practiceMaxRatio
        }
    }

    override fun score(scored: List<TrialRecord>): List<Metric> {
        val ok = scored.filter { it.outcome == Outcome.CORRECT }
        fun d(r: TrialRecord) = r.details.getValue("target_ms").toDouble()
        fun rep(r: TrialRecord) = r.details.getValue("reproduced_ms").toDouble()
        val m = mutableListOf<Metric>()
        for ((dur, rs) in ok.groupBy { d(it) }.entries.sortedBy { it.key }) {
            val reps = rs.map { rep(it) }
            val label = dur.toInt()
            m += Metric("ratio_D$label", Stats.mean(reps.map { it / dur }), "R/D", TimingClass.B, rs.size)
            m += Metric("abs_error_D$label", Stats.mean(reps.map { abs(it - dur) / dur }), "|R-D|/D", TimingClass.B, rs.size)
            m += Metric("precision_D$label", Stats.sd(reps)?.let { sd -> Stats.mean(reps)?.let { sd / it } }, "SD(R)/mean(R)",
                TimingClass.B, rs.size, "R is a difference of two touch timestamps")
        }
        m += Metric("mean_ratio", Stats.mean(ok.map { rep(it) / d(it) }), "R/D", TimingClass.B, ok.size)
        m += Metric("vierordt_slope", olsSlope(ok.map { d(it) }, ok.map { rep(it) }), "ms/ms", TimingClass.B, ok.size,
            "slope of R on D; <1 means long durations under-reproduced relative to short")
        m += Metric("omissions", scored.count { it.outcome == Outcome.OMISSION }.toDouble(), "count", TimingClass.A, scored.size)
        m += Metric("premature_releases", scored.count { it.outcome == Outcome.ANTICIPATION }.toDouble(), "count", TimingClass.A, scored.size)
        m += Metric("held_too_long", scored.count { it.outcome == Outcome.INCORRECT }.toDouble(), "count", TimingClass.A, scored.size)
        m += Metric("interrupted_trials", scored.count { it.interrupted }.toDouble(), "count", TimingClass.A, scored.size)
        m += Metric("dropped_frames", scored.sumOf { it.droppedFrames }.toDouble(), "count", TimingClass.A, scored.size)
        return m
    }

    companion object {
        fun maxRunOf(xs: List<Int>): Int {
            var best = 0; var cur = 0
            for (i in xs.indices) { cur = if (i > 0 && xs[i] == xs[i - 1]) cur + 1 else 1; if (cur > best) best = cur }
            return best
        }

        fun olsSlope(x: List<Double>, y: List<Double>): Double? {
            if (x.size < 2 || x.toSet().size < 2) return null
            val mx = x.average(); val my = y.average()
            val sxy = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
            val sxx = x.sumOf { (it - mx) * (it - mx) }
            return sxy / sxx
        }
    }
}

class TrpRunner(task: TimeReproductionTask, ctx: RunContext, private val durations: List<Int>) : CustomRunner(task, ctx) {
    private val c = task.config
    private enum class S { IDLE, FIX, STIM, PAUSE, WAIT_PRESS, HOLD, FEEDBACK, ITI }

    private var s = S.IDLE
    private var idx = 0
    private var seqNo = 0L
    private var trialStart = 0L
    private var onset: Long? = null
    private var offset: Long? = null
    private var cue: Long? = null
    private var press: ResponseEvent? = null
    private var release: ResponseEvent? = null
    private var premature = 0
    private var extra = 0
    private var offTarget = 0
    private var stimFrames = 0
    private var stateStart = 0L
    private var lastFeedback: Pair<Double, Double>? = null

    private val target get() = durations[idx]

    override fun step(ft: Long): FrameContent {
        var guard = 0
        while (advance(ft)) if (++guard > 16) error("time-reproduction runner did not settle")
        return content()
    }

    private fun advance(ft: Long): Boolean {
        when (s) {
            S.IDLE -> {
                if (idx >= durations.size) { finish(ft); return false }
                seqNo = ctx.seqBase + idx; trialStart = ft
                onset = null; offset = null; cue = null; press = null; release = null
                premature = 0; extra = 0; offTarget = 0; stimFrames = 0
                arm(seqNo); s = S.FIX; return true
            }
            S.FIX -> if (due(ft, trialStart + ms(c.fixationBeforeMs))) { onset = ft; markOnset(seqNo, ft); s = S.STIM; return true }
            S.STIM -> if (due(ft, onset!! + ms(target))) { offset = ft; s = S.PAUSE; return true }
            S.PAUSE -> if (due(ft, offset!! + ms(c.pauseBeforeResponseMs))) { cue = ft; s = S.WAIT_PRESS; return true }
            S.WAIT_PRESS -> {
                if (press != null) { s = S.HOLD; return true }
                if (due(ft, cue!! + ms(c.pressTimeoutMs))) { endTrial(ft, heldTooLong = false, interrupted = false); return true }
            }
            S.HOLD -> {
                if (release != null) { endTrial(ft, heldTooLong = false, interrupted = false); return true }
                if (due(ft, press!!.eventTimeNanos + ms(c.maxHoldMs))) { endTrial(ft, heldTooLong = true, interrupted = false); return true }
            }
            S.FEEDBACK -> if (due(ft, stateStart + ms(c.practiceFeedbackMs))) { s = S.ITI; stateStart = ft; return true }
            S.ITI -> if (due(ft, stateStart + ms(c.itiMs))) { s = S.IDLE; return true }
        }
        return false
    }

    private fun content(): FrameContent = when (s) {
        S.IDLE, S.FIX, S.ITI -> FrameContent(fixation = true, showPads = false)
        S.STIM -> { stimFrames++; FrameContent(stimulus = c.stimulus, markerOn = true, showPads = false) }
        S.PAUSE -> FrameContent(showPads = false)
        S.WAIT_PRESS -> FrameContent(showPads = true, responseCue = true)
        S.HOLD -> FrameContent(showPads = true, responseCue = true, padPressed = true)
        S.FEEDBACK -> FrameContent(durationFeedback = lastFeedback, showPads = false)
    }

    override fun touch(event: ResponseEvent) {
        val p = press
        if (event.release) {
            if (p != null && release == null && event.pointerId == p.pointerId && event.eventTimeNanos >= p.eventTimeNanos) release = event
            return
        }
        when (s) {
            S.WAIT_PRESS -> when {
                p != null -> extra++
                event.eventTimeNanos < cue!! -> premature++
                event.key == ResponseKey.SINGLE -> { press = event; markResponse(seqNo, event.eventTimeNanos) }
                else -> offTarget++
            }
            S.HOLD -> extra++
            S.FIX, S.STIM, S.PAUSE -> premature++
            S.IDLE, S.FEEDBACK, S.ITI -> unassignedResponses++
        }
    }

    override fun interruptCurrent(timeNanos: Long) {
        if (s in setOf(S.FIX, S.STIM, S.PAUSE, S.WAIT_PRESS, S.HOLD)) endTrial(timeNanos, heldTooLong = false, interrupted = true)
        s = S.IDLE
    }

    private fun endTrial(end: Long, heldTooLong: Boolean, interrupted: Boolean) {
        val p = press; val r = release; val cueT = cue
        val reproduced = if (p != null && r != null) (r.eventTimeNanos - p.eventTimeNanos) / 1e6 else null
        val outcome = when {
            interrupted -> Outcome.INTERRUPTED
            p == null -> Outcome.OMISSION
            heldTooLong || r == null -> Outcome.INCORRECT
            reproduced!! < c.prematureReleaseMs -> Outcome.ANTICIPATION
            else -> Outcome.CORRECT
        }
        val plan = TrialPlan(
            index = idx, block = 1, blockCondition = "-", condition = "D$target", stimulus = c.stimulus,
            expectedResponse = ResponseKey.SINGLE, foreperiodMs = c.fixationBeforeMs, fixationInForeperiod = true,
            stimulusMs = target, responseWindowMs = c.pressTimeoutMs, endMode = EndMode.CUSTOM, endMs = c.itiMs,
            fixationAfterStimulus = true,
        )
        val displayed = if (onset != null && offset != null) (offset!! - onset!!) / 1e6 else null
        addRecord(
            plan, seqNo, trialStart, onset, offset, end, stimFrames, p, rtMs = null, outcome = outcome,
            premature = premature, extra = extra, late = 0, offTarget = offTarget,
            details = mapOf(
                "target_ms" to target.toString(),
                "displayed_ms" to (displayed?.let { fmt(it) } ?: ""),
                "press_latency_ms" to (if (p != null && cueT != null) fmt((p.eventTimeNanos - cueT) / 1e6) else ""),
                "reproduced_ms" to (reproduced?.let { fmt(it) } ?: ""),
                "ratio" to (reproduced?.let { fmt(it / target) } ?: ""),
                "flag" to when (outcome) {
                    Outcome.ANTICIPATION -> "premature_release"; Outcome.INCORRECT -> "held_too_long"
                    Outcome.OMISSION -> "no_press"; else -> ""
                },
            ),
        )
        if (!interrupted) {
            if (phase == Phase.PRACTICE) {
                lastFeedback = target.toDouble() to (reproduced ?: 0.0)
                s = S.FEEDBACK
            } else {
                s = S.ITI
            }
            stateStart = end
        }
        idx++
    }
}
