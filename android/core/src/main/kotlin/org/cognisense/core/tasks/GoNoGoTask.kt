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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * T2 — Go/No-Go with event-rate manipulation. Specification: docs/task_specifications.md §5 T2.
 * All parameters come from config/tasks.json ("GNG"); this class has no defaults.
 */
data class GoNoGoConfig(
    val id: String,
    val version: String,
    val goStimulus: String,
    val nogoStimulus: String,
    val stimulusMs: Int,
    val responseWindowMs: Int,
    /** Inter-stimulus interval per event rate; keys must be exactly "fast" and "slow". */
    val isiMs: Map<String, Int>,
    val blockLeadInMs: Int,
    val nogoFraction: Double,
    val blocks: List<Pair<String, Int>>,
    val leadingGo: Int,
    val maxConsecutiveNoGo: Int,
    val practiceRate: String,
    val practiceTrials: Int,
    val practiceLeadingGo: Int,
    val practiceMinGoHits: Int,
    val practiceMinNoGoWithheld: Int,
) {
    init {
        require(isiMs.keys == setOf("fast", "slow")) { "isi_ms must define exactly 'fast' and 'slow'" }
        require((blocks.map { it.first } + practiceRate).all { it in isiMs }) { "unknown event rate" }
        for (n in blocks.map { it.second } + practiceTrials) {
            val k = n * nogoFraction
            require(abs(k - k.roundToInt()) < 1e-9) { "$n trials x $nogoFraction is not a whole number of No-Go trials" }
        }
        require(blocks.all { it.second > leadingGo } && practiceTrials > practiceLeadingGo)
        require(responseWindowMs <= stimulusMs + isiMs.values.min()) { "response window longer than the SOA" }
    }

    fun nogoCount(n: Int): Int = (n * nogoFraction).roundToInt()
}

class GoNoGoTask(val config: GoNoGoConfig) : TimelineTask() {
    override val id = config.id
    override val version = config.version

    override fun plan(rng: SeededRandom, phase: Phase): List<TrialPlan> {
        val blocks = if (phase == Phase.PRACTICE) listOf(config.practiceRate to config.practiceTrials) else config.blocks
        val leading = if (phase == Phase.PRACTICE) config.practiceLeadingGo else config.leadingGo
        val plans = mutableListOf<TrialPlan>()
        blocks.forEachIndexed { b, (rate, n) ->
            val nNoGo = config.nogoCount(n)
            val base = List(n - nNoGo) { GO } + List(nNoGo) { NOGO }
            val seq = SeededRandom.constrainedShuffle(base, rng) { s ->
                s.take(leading).all { it == GO } &&
                    SeededRandom.longestRun(s) { it == NOGO } <= config.maxConsecutiveNoGo
            }
            val isi = config.isiMs.getValue(rate)
            seq.forEachIndexed { i, cond ->
                plans += TrialPlan(
                    index = plans.size, block = b + 1, blockCondition = rate, condition = cond,
                    stimulus = if (cond == GO) config.goStimulus else config.nogoStimulus,
                    expectedResponse = if (cond == GO) ResponseKey.SINGLE else null,
                    foreperiodMs = if (i == 0) config.blockLeadInMs else 0,
                    fixationInForeperiod = true,
                    stimulusMs = config.stimulusMs,
                    responseWindowMs = config.responseWindowMs,
                    endMode = EndMode.FIXED_AFTER_ONSET,
                    endMs = config.stimulusMs + isi,
                    fixationAfterStimulus = true,
                )
            }
        }
        return plans
    }

    override fun practiceCriterionMet(practice: List<TrialRecord>): Boolean {
        val valid = practice.filter { !it.interrupted }
        val hits = valid.count { it.plan.condition == GO && it.outcome == Outcome.CORRECT }
        val withheld = valid.count { it.plan.condition == NOGO && it.outcome == Outcome.CORRECT }
        return hits >= config.practiceMinGoHits && withheld >= config.practiceMinNoGoWithheld
    }

    private data class Summary(
        val nGo: Int, val nNoGo: Int, val hits: Int, val omissions: Int, val commissions: Int,
        val anticipations: Int, val rts: List<Double>,
    ) {
        val omissionRate get() = Stats.ratio(omissions, nGo)
        val commissionRate get() = Stats.ratio(commissions, nNoGo)
        val medianRt get() = Stats.median(rts)
        val isd get() = Stats.sd(rts)
    }

