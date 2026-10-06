package org.cognisense.core.engine

import org.cognisense.core.util.Portable

/**
 * Base for tasks whose trials do not fit a fixed timeline (adaptive spatial span, press-and-hold
 * time reproduction, choice-dependent choice-delay). Shares the TrialEngine's guarantees:
 *   - clock-free: driven only by frame times and event timestamps;
 *   - an event targeted at T happens on the frame nearest T ([due]);
 *   - dropped frames counted per trial; interruptions counted once; 1 s fixation lead-in on resume;
 *   - timing-source measurements attached to records when the phase finishes.
 * Subclasses implement [step] (advance their state machine and return this frame's content),
 * [touch] and [interruptCurrent].
 */
abstract class CustomRunner(
    private val task: TaskDefinition,
    protected val ctx: RunContext,
) : TaskRun {
    protected val phase: Phase = ctx.phase
    private val period = (1e9 / ctx.nominalRefreshHz).toLong()
    private val halfFrame = period / 2
    private var lastFrame: Long? = null
    private var paused = false
    private var resuming = false
    private var resumeStart: Long? = null
    private var finished = false
    private var trialDropped = 0
    private val records = mutableListOf<TrialRecord>()

    init { require(ctx.nominalRefreshHz in 20.0..500.0) { "implausible refresh rate ${ctx.nominalRefreshHz}" } }

    override var droppedFramesTotal = 0; protected set
    override var interruptions = 0; protected set
    override var unassignedResponses = 0; protected set
    override val isFinished: Boolean get() = finished
    override val isPaused: Boolean get() = paused
    override fun records(): List<TrialRecord> = records.toList()
    override fun continueAfterBreak() {}

    final override fun onFrame(frameTimeNanos: Long): FrameContent {
        val ft = frameTimeNanos
        lastFrame?.let { last ->
            val delta = ft - last
            require(delta > 0) { "frame times must increase" }
            if (delta > period * 3 / 2) {
                val missed = ((delta + halfFrame) / period - 1).toInt()
                trialDropped += missed
                droppedFramesTotal += missed
            }
        }
        lastFrame = ft
        if (finished) return FrameContent(finished = true)
        if (paused) return FrameContent(paused = true, showPads = false)
        if (resuming) {
            val start = resumeStart ?: ft.also { resumeStart = it }
            if (!due(ft, start + ms(ctx.common.resumeLeadInMs))) return FrameContent(fixation = true, showPads = false)
            resuming = false
        }
        val content = step(ft)
        return if (finished) FrameContent(finished = true) else content
    }

    final override fun onResponse(event: ResponseEvent) {
        if (finished || paused || resuming) {
            if (!event.release) unassignedResponses++
            return
        }
        touch(event)
    }

    final override fun onInterruption(timeNanos: Long) {
        if (finished || paused) return
        interruptions++
        paused = true
        interruptCurrent(timeNanos)
        ctx.onEvent("interruption", timeNanos)
    }

    final override fun resume() {
        if (paused) { paused = false; resuming = true; resumeStart = null; lastFrame = null }
    }

    protected abstract fun step(ft: Long): FrameContent
    protected abstract fun touch(event: ResponseEvent)
    /** Record the current trial (if any) as INTERRUPTED and reset to a state that starts a trial after resume. */
    protected abstract fun interruptCurrent(timeNanos: Long)

    protected fun ms(x: Number): Long = (x.toDouble() * 1e6).toLong()
    protected fun due(ft: Long, target: Long): Boolean = ft >= target - halfFrame
    protected fun fmt(x: Double): String = Portable.fixed(x, 3)

    protected fun arm(seq: Long) = ctx.timingSources.forEach { it.armTrial(seq) }
    protected fun markOnset(seq: Long, ft: Long) = ctx.timingSources.forEach { it.onOnsetFrame(seq, ft) }
    protected fun markResponse(seq: Long, t: Long) = ctx.timingSources.forEach { it.onResponseEvent(seq, t) }

    protected fun addRecord(
        plan: TrialPlan, seq: Long, trialStart: Long, onset: Long?, offset: Long?, end: Long, stimulusFrames: Int,
        response: ResponseEvent?, rtMs: Double?, outcome: Outcome, premature: Int, extra: Int, late: Int,
        offTarget: Int, details: Map<String, String>,
    ) {
        records += TrialRecord(
            taskId = task.id, taskVersion = task.version, phase = phase, attempt = ctx.attempt, trialSeq = seq,
            plan = plan, trialStartNanos = trialStart, onsetNanos = onset, offsetNanos = offset, endNanos = end,
            stimulusFrames = stimulusFrames, responseNanos = response?.eventTimeNanos, responseKey = response?.key,
            touchX = response?.x, touchY = response?.y, rtMs = rtMs, outcome = outcome,
            prematureResponses = premature, extraResponses = extra, lateResponses = late, offTargetTouches = offTarget,
            droppedFrames = trialDropped, interrupted = outcome == Outcome.INTERRUPTED, details = details,
        )
        trialDropped = 0
    }

    protected fun finish(ft: Long) {
        val bySeq = ctx.timingSources.flatMap { it.measurements() }.groupBy { it.trialSeq }
        for (i in records.indices) {
            val r = records[i]
            records[i] = r.copy(timing = bySeq[r.trialSeq].orEmpty().associateBy { it.sourceId })
        }
        finished = true
        ctx.onEvent("phase_end", ft)
    }
}
