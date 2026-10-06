package org.cognisense.core.tasks

import org.cognisense.core.engine.ChoiceDisplay
import org.cognisense.core.engine.ChoiceOption
import org.cognisense.core.engine.ChoiceStage
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

/**
 * T10 — Choice-delay task. Specification: docs/task_specifications.md §5 T10.
 *
 * Practice phase = forced-choice familiarisation (only one option active), so the child experiences
 * both delays. Scored phase = free choices. Smaller-sooner (SS) vs larger-later (LL); no post-reward
 * delay, so choosing SS shortens the task. Rewards are shown per trial as tokens and then cleared:
 * there is no cumulative tally and no exchange. The waiting display is static (no countdown, no
 * animation). Which side shows SS is balanced and seeded.
 *
 * UNRESOLVED CONFLICT (docs/task_specifications.md §5 T10): this task requires a reward outcome,
 * which the project's no-reward rule otherwise prohibits. Implemented as option (a) pending the panel.
 */
data class CdtConfig(
    val id: String,
    val version: String,
    val stimulus: String,
    val ssTokens: Int,
    val ssDelayMs: Int,
    val llTokens: Int,
    val llDelayMs: Int,
    val postRewardDelayMs: Int,
    val rewardDisplayMs: Int,
    val fixationBeforeMs: Int,
    val itiMs: Int,
    val choiceTimeoutMs: Int,
    /** Practice: forced trials in order, each "SS" or "LL". */
    val forced: List<String>,
    val freeTrials: Int,
) {
    init {
        require(forced.isNotEmpty() && forced.all { it == "SS" || it == "LL" })
        require(freeTrials >= 2 && freeTrials % 2 == 0) { "free trials must be even so sides can be balanced" }
        require(llTokens > ssTokens && llDelayMs > ssDelayMs)
    }
}

class ChoiceDelayTask(val config: CdtConfig) : TaskDefinition {
    override val id = config.id
    override val version = config.version

    data class CdtTrial(val kind: String, val ssLeft: Boolean)

    /** Trial list for a phase. Sides balanced: exactly half the trials show SS on the left (rounded for odd counts). */
    fun trials(rng: SeededRandom, phase: Phase): List<CdtTrial> {
        val kinds = if (phase == Phase.PRACTICE) config.forced.map { "forced_$it" } else List(config.freeTrials) { "free" }
        val sides = (List(kinds.size / 2) { true } + List(kinds.size - kinds.size / 2) { false }).toMutableList()
        rng.shuffle(sides)
        return kinds.mapIndexed { i, k -> CdtTrial(k, sides[i]) }
    }

    override fun createRun(ctx: RunContext): TaskRun =
        CdtRunner(this, ctx, trials(SeededRandom.derive(ctx.sessionSeed, rngLabel(ctx.phase, ctx.attempt)), ctx.phase))

    override fun practiceCriterionMet(practice: List<TrialRecord>): Boolean =
        practice.size == config.forced.size && practice.all { it.outcome == Outcome.CORRECT }

    override fun score(scored: List<TrialRecord>): List<Metric> {
        val free = scored.filter { !it.interrupted && it.plan.condition == "free" }
        val made = free.filter { it.outcome == Outcome.CORRECT }
        fun propLl(rs: List<TrialRecord>) = Stats.ratio(rs.count { it.details["choice"] == "LL" }, rs.size)
        val half = free.size / 2
        val firstHalf = free.take(half).filter { it.outcome == Outcome.CORRECT }
        val secondHalf = free.drop(half).filter { it.outcome == Outcome.CORRECT }
        val latencies = made.mapNotNull { it.rtMs }
        val duration = if (scored.isEmpty()) null else (scored.last().endNanos - scored.first().trialStartNanos) / 1e9
        return listOf(
            Metric("prop_larger_later", propLl(made), "proportion", TimingClass.A, made.size, "free-choice trials with a choice"),
            Metric("prop_larger_later_first_half", propLl(firstHalf), "proportion", TimingClass.A, firstHalf.size, "exploratory"),
            Metric("prop_larger_later_second_half", propLl(secondHalf), "proportion", TimingClass.A, secondHalf.size, "exploratory"),
            Metric("median_choice_latency", Stats.median(latencies), "ms", TimingClass.C, latencies.size, "exploratory"),
            Metric("omissions", free.count { it.outcome == Outcome.OMISSION }.toDouble(), "count", TimingClass.A, free.size),
            Metric("taps_while_waiting", made.sumOf { it.details["wait_taps"]?.toIntOrNull() ?: 0 }.toDouble(), "count",
                TimingClass.A, made.size, "exploratory"),
            Metric("scored_duration", duration, "s", TimingClass.A, scored.size, "depends on the child's choices"),
            Metric("interrupted_trials", scored.count { it.interrupted }.toDouble(), "count", TimingClass.A, scored.size),
            Metric("dropped_frames", scored.sumOf { it.droppedFrames }.toDouble(), "count", TimingClass.A, scored.size),
        )
    }
}

