package org.cognisense.core.engine

/**
 * Runs one phase of a fixed-timeline task (Go/No-Go, Flanker, and other timeline tasks).
 *
 * STATUS: IMPLEMENTED (simulation-tested at 60/90/120/144 Hz; not yet run on a device).
 *
 * The engine never reads a clock. It is driven by two inputs, each carrying its own timestamp:
 *   onFrame(frameTimeNanos)  — once per display frame (Choreographer vsync time)
 *   onResponse(event)        — every touch, with the MotionEvent timestamp
 * An event targeted at time T happens on the frame nearest T (first frame >= T - half a frame).
 * Responses are classified by timestamp when the trial ends. All parameters come from
 * [RunContext.common] and the trial plans; nothing is hard-coded here.
 */
class TrialEngine(
    private val task: TaskDefinition,
    private val ctx: RunContext,
    private val plans: List<TrialPlan>,
) : TaskRun {
    private enum class State { IDLE, FOREPERIOD, STIMULUS, POST, FEEDBACK, BREAK, AWAIT_CONTINUE, PAUSED, RESUMING, FINISHED }

    private class Running(val plan: TrialPlan, val seq: Long, val start: Long) {
        var onset: Long? = null
        var offset: Long? = null
        val responses = mutableListOf<ResponseEvent>()
        var offTarget = 0
        var dropped = 0
        var stimulusFrames = 0
    }

    init {
        require(ctx.nominalRefreshHz in 20.0..500.0) { "implausible refresh rate ${ctx.nominalRefreshHz}" }
        require(plans.isNotEmpty())
        require(plans.withIndex().all { (i, p) -> p.index == i }) { "plan indices must be 0..n-1" }
        require(plans.none { it.endMode == EndMode.CUSTOM }) { "CUSTOM plans need a task-specific runner" }
    }

    private val phase = ctx.phase
    private val common = ctx.common
    /** Practice feedback only in practice phases; scored phases can never show feedback. */
    private val feedbackMs: Long = if (phase == Phase.PRACTICE) common.practiceFeedbackMs else 0L
    private val period = (1e9 / ctx.nominalRefreshHz).toLong()
    private val halfFrame = period / 2
    private var state = State.IDLE
    private var idx = 0
    private var cur: Running? = null
    private var lastFrame: Long? = null
    private var breakStart = 0L
    private var breakClearedForIdx = -1
    private var feedbackStart = 0L
    private var feedbackCorrect = false
    private var resumeStart: Long? = null
    private val records = mutableListOf<TrialRecord>()

    override var droppedFramesTotal = 0; private set
    override var interruptions = 0; private set
    override var unassignedResponses = 0; private set
    override val isFinished: Boolean get() = state == State.FINISHED
    override val isPaused: Boolean get() = state == State.PAUSED

    override fun records(): List<TrialRecord> = records.toList()

    override fun onFrame(frameTimeNanos: Long): FrameContent {
        val ft = frameTimeNanos
        lastFrame?.let { last ->
            val delta = ft - last
            require(delta > 0) { "frame times must increase" }
            if (delta > period * 3 / 2) {
                val missed = ((delta + halfFrame) / period - 1).toInt()
                cur?.let { it.dropped += missed }
                droppedFramesTotal += missed
            }
        }
        lastFrame = ft
        var guard = 0
        while (advance(ft)) { if (++guard > 16) error("engine did not settle") }
        val content = content(ft)
        if (content.stimulus != null) cur?.let { it.stimulusFrames++ }
        return content
    }

    override fun onResponse(event: ResponseEvent) {
        if (event.release) return // timeline tasks score touch-downs only
        val c = cur
        if (c == null || state !in setOf(State.FOREPERIOD, State.STIMULUS, State.POST)) {
            unassignedResponses++
            return
        }
        if (event.key == null) c.offTarget++ else c.responses += event
    }

    override fun onInterruption(timeNanos: Long) {
        when (state) {
            State.FOREPERIOD, State.STIMULUS, State.POST -> { finishTrial(timeNanos, interrupted = true); idx++ }
            State.FINISHED, State.PAUSED -> return // already paused: one event, counted once
            else -> {}
        }
        interruptions++
        ctx.onEvent("interruption", timeNanos)
        state = State.PAUSED
    }

    override fun resume() {
        if (state == State.PAUSED) {
            state = State.RESUMING
            resumeStart = null
            lastFrame = null // the pause gap is not dropped frames
        }
    }

    override fun continueAfterBreak() {
        if (state == State.AWAIT_CONTINUE) { breakClearedForIdx = idx; state = State.IDLE }
    }

    private fun ms(x: Number) = (x.toDouble() * 1e6).toLong()

    private fun advance(ft: Long): Boolean {
        when (state) {
            State.IDLE -> {
                if (idx >= plans.size) { finishPhase(ft); return true }
                val p = plans[idx]
                val newBlock = idx > 0 && p.block != plans[idx - 1].block
                if (newBlock && common.blockBreakMs > 0 && breakClearedForIdx != idx) {
                    breakStart = ft; state = State.BREAK
                    ctx.onEvent("break_start:block=${p.block}", ft)
                    return true
                }
                val c = Running(p, ctx.seqBase + idx, ft)
                cur = c
                ctx.timingSources.forEach { it.armTrial(c.seq) }
                if (idx == 0 || newBlock) ctx.onEvent("block_start:block=${p.block}", ft)
                state = State.FOREPERIOD
                return true
            }
            State.FOREPERIOD -> {
                val c = cur!!
                if (ft >= c.start + ms(c.plan.foreperiodMs) - halfFrame) {
                    c.onset = ft
                    ctx.timingSources.forEach { it.onOnsetFrame(c.seq, ft) }
                    state = State.STIMULUS
                    return true
                }
            }
            State.STIMULUS -> {
                val c = cur!!; val p = c.plan; val onset = c.onset!!
                val off = if (p.stimulusMs != null) {
                    ft >= onset + ms(p.stimulusMs) - halfFrame
                } else {
                    hasValidResponse(c) || ft >= onset + ms(p.responseWindowMs) - halfFrame
                }
                if (off) { c.offset = ft; state = State.POST; return true }
            }
            State.POST -> {
                val c = cur!!; val p = c.plan
                val endAt = when (p.endMode) {
                    EndMode.FIXED_AFTER_ONSET -> c.onset!! + ms(p.endMs)
                    EndMode.AFTER_RESPONSE -> c.offset!! + ms(p.endMs)
                    EndMode.CUSTOM -> error("unreachable")
                }
                if (ft >= endAt - halfFrame) {
                    finishTrial(ft, interrupted = false)
                    idx++
                    if (feedbackMs > 0) {
                        feedbackStart = ft
                        feedbackCorrect = records.last().outcome == Outcome.CORRECT
                        state = State.FEEDBACK
                    } else {
                        state = State.IDLE
                    }
                    return true
                }
            }
            State.FEEDBACK -> {
                if (ft >= feedbackStart + ms(feedbackMs + common.practiceFeedbackGapMs) - halfFrame) {
                    state = State.IDLE; return true
                }
            }
            State.BREAK -> if (ft - breakStart >= ms(common.blockBreakMs)) { state = State.AWAIT_CONTINUE; return true }
            State.RESUMING -> {
                val start = resumeStart ?: ft.also { resumeStart = it }
                if (ft >= start + ms(common.resumeLeadInMs) - halfFrame) { state = State.IDLE; return true }
            }
            State.AWAIT_CONTINUE, State.PAUSED, State.FINISHED -> {}
        }
        return false
    }

    private fun content(ft: Long): FrameContent {
        val p = cur?.plan
        return when (state) {
            State.FOREPERIOD -> FrameContent(fixation = p!!.fixationInForeperiod)
            State.STIMULUS -> FrameContent(stimulus = p!!.stimulus, markerOn = true)
            State.POST -> FrameContent(fixation = p!!.fixationAfterStimulus)
            State.FEEDBACK -> if (ft < feedbackStart + ms(feedbackMs) - halfFrame)
                FrameContent(feedback = feedbackCorrect) else FrameContent(fixation = true)
            State.BREAK -> FrameContent(breakRemainingMs = ((ms(common.blockBreakMs) - (ft - breakStart)) / 1_000_000).coerceAtLeast(0))
            State.AWAIT_CONTINUE -> FrameContent(awaitingContinue = true)
            State.PAUSED -> FrameContent(paused = true)
            State.RESUMING -> FrameContent(fixation = true)
            State.FINISHED -> FrameContent(finished = true)
            State.IDLE -> FrameContent()
        }
    }

    private fun hasValidResponse(c: Running): Boolean {
        val onset = c.onset ?: return false
        val end = onset + ms(c.plan.responseWindowMs)
        return c.responses.any { it.eventTimeNanos in onset until end }
    }

    private fun finishTrial(endNanos: Long, interrupted: Boolean) {
        val c = cur ?: return
        val p = c.plan
        val onset = c.onset
        val sorted = c.responses.sortedBy { it.eventTimeNanos }
        val windowEnd = onset?.plus(ms(p.responseWindowMs))
        val premature = sorted.count { onset == null || it.eventTimeNanos < onset }
        val inWindow = if (onset == null) emptyList() else sorted.filter { it.eventTimeNanos in onset until windowEnd!! }
        val late = if (windowEnd == null) 0 else sorted.count { it.eventTimeNanos >= windowEnd }
        val firstLate = if (windowEnd == null) null else sorted.firstOrNull { it.eventTimeNanos >= windowEnd }
        val first = inWindow.firstOrNull()
        if (first != null) ctx.timingSources.forEach { it.onResponseEvent(c.seq, first.eventTimeNanos) }
        val rtMs = if (first != null && onset != null) (first.eventTimeNanos - onset) / 1e6 else null
        val outcome = if (interrupted) Outcome.INTERRUPTED else classify(p, first, rtMs)
        records += TrialRecord(
            taskId = task.id, taskVersion = task.version, phase = phase, attempt = ctx.attempt, trialSeq = c.seq,
            plan = p, trialStartNanos = c.start, onsetNanos = onset, offsetNanos = c.offset, endNanos = endNanos,
            stimulusFrames = c.stimulusFrames, responseNanos = first?.eventTimeNanos,
            responseKey = first?.key, touchX = first?.x, touchY = first?.y, rtMs = rtMs, outcome = outcome,
            prematureResponses = premature, extraResponses = (inWindow.size - 1).coerceAtLeast(0),
            lateResponses = late, offTargetTouches = c.offTarget, droppedFrames = c.dropped,
            interrupted = interrupted,
            lateRtMs = if (firstLate != null && onset != null) (firstLate.eventTimeNanos - onset) / 1e6 else null,
        )
        cur = null
    }

    private fun classify(p: TrialPlan, first: ResponseEvent?, rtMs: Double?): Outcome {
        if (first == null) return if (p.expectedResponse == null) Outcome.CORRECT else Outcome.OMISSION
        if (rtMs!! < common.anticipationMs) return Outcome.ANTICIPATION
        if (p.expectedResponse == null) return Outcome.COMMISSION
        return if (first.key == p.expectedResponse) Outcome.CORRECT else Outcome.INCORRECT
    }

    private fun finishPhase(ft: Long) {
        val bySeq = ctx.timingSources.flatMap { it.measurements() }.groupBy { it.trialSeq }
        for (i in records.indices) {
            val r = records[i]
            records[i] = r.copy(timing = bySeq[r.trialSeq].orEmpty().associateBy { it.sourceId })
        }
        state = State.FINISHED
        ctx.onEvent("phase_end", ft)
    }
}
