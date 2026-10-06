package org.cognisense.core.tasks

import org.cognisense.core.engine.CustomRunner
import org.cognisense.core.engine.EndMode
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.RunContext
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialPlan
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import org.cognisense.core.scoring.Stats
import org.cognisense.core.scoring.TimingClass

/**
 * T6 — Spatial span (Corsi-type), forward or backward. Specification: docs/task_specifications.md §5 T6.
 * Forward and backward are two task definitions (SPF, SPB) run in sequence, each with its own
 * instructions and practice. All parameters, the board layout and the fixed sequences come from
 * config/tasks.json.
 *
 * Trial: board shown -> squares light one at a time -> recall cue (frame) -> child taps squares; each
 * tap echoes for a moment (registration only, identical for right and wrong taps) -> trial ends when
 * the number of taps equals the sequence length (or on timeout). Scored phase is adaptive: two
 * sequences per length; advance if >= 1 correct; stop when both are wrong or at the maximum length.
 * An interrupted trial is logged and the same sequence is re-administered after resume.
 */
data class SpanConfig(
    val id: String,
    val version: String,
    val backward: Boolean,
    val board: String,
    val squares: Int,
    val fixationBeforeMs: Int,
    val highlightMs: Int,
    val interHighlightMs: Int,
    val cueDelayMs: Int,
    val echoMs: Int,
    val recallTimeoutMs: Int,
    val itiMs: Int,
    val startLength: Int,
    val maxLength: Int,
    val trialsPerLength: Int,
    val sequences: Map<Int, List<List<Int>>>,
    val practiceSequences: List<List<Int>>,
    val practiceMinCorrect: Int,
) {
    init {
        require(startLength in 2..maxLength && maxLength <= squares) { "bad span lengths" }
        for (len in startLength..maxLength) {
            val seqs = requireNotNull(sequences[len]) { "no sequences for length $len" }
            require(seqs.size >= trialsPerLength) { "need $trialsPerLength sequences of length $len" }
            seqs.forEach { require(it.size == len && it.toSet().size == len && it.all { s -> s in 0 until squares }) { "bad sequence $it" } }
        }
        require(practiceSequences.isNotEmpty() && practiceMinCorrect <= practiceSequences.size)
        practiceSequences.forEach { require(it.toSet().size == it.size && it.all { s -> s in 0 until squares }) }
        val scored = sequences.values.flatten().toSet()
        require(practiceSequences.none { it in scored }) { "a practice sequence duplicates a scored sequence" }
    }
}

/** How the fixed sequences in config/tasks.json were generated. Tests check the config still matches. */
object SpanSequences {
    fun generate(seed: Long, label: String, minLen: Int, maxLen: Int, perLength: Int, squares: Int): Map<Int, List<List<Int>>> {
        val rng = SeededRandom.derive(seed, label)
        return (minLen..maxLen).associateWith { len ->
            List(perLength) { (0 until squares).toMutableList().also { rng.shuffle(it) }.take(len) }
        }
    }

    /** Practice sequences: drawn from their own stream, skipping any sequence used in scored trials. */
    fun generatePractice(seed: Long, label: String, count: Int, len: Int, squares: Int, exclude: Set<List<Int>>): List<List<Int>> {
        val rng = SeededRandom.derive(seed, label)
        val out = mutableListOf<List<Int>>()
        while (out.size < count) {
            val s = (0 until squares).toMutableList().also { rng.shuffle(it) }.take(len)
            if (s !in exclude && s !in out) out += s
        }
        return out
    }
}

class SpatialSpanTask(val config: SpanConfig) : TaskDefinition {
    override val id = config.id
    override val version = config.version

    override fun createRun(ctx: RunContext): TaskRun = SpanRunner(this, ctx)

    override fun practiceCriterionMet(practice: List<TrialRecord>): Boolean =
        practice.count { !it.interrupted && it.outcome == Outcome.CORRECT } >= config.practiceMinCorrect

