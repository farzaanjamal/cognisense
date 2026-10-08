package org.cognisense.core.engine

import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import org.cognisense.core.timing.SoftwareTimingSource
import org.cognisense.core.timing.TimingMeasurement
import org.cognisense.core.timing.TimingSource

enum class Phase { PRACTICE, SCORED }

/** Response pads. The UI maps physical touch regions to these; LEFT/RIGHT are never mirrored for RTL locales. */
enum class ResponseKey { SINGLE, LEFT, RIGHT }

/**
 * FIXED_AFTER_ONSET: fixed event rate (e.g. Go/No-Go). AFTER_RESPONSE: response-terminated, then ITI.
 * CUSTOM: the trial timeline is defined by a task-specific runner; plan fields are descriptive only.
 */
enum class EndMode { FIXED_AFTER_ONSET, AFTER_RESPONSE, CUSTOM }

enum class Outcome {
    /** Correct response / correct withholding / completed trial (task-specific meaning documented per task). */
    CORRECT,
    /** Response when the plan expected none (e.g. No-Go). */
    COMMISSION,
    /** No response in time. */
    OMISSION,
    /** Wrong response (wrong pad, wrong sequence, held too long). */
    INCORRECT,
    /** Response faster than the anticipation threshold, or a premature release; kept, flagged, excluded from RT metrics. */
    ANTICIPATION,
    /** Trial cut short by an interruption. */
    INTERRUPTED,
}

/** Parameters shared by all tasks. Loaded from config/tasks.json ("common"); never hard-coded. */
data class CommonConfig(
    val anticipationMs: Double,
    val blockBreakMs: Long,
    val betweenTaskRestMs: Long,
    val maxPracticeAttempts: Int,
    val practiceFeedbackMs: Long,
    val practiceFeedbackGapMs: Long,
    val resumeLeadInMs: Long,
    val refreshProbeFrames: Int,
)

/** One trial, specified before it runs and logged in full. */
data class TrialPlan(
    val index: Int,
    val block: Int,
    val blockCondition: String,
    val condition: String,
    val stimulus: String,
    val expectedResponse: ResponseKey?,
    val foreperiodMs: Int,
    val fixationInForeperiod: Boolean,
    val stimulusMs: Int?,
    val responseWindowMs: Int,
    val endMode: EndMode,
    val endMs: Int,
    val fixationAfterStimulus: Boolean,
) {
    init {
        require(foreperiodMs >= 0 && responseWindowMs > 0 && endMs >= 0)
        when (endMode) {
            EndMode.FIXED_AFTER_ONSET -> {
                requireNotNull(stimulusMs) { "FIXED_AFTER_ONSET needs a fixed stimulus duration" }
                require(responseWindowMs <= endMs) { "response window must end within the trial" }
                require(stimulusMs <= endMs)
            }
            EndMode.AFTER_RESPONSE -> require(stimulusMs == null) { "AFTER_RESPONSE trials are response-terminated" }
            EndMode.CUSTOM -> {}
        }
    }
}

/**
 * A touch, as delivered by the platform layer. [eventTimeNanos] must be the MotionEvent timestamp
 * (CLOCK_MONOTONIC), never the time the callback ran.
 */
data class ResponseEvent(
    val eventTimeNanos: Long,
    /** Pad touched, or null for a touch outside every pad. */
    val key: ResponseKey?,
    val x: Float = Float.NaN,
    val y: Float = Float.NaN,
    /** Index of the board item touched (spatial span), or null. */
    val target: Int? = null,
    /** True for a finger lift. Only tasks that need releases (time reproduction) use them; others ignore them. */
    val release: Boolean = false,
    val pointerId: Int = 0,
)

data class ChoiceOption(val tokens: Int, val longDelay: Boolean, val active: Boolean)
enum class ChoiceStage { CHOOSE, WAIT, REWARD }
data class ChoiceDisplay(
    val left: ChoiceOption?,
    val right: ChoiceOption?,
    val stage: ChoiceStage,
    val rewardTokens: Int = 0,
)