class CdtRunner(
    task: ChoiceDelayTask, ctx: RunContext, private val trials: List<ChoiceDelayTask.CdtTrial>,
) : CustomRunner(task, ctx) {
    private val c = task.config
    private enum class S { IDLE, FIX, CHOOSE, WAIT, REWARD, POST, ITI }

    private var s = S.IDLE
    private var idx = 0
    private var seqNo = 0L
    private var trialStart = 0L
    private var onset: Long? = null
    private var choice: ResponseEvent? = null
    private var rewardAt = 0L
    private var stateStart = 0L
    private var premature = 0
    private var extra = 0
    private var offTarget = 0
    private var waitTaps = 0
    private var stimFrames = 0

    private val trial get() = trials[idx]

    /** Option shown on [side], or null. SS sits on the left when trial.ssLeft. */
    private fun option(side: ResponseKey): ChoiceOption {
        val isSs = (side == ResponseKey.LEFT) == trial.ssLeft
        val active = when (trial.kind) {
            "forced_SS" -> isSs
            "forced_LL" -> !isSs
            else -> true
        }
        return if (isSs) ChoiceOption(c.ssTokens, longDelay = false, active = active)
        else ChoiceOption(c.llTokens, longDelay = true, active = active)
    }

    private fun chosenIsSs(): Boolean = (choice!!.key == ResponseKey.LEFT) == trial.ssLeft

    override fun step(ft: Long): FrameContent {
        var guard = 0
        while (advance(ft)) if (++guard > 16) error("choice-delay runner did not settle")
        return content()
    }

    private fun advance(ft: Long): Boolean {
        when (s) {
            S.IDLE -> {
                if (idx >= trials.size) { finish(ft); return false }
                seqNo = ctx.seqBase + idx; trialStart = ft
                onset = null; choice = null; premature = 0; extra = 0; offTarget = 0; waitTaps = 0; stimFrames = 0
                arm(seqNo); s = S.FIX; return true
            }
            S.FIX -> if (due(ft, trialStart + ms(c.fixationBeforeMs))) { onset = ft; markOnset(seqNo, ft); s = S.CHOOSE; return true }
            S.CHOOSE -> {
                val ch = choice
                if (ch != null) {
                    rewardAt = ch.eventTimeNanos + ms(if (chosenIsSs()) c.ssDelayMs else c.llDelayMs)
                    s = S.WAIT; return true
                }
                if (due(ft, onset!! + ms(c.choiceTimeoutMs))) { endTrial(ft, interrupted = false); s = S.ITI; stateStart = ft; return true }
            }
            S.WAIT -> if (due(ft, rewardAt)) { s = S.REWARD; stateStart = ft; return true }
            S.REWARD -> if (due(ft, stateStart + ms(c.rewardDisplayMs))) { s = S.POST; stateStart = ft; return true }
            S.POST -> if (due(ft, stateStart + ms(c.postRewardDelayMs))) { endTrial(ft, interrupted = false); s = S.ITI; stateStart = ft; return true }
            S.ITI -> if (due(ft, stateStart + ms(c.itiMs))) { s = S.IDLE; return true }
        }
        return false
    }

    private fun content(): FrameContent = when (s) {
        S.IDLE, S.FIX, S.ITI -> FrameContent(fixation = true, showPads = false)
        S.CHOOSE -> {
            stimFrames++
            FrameContent(choice = ChoiceDisplay(option(ResponseKey.LEFT), option(ResponseKey.RIGHT), ChoiceStage.CHOOSE),
                markerOn = true, showPads = true)
        }
        S.WAIT -> {
            val left = choice!!.key == ResponseKey.LEFT
            FrameContent(choice = ChoiceDisplay(if (left) option(ResponseKey.LEFT) else null,
                if (left) null else option(ResponseKey.RIGHT), ChoiceStage.WAIT), showPads = false)
        }
        S.REWARD -> FrameContent(choice = ChoiceDisplay(null, null, ChoiceStage.REWARD,
            rewardTokens = if (chosenIsSs()) c.ssTokens else c.llTokens), showPads = false)
        S.POST -> FrameContent(showPads = false)
    }

    override fun touch(event: ResponseEvent) {
        if (event.release) return
        when (s) {
            S.CHOOSE -> when {
                choice != null -> extra++
                event.eventTimeNanos < onset!! -> premature++
                event.key == ResponseKey.LEFT || event.key == ResponseKey.RIGHT ->
                    if (option(event.key).active) { choice = event; markResponse(seqNo, event.eventTimeNanos) } else offTarget++
                else -> offTarget++
            }
            S.WAIT, S.REWARD, S.POST -> waitTaps++
            S.FIX -> premature++
            S.IDLE, S.ITI -> unassignedResponses++
        }
    }

    override fun interruptCurrent(timeNanos: Long) {
        if (s in setOf(S.FIX, S.CHOOSE, S.WAIT, S.REWARD, S.POST)) endTrial(timeNanos, interrupted = true)
        s = S.IDLE
    }

    private fun endTrial(end: Long, interrupted: Boolean) {
        val ch = choice
        val outcome = when {
            interrupted -> Outcome.INTERRUPTED
            ch == null -> Outcome.OMISSION
            else -> Outcome.CORRECT
        }
        val ss = if (ch != null) chosenIsSs() else null
        val forcedSide = when (trial.kind) {
            "forced_SS" -> if (trial.ssLeft) ResponseKey.LEFT else ResponseKey.RIGHT
            "forced_LL" -> if (trial.ssLeft) ResponseKey.RIGHT else ResponseKey.LEFT
            else -> null
        }
        val plan = TrialPlan(
            index = idx, block = 1, blockCondition = if (trial.ssLeft) "ss_left" else "ss_right", condition = trial.kind,
            stimulus = c.stimulus, expectedResponse = forcedSide, foreperiodMs = c.fixationBeforeMs,
            fixationInForeperiod = true, stimulusMs = null, responseWindowMs = c.choiceTimeoutMs,
            endMode = EndMode.CUSTOM, endMs = c.itiMs, fixationAfterStimulus = true,
        )
        val rt = if (ch != null && onset != null) (ch.eventTimeNanos - onset!!) / 1e6 else null
        addRecord(
            plan, seqNo, trialStart, onset, null, end, stimFrames, ch, rtMs = rt, outcome = outcome,
            premature = premature, extra = extra, late = 0, offTarget = offTarget,
            details = mapOf(
                "kind" to trial.kind,
                "ss_side" to if (trial.ssLeft) "LEFT" else "RIGHT",
                "choice" to when (ss) { true -> "SS"; false -> "LL"; null -> "" },
                "delay_ms" to when (ss) { true -> c.ssDelayMs.toString(); false -> c.llDelayMs.toString(); null -> "" },
                "tokens" to when (ss) { true -> c.ssTokens.toString(); false -> c.llTokens.toString(); null -> "" },
                "wait_taps" to waitTaps.toString(),
            ),
        )
        idx++
    }
}
