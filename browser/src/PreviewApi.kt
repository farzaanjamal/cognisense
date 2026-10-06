@file:OptIn(ExperimentalJsExport::class)

package org.cognisense.browser

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.data.JsonExport
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.RunContext
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.verify.CrossPlatformCheck

/**
 * JavaScript entry point for the browser preview. Compiled together with the unchanged core
 * sources (android/core/src/main/kotlin), so the preview runs the same task logic as the app.
 *
 * REVIEW TOOL ONLY: this API can start only the shortened Expert Review variants of each task.
 * There is no way to run full-length tasks, no session mode, and no participant identifiers.
 * Times are milliseconds on the page clock (performance.now / requestAnimationFrame / event.timeStamp).
 */
@JsExport
class PreviewApi(configJson: String) {
    private val cfg = BatteryConfig.parse(configJson.encodeToByteArray())

    val configVersion: String get() = cfg.configVersion
    val configSha256: String get() = cfg.sha256

    fun reviewVersion(partId: String): String = cfg.review(partId).version

    /** [seed] is a decimal 64-bit integer as a string (JavaScript numbers cannot hold 64 bits exactly). */
    fun startReviewRun(partId: String, phase: String, seed: String, hz: Double, attempt: Int, seqBase: Int): PreviewRun {
        val def = cfg.review(partId)
        val run = def.createRun(RunContext(Phase.valueOf(phase), seed.toLong(), hz, cfg.common, seqBase.toLong(), attempt))
        return PreviewRun(def, run)
    }

    /** Verification only: digest of a scripted run, compared against the JVM build. */
    fun verificationDigest(partId: String, phase: String, seed: String, review: Boolean): String =
        CrossPlatformCheck.digest(cfg, partId, Phase.valueOf(phase), seed.toLong(), review)
}

@JsExport
class PreviewRun internal constructor(private val def: TaskDefinition, private val run: TaskRun) {
    val taskId: String get() = def.id
    val version: String get() = def.version
    val finished: Boolean get() = run.isFinished
    val paused: Boolean get() = run.isPaused

    private fun ns(ms: Double): Long = (ms * 1_000_000.0).toLong()

    /** Advance to frame time [tMs]; returns the frame's content as JSON. */
    fun frame(tMs: Double): String = frameJson(run.onFrame(ns(tMs)))

    /** [key]: "" | SINGLE | LEFT | RIGHT; [target]: board square index or -1. */
    fun touch(tMs: Double, key: String, target: Int, release: Boolean, pointer: Int, x: Double, y: Double) {
        run.onResponse(ResponseEvent(ns(tMs), if (key.isEmpty()) null else ResponseKey.valueOf(key), x.toFloat(), y.toFloat(),
            target = if (target < 0) null else target, release = release, pointerId = pointer))
    }

    fun interrupt(tMs: Double) = run.onInterruption(ns(tMs))
    fun resume() = run.resume()
    fun continueAfterBreak() = run.continueAfterBreak()
    fun practiceCriterionMet(): Boolean = def.practiceCriterionMet(run.records())
    fun droppedFrames(): Int = run.droppedFramesTotal
    fun interruptions(): Int = run.interruptions

    fun recordsJson(): String = JsonExport.value(run.records().map { recordMap(it) })

    fun metricsJson(): String = JsonExport.value(def.score(run.records()).map {
        mapOf("name" to it.name, "value" to it.value, "unit" to it.unit, "timing_class" to it.timingClass.name,
            "n" to it.n, "note" to it.note)
    })

    private fun recordMap(r: TrialRecord): Map<String, Any?> = linkedMapOf(
        "task_id" to r.taskId, "task_version" to r.taskVersion, "phase" to r.phase.name, "attempt" to r.attempt,
        "trial_seq" to r.trialSeq, "trial_index" to r.plan.index, "block" to r.plan.block,
        "block_condition" to r.plan.blockCondition, "condition" to r.plan.condition, "stimulus" to r.plan.stimulus,
        "expected_response" to (r.plan.expectedResponse?.name ?: "WITHHOLD"), "outcome" to r.outcome.name,
        "rt_ms_not_a_measurement" to r.rtMs, "stimulus_frames" to r.stimulusFrames,
        "displayed_stimulus_ms" to r.measuredStimulusMs, "premature_responses" to r.prematureResponses,
        "extra_responses" to r.extraResponses, "late_responses" to r.lateResponses,
        "off_target_touches" to r.offTargetTouches, "dropped_frames" to r.droppedFrames, "interrupted" to r.interrupted,
        "details" to r.details,
    )

    private fun frameJson(c: FrameContent): String = JsonExport.value(linkedMapOf(
        "stimulus" to c.stimulus, "fixation" to c.fixation, "markerOn" to c.markerOn,
        "breakRemainingMs" to c.breakRemainingMs, "awaitingContinue" to c.awaitingContinue, "paused" to c.paused,
        "finished" to c.finished, "feedback" to c.feedback, "showPads" to c.showPads, "padPressed" to c.padPressed,
        "board" to c.board, "highlight" to c.highlight, "echo" to c.echo, "responseCue" to c.responseCue,
        "durationFeedback" to c.durationFeedback?.let { listOf(it.first, it.second) },
        "choice" to c.choice?.let { ch ->
            fun opt(o: org.cognisense.core.engine.ChoiceOption?) =
                o?.let { mapOf("tokens" to it.tokens, "longDelay" to it.longDelay, "active" to it.active) }
            mapOf("left" to opt(ch.left), "right" to opt(ch.right), "stage" to ch.stage.name, "rewardTokens" to ch.rewardTokens)
        },
    ))
}