/** What the platform layer must draw for one frame. Task screens draw nothing else. */
data class FrameContent(
    val stimulus: String? = null,
    val fixation: Boolean = false,
    /** Onset marker: on exactly while a task stimulus is visible (rising edge = onset, falling edge = offset). */
    val markerOn: Boolean = false,
    val breakRemainingMs: Long? = null,
    val awaitingContinue: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    /** Practice only: true = tick shape, false = cross shape. */
    val feedback: Boolean? = null,
    /** Whether response pads are drawn (time reproduction shows its pad only at the response cue). */
    val showPads: Boolean = true,
    val padPressed: Boolean = false,
    /** Board identifier (spatial span), with the item lit as stimulus and the item echoing a tap. */
    val board: String? = null,
    val highlight: Int? = null,
    val echo: Int? = null,
    /** Signals "your turn" (span recall frame, time-reproduction pad ready). */
    val responseCue: Boolean = false,
    /** Practice feedback for time reproduction: (target ms, reproduced ms). */
    val durationFeedback: Pair<Double, Double>? = null,
    val choice: ChoiceDisplay? = null,
)

data class TrialRecord(
    val taskId: String,
    val taskVersion: String,
    val phase: Phase,
    /** Practice attempt number (1-3); 1 for scored phases. */
    val attempt: Int,
    val trialSeq: Long,
    val plan: TrialPlan,
    val trialStartNanos: Long,
    val onsetNanos: Long?,
    val offsetNanos: Long?,
    val endNanos: Long,
    val stimulusFrames: Int,
    val responseNanos: Long?,
    val responseKey: ResponseKey?,
    val touchX: Float?,
    val touchY: Float?,
    /** Software-timed RT in ms relative to stimulus onset, or null where a task defines no single RT. */
    val rtMs: Double?,
    val outcome: Outcome,
    val prematureResponses: Int,
    val extraResponses: Int,
    val lateResponses: Int,
    val offTargetTouches: Int,
    val droppedFrames: Int,
    val interrupted: Boolean,
    val timing: Map<String, TimingMeasurement> = emptyMap(),
    /** Task-specific fields (e.g. span sequence and taps, reproduced duration, choice). Documented per task. */
    val details: Map<String, String> = emptyMap(),
    /**
     * Software-timed latency (ms from onset) of the first response AFTER the response window, or null.
     * The window is judged in device time, so a slow device can push a slow response past it; keeping
     * this latency lets an analysis re-apply the window after device correction instead of losing it.
     */
    val lateRtMs: Double? = null,
) {
    val measuredStimulusMs: Double?
        get() = if (onsetNanos != null && offsetNanos != null) (offsetNanos - onsetNanos) / 1e6 else null
}

/** One running phase of one task, driven by the platform: frames in, content out; touches in. */
interface TaskRun {
    fun onFrame(frameTimeNanos: Long): FrameContent
    fun onResponse(event: ResponseEvent)
    fun onInterruption(timeNanos: Long)
    fun resume()
    fun continueAfterBreak()
    val isFinished: Boolean
    val isPaused: Boolean
    fun records(): List<TrialRecord>
    val droppedFramesTotal: Int
    val interruptions: Int
    val unassignedResponses: Int
}

data class RunContext(
    val phase: Phase,
    val sessionSeed: Long,
    val nominalRefreshHz: Double,
    val common: CommonConfig,
    val seqBase: Long = 0,
    val attempt: Int = 1,
    val timingSources: List<TimingSource> = listOf(SoftwareTimingSource()),
    val onEvent: (String, Long) -> Unit = { _, _ -> },
)

/** A task = parameters from config/tasks.json + minimal task-specific logic. */
interface TaskDefinition {
    val id: String
    val version: String
    fun createRun(ctx: RunContext): TaskRun
    fun practiceCriterionMet(practice: List<TrialRecord>): Boolean
    /** Metrics from scored trials; implementations exclude interrupted trials. */
    fun score(scored: List<TrialRecord>): List<Metric>
    fun rngLabel(phase: Phase, attempt: Int = 1): String = "$id:$version:${phase.name}:$attempt"
}

/** Tasks whose trials fit a fixed timeline run on the shared TrialEngine. */
abstract class TimelineTask : TaskDefinition {
    abstract fun plan(rng: SeededRandom, phase: Phase): List<TrialPlan>

    override fun createRun(ctx: RunContext): TaskRun =
        TrialEngine(this, ctx, plan(SeededRandom.derive(ctx.sessionSeed, rngLabel(ctx.phase, ctx.attempt)), ctx.phase))
}