    override fun score(scored: List<TrialRecord>): List<Metric> {
        val valid = scored.filter { !it.interrupted }
        val correct = valid.filter { it.outcome == Outcome.CORRECT }
        val span = correct.maxOfOrNull { it.details.getValue("length").toInt() } ?: 0
        val latencies = valid.mapNotNull { it.details["first_tap_latency_ms"]?.toDoubleOrNull() }
        return listOf(
            Metric("span", span.toDouble(), "items", TimingClass.A, valid.size, "longest length with >= 1 correct; 0 if none"),
            Metric("total_correct", correct.size.toDouble(), "trials", TimingClass.A, valid.size),
            Metric("product_score", (span * correct.size).toDouble(), "span x total correct", TimingClass.A, valid.size,
                "Kessels et al. (2000) [VERIFY]"),
            Metric("trials_administered", valid.size.toDouble(), "trials", TimingClass.A, valid.size),
            Metric("omissions", valid.count { it.outcome == Outcome.OMISSION }.toDouble(), "count", TimingClass.A, valid.size,
                "recall timed out"),
            Metric("median_first_tap_latency", Stats.median(latencies), "ms", TimingClass.C, latencies.size, "exploratory"),
            Metric("interrupted_trials", scored.count { it.interrupted }.toDouble(), "count", TimingClass.A, scored.size),
            Metric("dropped_frames", scored.sumOf { it.droppedFrames }.toDouble(), "count", TimingClass.A, scored.size),
        )
    }
}

class SpanRunner(task: SpatialSpanTask, ctx: RunContext) : CustomRunner(task, ctx) {
    private val c = task.config
    private enum class S { IDLE, FIX, PRESENT, CUE_DELAY, RECALL, FEEDBACK, ITI }

    private var s = S.IDLE
    // schedule
    private var length = c.startLength
    private var slot = 0
    private var correctAtLength = 0
    private var practiceIdx = 0
    private var done = false
    private var trialIndex = 0
    // current trial
    private var seq: List<Int> = emptyList()
    private var seqNo = 0L
    private var trialStart = 0L
    private var onset: Long? = null
    private var presentationEnd: Long? = null
    private var cue: Long? = null
    private val taps = mutableListOf<ResponseEvent>()
    private var premature = 0
    private var extra = 0
    private var offTarget = 0
    private var stimFrames = 0
    private var stateStart = 0L
    private var lastCorrect = false
    /** The child may have seen part of a sequence before an interruption; its re-administration is flagged. */
    private var readminister = false

    private val slotMs get() = c.highlightMs + c.interHighlightMs

    private fun nextSequence(): List<Int>? = when {
        done -> null
        phase == Phase.PRACTICE -> c.practiceSequences.getOrNull(practiceIdx)
        else -> c.sequences[length]?.getOrNull(slot)
    }

    override fun step(ft: Long): FrameContent {
        var guard = 0
        while (advance(ft)) if (++guard > 16) error("span runner did not settle")
        return content(ft)
    }

    private fun advance(ft: Long): Boolean {
        when (s) {
            S.IDLE -> {
                val next = nextSequence()
                if (next == null) { finish(ft); return false }
                seq = next; seqNo = ctx.seqBase + trialIndex; trialStart = ft
                onset = null; presentationEnd = null; cue = null
                taps.clear(); premature = 0; extra = 0; offTarget = 0; stimFrames = 0
                arm(seqNo)
                s = S.FIX
                return true
            }
            S.FIX -> if (due(ft, trialStart + ms(c.fixationBeforeMs))) {
                onset = ft; markOnset(seqNo, ft); s = S.PRESENT; return true
            }
            S.PRESENT -> if (due(ft, onset!! + ms((seq.size - 1) * slotMs + c.highlightMs))) {
                presentationEnd = ft; s = S.CUE_DELAY; return true
            }
            S.CUE_DELAY -> if (due(ft, presentationEnd!! + ms(c.cueDelayMs))) {
                cue = ft; s = S.RECALL; return true
            }
            S.RECALL -> {
                val last = taps.lastOrNull()
                if (taps.size >= seq.size && last != null && ft >= last.eventTimeNanos + ms(c.echoMs)) {
                    endTrial(ft, timedOut = false, interrupted = false); return true
                }
                if (taps.size < seq.size && due(ft, cue!! + ms(c.recallTimeoutMs))) {
                    endTrial(ft, timedOut = true, interrupted = false); return true
                }
            }
            S.FEEDBACK -> if (due(ft, stateStart + ms(ctx.common.practiceFeedbackMs))) {
                s = S.ITI; stateStart = ft; return true
            }
            S.ITI -> if (due(ft, stateStart + ms(c.itiMs))) { s = S.IDLE; return true }
        }
        return false
    }

