package org.cognisense.core.config

import org.cognisense.core.engine.CommonConfig
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.tasks.CdtConfig
import org.cognisense.core.tasks.ChoiceDelayTask
import org.cognisense.core.tasks.FlankerConfig
import org.cognisense.core.tasks.FlankerTask
import org.cognisense.core.tasks.GoNoGoConfig
import org.cognisense.core.tasks.GoNoGoTask
import org.cognisense.core.tasks.SpanConfig
import org.cognisense.core.tasks.SpatialSpanTask
import org.cognisense.core.tasks.TimeReproductionTask
import org.cognisense.core.tasks.TrpConfig
import org.cognisense.core.util.Portable

/**
 * config/tasks.json — the single source of truth for every task parameter, read by the Android app
 * (and, when built, the browser preview). Loading builds every task definition, standard and review,
 * so any malformed or inconsistent value fails here, at start-up, with its JSON path.
 *
 * Every session records [configVersion] and [sha256] (of the exact file bytes), so the parameters
 * behind any data set can be identified even if someone edits the file without bumping versions.
 */
class BatteryConfig private constructor(
    val configVersion: String,
    val sha256: String,
    val common: CommonConfig,
    val coreOrder: List<String>,
    val pool: List<PoolEntry>,
    val display: JObj,
    val stimuli: JObj,
    private val tasks: JObj,
) {
    data class PoolEntry(
        val code: String, val id: String, val name: String, val construct: String, val measures: String,
        val reviewDuration: String, val parts: List<String>,
    )

    private val built = HashMap<Pair<String, Boolean>, TaskDefinition>()

    fun standard(partId: String): TaskDefinition = build(partId, review = false)
    fun review(partId: String): TaskDefinition = build(partId, review = true)
    fun pads(partId: String): String = tasks.obj(partId).str("pads")
    fun poolEntry(code: String): PoolEntry = pool.first { it.code == code }

    private fun build(id: String, review: Boolean): TaskDefinition = built.getOrPut(id to review) {
        val base = tasks.obj(id)
        val o = if (review) base.merged(base.obj("review")) else base
        val v = base.str("version") + if (review) "-review" else ""
        when (val kind = base.str("kind")) {
            "go_no_go" -> GoNoGoTask(goNoGo(id, v, o))
            "flanker" -> FlankerTask(flanker(id, v, o))
            "spatial_span" -> SpatialSpanTask(span(id, v, o))
            "time_reproduction" -> TimeReproductionTask(trp(id, v, o))
            "choice_delay" -> ChoiceDelayTask(cdt(id, v, o))
            else -> throw IllegalArgumentException("config: unknown task kind '$kind' for $id")
        }
    }

    private fun goNoGo(id: String, v: String, o: JObj): GoNoGoConfig {
        val st = o.obj("stimuli"); val pr = o.obj("practice"); val isi = o.obj("isi_ms")
        return GoNoGoConfig(
            id = id, version = v, goStimulus = st.str("go"), nogoStimulus = st.str("nogo"),
            stimulusMs = o.int("stimulus_ms"), responseWindowMs = o.int("response_window_ms"),
            isiMs = isi.keys().associateWith { isi.int(it) }, blockLeadInMs = o.int("block_lead_in_ms"),
            nogoFraction = o.num("nogo_fraction"), blocks = o.objList("blocks").map { it.str("rate") to it.int("trials") },
            leadingGo = o.int("leading_go"), maxConsecutiveNoGo = o.int("max_consecutive_nogo"),
            practiceRate = pr.str("rate"), practiceTrials = pr.int("trials"), practiceLeadingGo = pr.int("leading_go"),
            practiceMinGoHits = pr.int("min_go_hits"), practiceMinNoGoWithheld = pr.int("min_nogo_withheld"),
        )
    }

    private fun flanker(id: String, v: String, o: JObj): FlankerConfig {
        val fp = o.obj("foreperiod_ms"); val pr = o.obj("practice")
        return FlankerConfig(
            id = id, version = v, stimulusPrefix = o.str("stimulus_prefix"), blocks = o.int("blocks"),
            trialsPerBlock = o.int("trials_per_block"), foreperiodMinMs = fp.int("min"), foreperiodMaxMs = fp.int("max"),
            responseWindowMs = o.int("response_window_ms"), itiMs = o.int("iti_ms"),
            maxSameCondition = o.int("max_same_condition"), practiceTrials = pr.int("trials"),
            practiceMinCorrect = pr.int("min_correct"),
        )
    }

    private fun span(id: String, v: String, o: JObj): SpanConfig {
        val seqs = o.obj("sequences"); val pr = o.obj("practice")
        val board = o.str("board")
        return SpanConfig(
            id = id, version = v, backward = when (val d = o.str("direction")) {
                "forward" -> false; "backward" -> true
                else -> throw IllegalArgumentException("config: direction '$d' must be forward or backward")
            },
            board = board, squares = stimuli.obj(board).arr("positions").size,
            fixationBeforeMs = o.int("fixation_before_ms"), highlightMs = o.int("highlight_ms"),
            interHighlightMs = o.int("inter_highlight_ms"), cueDelayMs = o.int("cue_delay_ms"), echoMs = o.int("echo_ms"),
            recallTimeoutMs = o.int("recall_timeout_ms"), itiMs = o.int("iti_ms"), startLength = o.int("start_length"),
            maxLength = o.int("max_length"), trialsPerLength = o.int("trials_per_length"),
            sequences = seqs.keys().associate { it.toInt() to seqs.intLists(it) },
            practiceSequences = pr.intLists("sequences"), practiceMinCorrect = pr.int("min_correct"),
        )
    }

    private fun trp(id: String, v: String, o: JObj): TrpConfig {
        val pr = o.obj("practice")
        return TrpConfig(
            id = id, version = v, stimulus = o.str("stimulus"), durationsMs = o.intList("durations_ms"),
            repetitions = o.int("repetitions"), maxConsecutiveSame = o.int("max_consecutive_same"),
            fixationBeforeMs = o.int("fixation_before_ms"), pauseBeforeResponseMs = o.int("pause_before_response_ms"),
            pressTimeoutMs = o.int("press_timeout_ms"), maxHoldMs = o.int("max_hold_ms"),
            prematureReleaseMs = o.int("premature_release_ms"), itiMs = o.int("iti_ms"),
            practiceDurationsMs = pr.intList("durations_ms"), practiceFeedbackMs = pr.int("feedback_ms"),
            practiceMinRatio = pr.num("min_ratio"), practiceMaxRatio = pr.num("max_ratio"),
        )
    }

    private fun cdt(id: String, v: String, o: JObj): CdtConfig {
        val ss = o.obj("smaller_sooner"); val ll = o.obj("larger_later")
        return CdtConfig(
            id = id, version = v, stimulus = o.str("stimulus"), ssTokens = ss.int("tokens"), ssDelayMs = ss.int("delay_ms"),
            llTokens = ll.int("tokens"), llDelayMs = ll.int("delay_ms"), postRewardDelayMs = o.int("post_reward_delay_ms"),
            rewardDisplayMs = o.int("reward_display_ms"), fixationBeforeMs = o.int("fixation_before_ms"),
            itiMs = o.int("iti_ms"), choiceTimeoutMs = o.int("choice_timeout_ms"),
            forced = o.obj("practice").strList("forced"), freeTrials = o.int("free_trials"),
        )
    }

    companion object {
        const val SCHEMA = "cognisense.tasks"

        fun parse(bytes: ByteArray): BatteryConfig {
            @Suppress("UNCHECKED_CAST")
            val root = JObj(Json.parse(bytes.decodeToString()) as? Map<String, Any?>
                ?: throw IllegalArgumentException("config: root must be an object"))
            require(root.str("schema") == SCHEMA) { "config: schema must be '$SCHEMA'" }
            val c = root.obj("common")
            val common = CommonConfig(
                anticipationMs = c.num("anticipation_ms"), blockBreakMs = c.long("block_break_ms"),
                betweenTaskRestMs = c.long("between_task_rest_ms"), maxPracticeAttempts = c.int("max_practice_attempts"),
                practiceFeedbackMs = c.long("practice_feedback_ms"), practiceFeedbackGapMs = c.long("practice_feedback_gap_ms"),
                resumeLeadInMs = c.long("resume_lead_in_ms"), refreshProbeFrames = c.int("refresh_probe_frames"),
            )
            val pool = root.objList("pool").map {
                PoolEntry(it.str("code"), it.str("id"), it.str("name"), it.str("construct"), it.str("measures"),
                    it.str("review_duration"), it.strList("parts"))
            }
            val cfg = BatteryConfig(
                configVersion = root.str("config_version"),
                sha256 = Portable.sha256Hex(bytes),
                common = common, coreOrder = root.strList("core_battery_order"), pool = pool,
                display = root.obj("display"), stimuli = root.obj("stimuli"), tasks = root.obj("tasks"),
            )
            // Fail fast: every referenced part must build in both variants; core order must name pool tasks.
            require(cfg.coreOrder.all { code -> pool.any { it.code == code } }) { "config: core_battery_order names an unknown task" }
            for (part in pool.flatMap { it.parts }) {
                cfg.standard(part); cfg.review(part)
                require(cfg.pads(part) in setOf("SINGLE", "LEFT_RIGHT", "BOARD")) { "config: bad pads for $part" }
            }
            return cfg
        }
    }
}
