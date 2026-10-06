package org.cognisense.core

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.engine.EndMode
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.RunContext
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TimelineTask
import org.cognisense.core.engine.TrialEngine
import org.cognisense.core.engine.TrialPlan
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import java.io.File
import kotlin.math.abs

/**
 * Dependency-free test harness (the build sandbox has no Maven access, so no JUnit).
 * Run with android/core/run_tests.sh or ./gradlew :core:coreTests. Exit code 0 = all passed.
 */
object T {
    var passed = 0
    val failures = mutableListOf<String>()
    fun check(name: String, ok: Boolean, detail: String = "") {
        if (ok) passed++ else failures += "$name ${if (detail.isNotEmpty()) "— $detail" else ""}"
    }
    fun near(name: String, actual: Double?, expected: Double, tol: Double) =
        check(name, actual != null && abs(actual - expected) <= tol, "expected $expected ± $tol, got $actual")
    inline fun throws(name: String, block: () -> Unit) {
        val threw = try { block(); false } catch (e: Throwable) { true }
        check(name, threw, "expected an exception")
    }
}

/** The shipped config file, the same bytes the app packages. */
val CONFIG_FILE = File(System.getProperty("cognisense.config") ?: "../../config/tasks.json")
val cfg: BatteryConfig by lazy { BatteryConfig.parse(CONFIG_FILE.readBytes()) }

fun ctx(phase: Phase, hz: Double = 60.0, seed: Long = 1L, breakMs: Long? = null, attempt: Int = 1, seqBase: Long = 0) =
    RunContext(phase, seed, hz, if (breakMs != null) cfg.common.copy(blockBreakMs = breakMs) else cfg.common,
        seqBase = seqBase, attempt = attempt)

/** Drives a TaskRun like Choreographer: one onFrame per vsync; touches delivered between frames in time order. */
class FrameSim(val run: TaskRun, hz: Double, startNanos: Long = 1_000_000_000L) {
    val period = (1e9 / hz).toLong()
    var t = startNanos
    private val pending = mutableListOf<ResponseEvent>()
    var lastContent = FrameContent()
    /** Called on every frame with (frame time, content). */
    var onContent: ((Long, FrameContent) -> Unit)? = null
    /** Called on the first frame of each stimulus appearance (timeline tasks). */
    var onsetHandler: ((Long, FrameContent) -> Unit)? = null
    private var stimulusWasVisible = false
    var markerFrames = 0; var stimulusFrames = 0; var markerMismatch = 0; var frames = 0

    fun respondAt(timeNanos: Long, key: ResponseKey?, target: Int? = null, pointer: Int = 0) {
        pending += ResponseEvent(timeNanos, key, 10f, 20f, target = target, pointerId = pointer)
    }
    fun releaseAt(timeNanos: Long, pointer: Int = 0) {
        pending += ResponseEvent(timeNanos, null, 10f, 20f, release = true, pointerId = pointer)
    }

    fun frame(skip: Int = 0) {
        t += period * skip
        pending.sortBy { it.eventTimeNanos }
        while (pending.isNotEmpty() && pending.first().eventTimeNanos <= t) run.onResponse(pending.removeAt(0))
        val c = run.onFrame(t)
        frames++
        if (c.markerOn) markerFrames++
        if (c.stimulus != null) stimulusFrames++
        if (c.stimulus != null && !c.markerOn) markerMismatch++
        if (c.stimulus != null && !stimulusWasVisible) onsetHandler?.invoke(t, c)
        stimulusWasVisible = c.stimulus != null
        onContent?.invoke(t, c)
        if (c.awaitingContinue) run.continueAfterBreak()
        lastContent = c
        t += period
    }

    fun runToEnd(maxFrames: Int = 3_000_000) {
        var n = 0
        while (!run.isFinished) { frame(); require(++n < maxFrames) { "did not finish" } }
    }
}

class FixedPlanTask(private val plans: List<TrialPlan>) : TimelineTask() {
    override val id = "TEST"; override val version = "0"
    override fun plan(rng: SeededRandom, phase: Phase) = plans
    override fun practiceCriterionMet(practice: List<TrialRecord>) = true
    override fun score(scored: List<TrialRecord>) = emptyList<Metric>()
}

fun gngPlan(i: Int, cond: String, fore: Int = 1000, block: Int = 1) = TrialPlan(
    index = i, block = block, blockCondition = "fast", condition = cond, stimulus = "s",
    expectedResponse = if (cond == "go") ResponseKey.SINGLE else null, foreperiodMs = fore,
    fixationInForeperiod = true, stimulusMs = 300, responseWindowMs = 1000,
    endMode = EndMode.FIXED_AFTER_ONSET, endMs = 1300, fixationAfterStimulus = true,
)

fun engineFor(plans: List<TrialPlan>, hz: Double = 60.0, breakMs: Long = 0, phase: Phase = Phase.SCORED, attempt: Int = 1) =
    TrialEngine(FixedPlanTask(plans), ctx(phase, hz, breakMs = breakMs, attempt = attempt), plans)

const val MS = 1_000_000L