    /** Anticipations are excluded from both numerator and denominator and reported separately. */
    private fun summarise(rs: List<TrialRecord>): Summary {
        val go = rs.filter { it.plan.condition == GO }
        val nogo = rs.filter { it.plan.condition == NOGO }
        val goEval = go.filter { it.outcome != Outcome.ANTICIPATION }
        val nogoEval = nogo.filter { it.outcome != Outcome.ANTICIPATION }
        return Summary(
            nGo = goEval.size, nNoGo = nogoEval.size,
            hits = goEval.count { it.outcome == Outcome.CORRECT },
            omissions = goEval.count { it.outcome == Outcome.OMISSION },
            commissions = nogoEval.count { it.outcome == Outcome.COMMISSION },
            anticipations = rs.count { it.outcome == Outcome.ANTICIPATION },
            rts = goEval.filter { it.outcome == Outcome.CORRECT }.mapNotNull { it.rtMs },
        )
    }

    override fun score(scored: List<TrialRecord>): List<Metric> {
        val valid = scored.filter { !it.interrupted }
        val all = summarise(valid)
        val fast = summarise(valid.filter { it.plan.blockCondition == "fast" })
        val slow = summarise(valid.filter { it.plan.blockCondition == "slow" })
        val fastBlocks = valid.filter { it.plan.blockCondition == "fast" }.map { it.plan.block }.distinct().sorted()
        val f1 = fastBlocks.getOrNull(0)?.let { b -> summarise(valid.filter { it.plan.block == b }) }
        val f2 = fastBlocks.getOrNull(1)?.let { b -> summarise(valid.filter { it.plan.block == b }) }
        val meanRt = Stats.mean(all.rts)
        val cv = if (all.isd != null && meanRt != null && meanRt > 0) all.isd!! / meanRt else null

        val m = mutableListOf(
            Metric("commission_rate", all.commissionRate, "proportion", TimingClass.A, all.nNoGo),
            Metric("omission_rate", all.omissionRate, "proportion", TimingClass.A, all.nGo),
            Metric("d_prime", Stats.dPrime(all.hits, all.nGo, all.commissions, all.nNoGo), "z", TimingClass.A,
                all.nGo + all.nNoGo, "log-linear correction"),
            Metric("median_go_rt", all.medianRt, "ms", TimingClass.C, all.rts.size),
            Metric("isd_go_rt", all.isd, "ms", TimingClass.B, all.rts.size),
            Metric("cv_go_rt", cv, "ratio", TimingClass.C, all.rts.size, "offset-sensitive via the mean"),
            Metric("anticipations", all.anticipations.toDouble(), "count", TimingClass.C, valid.size,
                "threshold applied to software-timed RT"),
        )
        for ((label, s) in listOf("fast" to fast, "slow" to slow)) {
            m += Metric("${label}_commission_rate", s.commissionRate, "proportion", TimingClass.A, s.nNoGo)
            m += Metric("${label}_omission_rate", s.omissionRate, "proportion", TimingClass.A, s.nGo)
            m += Metric("${label}_median_go_rt", s.medianRt, "ms", TimingClass.C, s.rts.size)
            m += Metric("${label}_isd_go_rt", s.isd, "ms", TimingClass.B, s.rts.size)
        }
        m += Metric("event_rate_median_rt_effect", Stats.diff(slow.medianRt, fast.medianRt), "ms", TimingClass.B,
            fast.rts.size + slow.rts.size, "slow minus fast")
        m += Metric("event_rate_isd_effect", Stats.diff(slow.isd, fast.isd), "ms", TimingClass.B,
            fast.rts.size + slow.rts.size, "slow minus fast")
        m += Metric("event_rate_omission_effect", Stats.diff(slow.omissionRate, fast.omissionRate), "proportion",
            TimingClass.A, fast.nGo + slow.nGo, "slow minus fast")
        m += Metric("event_rate_commission_effect", Stats.diff(slow.commissionRate, fast.commissionRate),
            "proportion", TimingClass.A, fast.nNoGo + slow.nNoGo, "slow minus fast; few No-Go trials per rate")
        m += Metric("time_on_task_median_rt", Stats.diff(f2?.medianRt, f1?.medianRt), "ms", TimingClass.B,
            (f1?.rts?.size ?: 0) + (f2?.rts?.size ?: 0), "second fast block minus first")
        m += Metric("time_on_task_omission", Stats.diff(f2?.omissionRate, f1?.omissionRate), "proportion",
            TimingClass.A, (f1?.nGo ?: 0) + (f2?.nGo ?: 0), "second fast block minus first")
        m += Metric("interrupted_trials", scored.count { it.interrupted }.toDouble(), "count", TimingClass.A, scored.size)
        m += Metric("dropped_frames", scored.sumOf { it.droppedFrames }.toDouble(), "count", TimingClass.A, scored.size)
        return m
    }

    companion object {
        const val GO = "go"
        const val NOGO = "nogo"
    }
}