    private fun litItem(ft: Long): Int? {
        val o = onset ?: return null
        for (j in seq.indices) {
            val on = o + ms(j * slotMs)
            if (due(ft, on) && !due(ft, on + ms(c.highlightMs))) return seq[j]
        }
        return null
    }

    private fun content(ft: Long): FrameContent = when (s) {
        S.IDLE, S.ITI -> FrameContent(fixation = true, showPads = false)
        S.FIX, S.CUE_DELAY -> FrameContent(board = c.board, showPads = false)
        S.PRESENT -> {
            val lit = litItem(ft)
            if (lit != null) stimFrames++
            FrameContent(board = c.board, highlight = lit, markerOn = lit != null, showPads = false)
        }
        S.RECALL -> {
            val last = taps.lastOrNull()
            val echo = if (last != null && ft < last.eventTimeNanos + ms(c.echoMs)) last.target else null
            FrameContent(board = c.board, responseCue = true, echo = echo, showPads = false)
        }
        S.FEEDBACK -> FrameContent(feedback = lastCorrect, showPads = false)
    }

    override fun touch(event: ResponseEvent) {
        if (event.release) return
        when (s) {
            S.RECALL -> {
                val cueT = cue!!
                when {
                    event.eventTimeNanos < cueT -> premature++
                    event.target == null -> offTarget++
                    taps.size >= seq.size -> extra++
                    else -> {
                        taps += event
                        if (taps.size == 1) markResponse(seqNo, event.eventTimeNanos)
                    }
                }
            }
            S.FIX, S.PRESENT, S.CUE_DELAY -> premature++
            S.IDLE, S.FEEDBACK, S.ITI -> unassignedResponses++
        }
    }

    override fun interruptCurrent(timeNanos: Long) {
        if (s in setOf(S.FIX, S.PRESENT, S.CUE_DELAY, S.RECALL)) endTrial(timeNanos, timedOut = false, interrupted = true)
        s = S.IDLE
    }

    private fun endTrial(end: Long, timedOut: Boolean, interrupted: Boolean) {
        val expected = if (c.backward) seq.reversed() else seq
        val tapped = taps.mapNotNull { it.target }
        val outcome = when {
            interrupted -> Outcome.INTERRUPTED
            timedOut -> Outcome.OMISSION
            tapped == expected -> Outcome.CORRECT
            else -> Outcome.INCORRECT
        }
        val first = taps.firstOrNull()
        val cueT = cue
        val plan = TrialPlan(
            index = trialIndex, block = 1, blockCondition = if (c.backward) "backward" else "forward",
            condition = "len${seq.size}", stimulus = c.board, expectedResponse = null,
            foreperiodMs = c.fixationBeforeMs, fixationInForeperiod = false, stimulusMs = null,
            responseWindowMs = c.recallTimeoutMs, endMode = EndMode.CUSTOM, endMs = c.itiMs, fixationAfterStimulus = true,
        )
        addRecord(
            plan, seqNo, trialStart, onset, presentationEnd, end, stimFrames, first, rtMs = null, outcome = outcome,
            premature = premature, extra = extra, late = 0, offTarget = offTarget,
            details = mapOf(
                "length" to seq.size.toString(),
                "slot" to (if (phase == Phase.PRACTICE) practiceIdx else slot).toString(),
                "sequence" to seq.joinToString("-"),
                "expected" to expected.joinToString("-"),
                "taps" to tapped.joinToString("-"),
                "tap_latencies_ms" to (if (cueT == null) "" else taps.joinToString(";") { fmt((it.eventTimeNanos - cueT) / 1e6) }),
                "first_tap_latency_ms" to (if (first != null && cueT != null) fmt((first.eventTimeNanos - cueT) / 1e6) else ""),
                "readministered_after_interruption" to (readminister && !interrupted).toString(),
            ),
        )
        trialIndex++
        lastCorrect = outcome == Outcome.CORRECT
        readminister = interrupted
        if (!interrupted) {
            if (phase == Phase.PRACTICE) {
                practiceIdx++
            } else {
                if (lastCorrect) correctAtLength++
                slot++
                if (slot >= c.trialsPerLength) {
                    if (correctAtLength == 0 || length >= c.maxLength) done = true
                    else { length++; slot = 0; correctAtLength = 0 }
                }
            }
            s = if (phase == Phase.PRACTICE) S.FEEDBACK else S.ITI
            stateStart = end
        }
    }
}
