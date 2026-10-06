package org.cognisense.core.tasks

import org.cognisense.core.engine.EndMode
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.TimelineTask
import org.cognisense.core.engine.TrialPlan
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import org.cognisense.core.scoring.Stats
import org.cognisense.core.scoring.TimingClass

/**
 * T5 — Flanker (arrow version). Specification: docs/task_specifications.md §5 T5.
 * All parameters come from config/tasks.json ("FLK"); this class has no defaults.
 */
data class FlankerConfig(
    val id: String,
    val version: String,
    /** Stimulus ids are "<prefix>_<congruent|incongruent>_<left|right>". */
    val stimulusPrefix: String,
    val blocks: Int,
    val trialsPerBlock: Int,
    val foreperiodMinMs: Int,
    val foreperiodMaxMs: Int,
    val responseWindowMs: Int,
    val itiMs: Int,
    val maxSameCondition: Int,
    val practiceTrials: Int,
    val practiceMinCorrect: Int,
) {
    init {
        require(trialsPerBlock % 4 == 0 && practiceTrials % 4 == 0) { "trial counts must be divisible by 4 (2 conditions x 2 directions)" }
        require(foreperiodMaxMs >= foreperiodMinMs && blocks >= 1)
        require(practiceMinCorrect <= practiceTrials)
    }
}

class FlankerTask(val config: FlankerConfig) : TimelineTask() {
    override val id = config.id
    override val version = config.version

    private data class Cell(val condition: String, val direction: ResponseKey)

    override fun plan(rng: SeededRandom, phase: Phase): List<TrialPlan> {
        val nBlocks = if (phase == Phase.PRACTICE) 1 else config.blocks
        val perBlock = if (phase == Phase.PRACTICE) config.practiceTrials else config.trialsPerBlock
        val plans = mutableListOf<TrialPlan>()
        for (b in 1..nBlocks) {
            val cells = listOf(CONGRUENT, INCONGRUENT).flatMap { c ->
                listOf(ResponseKey.LEFT, ResponseKey.RIGHT).flatMap { d -> List(perBlock / 4) { Cell(c, d) } }
            }
            val seq = SeededRandom.constrainedShuffle(cells, rng) { s ->
                maxRun(s.map { it.condition }) <= config.maxSameCondition
            }
            for (cell in seq) {
                plans += TrialPlan(
                    index = plans.size, block = b, blockCondition = "-", condition = cell.condition,
                    stimulus = "${config.stimulusPrefix}_${cell.condition}_${cell.direction.name.lowercase()}",
                    expectedResponse = cell.direction,
                    foreperiodMs = rng.nextInt(config.foreperiodMinMs, config.foreperiodMaxMs + 1),
                    fixationInForeperiod = true,
                    stimulusMs = null, responseWindowMs = config.responseWindowMs,
                    endMode = EndMode.AFTER_RESPONSE, endMs = config.itiMs,
                    fixationAfterStimulus = false,
                )
            }
        }
        return plans
    }

    override fun practiceCriterionMet(practice: List<TrialRecord>): Boolean =
        practice.count { !it.interrupted && it.outcome == Outcome.CORRECT } >= config.practiceMinCorrect

    override fun score(scored: List<TrialRecord>): List<Metric> {
        val valid = scored.filter { !it.interrupted && it.outcome != Outcome.ANTICIPATION }
        fun correctRts(c: String) = valid.filter { it.plan.condition == c && it.outcome == Outcome.CORRECT }
            .mapNotNull { it.rtMs }
        fun acc(c: String): Double? {
            val rs = valid.filter { it.plan.condition == c }
            return Stats.ratio(rs.count { it.outcome == Outcome.CORRECT }, rs.size)
        }
        val conRts = correctRts(CONGRUENT); val incRts = correctRts(INCONGRUENT)
        val allRts = conRts + incRts
        val accC = acc(CONGRUENT); val accI = acc(INCONGRUENT)
        fun ie(rts: List<Double>, a: Double?) = Stats.mean(rts)?.let { m -> a?.takeIf { it > 0 }?.let { m / it } }
        return listOf(
            Metric("interference_rt", Stats.diff(Stats.median(incRts), Stats.median(conRts)), "ms", TimingClass.B,
                allRts.size, "median incongruent minus median congruent, correct trials"),
            Metric("accuracy_interference", Stats.diff(accC, accI), "proportion", TimingClass.A, valid.size),
            Metric("accuracy_congruent", accC, "proportion", TimingClass.A, valid.count { it.plan.condition == CONGRUENT }),
            Metric("accuracy_incongruent", accI, "proportion", TimingClass.A, valid.count { it.plan.condition == INCONGRUENT }),
            Metric("median_rt", Stats.median(allRts), "ms", TimingClass.C, allRts.size),
            Metric("isd_rt", Stats.sd(allRts), "ms", TimingClass.B, allRts.size),
            Metric("inverse_efficiency_congruent", ie(conRts, accC), "ms", TimingClass.C, conRts.size, "exploratory"),
            Metric("inverse_efficiency_incongruent", ie(incRts, accI), "ms", TimingClass.C, incRts.size, "exploratory"),
            Metric("anticipations", scored.count { it.outcome == Outcome.ANTICIPATION }.toDouble(), "count",
                TimingClass.C, scored.size, "threshold applied to software-timed RT"),
            Metric("interrupted_trials", scored.count { it.interrupted }.toDouble(), "count", TimingClass.A, scored.size),
            Metric("dropped_frames", scored.sumOf { it.droppedFrames }.toDouble(), "count", TimingClass.A, scored.size),
        )
    }

    companion object {
        const val CONGRUENT = "congruent"
        const val INCONGRUENT = "incongruent"

        fun <T> maxRun(xs: List<T>): Int {
            var best = 0; var cur = 0; var prev: T? = null
            for ((i, x) in xs.withIndex()) {
                cur = if (i > 0 && x == prev) cur + 1 else 1
                if (cur > best) best = cur
                prev = x
            }
            return best
        }
    }
}
