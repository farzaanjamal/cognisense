package org.cognisense.core

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.config.JObj
import org.cognisense.core.config.Json
import org.cognisense.core.engine.ChoiceStage
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.tasks.ChoiceDelayTask
import org.cognisense.core.tasks.SpanSequences
import org.cognisense.core.tasks.SpatialSpanTask
import org.cognisense.core.tasks.TimeReproductionTask
import org.cognisense.core.util.Portable
import kotlin.math.abs

// ---------------------------------------------------------------- config

fun testPortable() {
    // FIPS 180-4 / NIST test vectors
    T.check("sha256('')", Portable.sha256Hex(ByteArray(0)) == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    T.check("sha256('abc')", Portable.sha256Hex("abc".encodeToByteArray()) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    T.check("sha256(448-bit)", Portable.sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()) ==
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1")
    val bytes = CONFIG_FILE.readBytes()
    val jdk = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    T.check("sha256 of config matches the JDK", Portable.sha256Hex(bytes) == jdk)
    for (n in listOf(55, 56, 63, 64, 65, 1000)) {
        val b = ByteArray(n) { (it * 7).toByte() }
        T.check("sha256 length $n matches the JDK", Portable.sha256Hex(b) ==
            java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) })
    }
    T.check("fixed 3dp", Portable.fixed(400.0, 3) == "400.000" && Portable.fixed(0.9, 3) == "0.900")
    T.check("fixed rounding", Portable.fixed(2.5, 0) == "3" && Portable.fixed(-1.25, 1) == "-1.3" && Portable.fixed(-0.0004, 3) == "0.000")
    T.check("fixed large", Portable.fixed(1234567.891, 2) == "1234567.89")
}

/** Guards against encoding corruption of the Urdu drafts (it happened once: a non-UTF-8 console turned them into '?'). */
fun testStringsFile() {
    val f = java.io.File(CONFIG_FILE.parentFile, "strings.json")
    @Suppress("UNCHECKED_CAST")
    val strings = JObj(Json.parse(f.readBytes().decodeToString()) as Map<String, Any?>).obj("strings")
    val arabic = Regex("[\\u0600-\\u06FF]")
    val bad = strings.keys().filter { k ->
        val ur = strings.obj(k).str("ur"); val en = strings.obj(k).str("en")
        k != "lang_switch" && (!arabic.containsMatchIn(ur) || (ur.count { it == '?' } > en.count { it == '?' }))
    }
    T.check("every Urdu string is Arabic-script text (no encoding damage)", bad.isEmpty(), bad.take(5).toString())
    T.check("strings file has both languages for every key", strings.keys().all { k -> strings.obj(k).let { it.has("en") && it.has("ur") } })
}

fun testConfig() {
    testPortable()
    testStringsFile()
    // JSON parser
    @Suppress("UNCHECKED_CAST")
    val doc = Json.parse("""{"a":[1,-2.5e3,true,null],"s":"q\"\\\/\n\u00e9","o":{}}""") as Map<String, Any?>
    T.check("json numbers/literals", doc["a"] == listOf(1.0, -2500.0, true, null))
    T.check("json escapes", doc["s"] == "q\"\\/\né")
    for (bad in listOf("""{"a":1,}""", """{"a":1,"a":2}""", "[01]", """{"a":"\x"}""", "[1] 2", """{"a" 1}""", "\"\u0001\""))
        T.throws("json rejects $bad") { Json.parse(bad) }

    // shipped config
    T.check("config version", cfg.configVersion == "0.2")
    T.check("config sha256 is 64 hex", cfg.sha256.length == 64 && cfg.sha256.all { it in "0123456789abcdef" })
    T.check("config hash stable", BatteryConfig.parse(CONFIG_FILE.readBytes()).sha256 == cfg.sha256)
    T.check("pool has 10 tasks", cfg.pool.size == 10)
    T.check("core order", cfg.coreOrder == listOf("T2", "T6", "T5", "T9", "T10"))
    T.check("all core tasks implemented", cfg.coreOrder.all { cfg.poolEntry(it).parts.isNotEmpty() })
    T.check("common from config", cfg.common.anticipationMs == 150.0 && cfg.common.blockBreakMs == 10_000L)

    // sequence provenance: the fixed sequences must equal the documented generator's output
    for ((id, max) in listOf("SPF" to 9, "SPB" to 8)) {
        val c = (cfg.standard(id) as SpatialSpanTask).config
        val gen = SpanSequences.generate(20261002L, id, 2, max, 2, 9)
        T.check("$id sequences match generator", c.sequences == gen)
        T.check("$id practice matches generator",
            c.practiceSequences == SpanSequences.generatePractice(20261002L, "$id:practice", 2, 2, 9, gen.values.flatten().toSet()))
    }

    // validation fails with the JSON path
    val text = CONFIG_FILE.readText()
    fun rejects(name: String, from: String, to: String, expectInMessage: String) {
        require(from in text) { "test setup: '$from' not in config" }
        val msg = try { BatteryConfig.parse(text.replace(from, to).toByteArray()); null } catch (e: Exception) { e.message ?: "" }
        T.check("config rejects $name", msg != null && expectInMessage in msg, "message: $msg")
    }
    rejects("missing key", "\"stimulus_ms\": 300", "\"stimulus_msX\": 300", "tasks.GNG.stimulus_ms")
    rejects("fractional No-Go count", "\"nogo_fraction\": 0.25", "\"nogo_fraction\": 0.3", "not a whole number")
    rejects("practice sequence equal to a scored one", "[[0, 1], [8, 2]]", "[[8, 4], [8, 2]]", "duplicates a scored")
    rejects("unknown task kind", "\"kind\": \"flanker\"", "\"kind\": \"flankr\"", "unknown task kind")
    T.check("JObj merge overrides", JObj(mapOf("a" to 1.0, "b" to 2.0)).merged(JObj(mapOf("b" to 3.0))).num("b") == 3.0)
}

// ---------------------------------------------------------------- spatial span

/** Simulated child: watches the highlights, then taps them (reversed if backward). Wrong above [maxCorrectLength]. */
class SpanChild(
    private val sim: FrameSim, private val backward: Boolean, private val maxCorrectLength: Int,
    private val latencyMs: Long = 600, private val interTapMs: Long = 400,
) {
    private val seen = mutableListOf<Int>()
    private var lastHighlight: Int? = null
    private var inRecall = false
    var silentTrials = setOf<Int>() // trial numbers (0-based) on which the child never taps
    var trial = 0
    var tapDuringPresentationOnTrial: Int? = null

    fun attach() {
        sim.onContent = { t, c -> observe(t, c) }
    }

    private fun observe(t: Long, c: FrameContent) {
        if (c.board == null && !inRecall) seen.clear() // a paused or fixation screen restarts the child's memory
        if (c.highlight != null && c.highlight != lastHighlight) {
            seen += c.highlight!!
            if (tapDuringPresentationOnTrial == trial && seen.size == 1) sim.respondAt(t + 100 * MS, null, target = 0)
        }
        lastHighlight = c.highlight
        if (c.responseCue && !inRecall) {
            inRecall = true
            if (trial !in silentTrials) {
                val order = if (backward) seen.reversed() else seen.toList()
                val taps = if (seen.size > maxCorrectLength) order.toMutableList().also { it.add(it.removeAt(0)) } else order
                taps.forEachIndexed { k, target -> sim.respondAt(t + (latencyMs + k * interTapMs) * MS, null, target = target) }
            }
        }
        if (!c.responseCue && inRecall) { inRecall = false; seen.clear(); trial++ }
    }
}

fun runSpan(id: String, phase: Phase, maxCorrect: Int, review: Boolean = false, setup: (SpanChild, FrameSim, TaskRun) -> Unit = { _, _, _ -> }): Pair<TaskRun, FrameSim> {
    val task = (if (review) cfg.review(id) else cfg.standard(id)) as SpatialSpanTask
    val run = task.createRun(ctx(phase))
    val sim = FrameSim(run, 60.0)
    val child = SpanChild(sim, task.config.backward, maxCorrect)
    child.attach(); setup(child, sim, run)
    sim.runToEnd()
    return run to sim
}

fun testSpan() {
    val spf = cfg.standard("SPF") as SpatialSpanTask
    val (run, sim) = runSpan("SPF", Phase.SCORED, maxCorrect = 5)
    val r = run.records()
    T.check("span fwd: lengths 2..6 administered, stops after two failures at 6",
        r.map { it.details["length"]!!.toInt() } == listOf(2, 2, 3, 3, 4, 4, 5, 5, 6, 6), r.map { it.details["length"] }.toString())
    T.check("span fwd: sequences come from config", r.all { rec ->
        rec.details["sequence"] == spf.config.sequences.getValue(rec.details.getValue("length").toInt())[rec.details.getValue("slot").toInt()].joinToString("-") })
    T.check("span fwd: outcomes", r.take(8).all { it.outcome == Outcome.CORRECT } && r.drop(8).all { it.outcome == Outcome.INCORRECT })
    val m = spf.score(r).associateBy { it.name }
    T.check("span fwd: span 5, total 8, product 40",
        m["span"]!!.value == 5.0 && m["total_correct"]!!.value == 8.0 && m["product_score"]!!.value == 40.0)
    T.check("span: 700 ms highlights = 42 frames per item at 60 Hz", r.all { it.stimulusFrames == 42 * it.details["length"]!!.toInt() },
        r.map { it.stimulusFrames }.toString())
    T.check("span: marker on exactly during highlights", sim.markerFrames == r.sumOf { it.stimulusFrames })
    T.near("span: first-tap latency from recall cue", r[0].details["first_tap_latency_ms"]!!.toDouble(), 600.0, 1e-6)
    T.check("span: tap timestamps logged per tap", r[3].details["tap_latencies_ms"]!!.split(";").size == 3)

    val (runB, _) = runSpan("SPB", Phase.SCORED, maxCorrect = 3)
    val mb = cfg.standard("SPB").score(runB.records()).associateBy { it.name }
    T.check("span bwd: reversed taps scored correct; span 3, total 4", mb["span"]!!.value == 3.0 && mb["total_correct"]!!.value == 4.0)

    val (runR, _) = runSpan("SPF", Phase.SCORED, maxCorrect = 9, review = true)
    T.check("span review: stops at review max length 5", runR.records().size == 8 && cfg.review("SPF").version == "0.1-review")

    val (runP, simP) = runSpan("SPF", Phase.PRACTICE, maxCorrect = 9)
    T.check("span practice: 2 trials, criterion met", runP.records().size == 2 && cfg.standard("SPF").practiceCriterionMet(runP.records()))
    var fb = 0; simP.onContent = null
    T.check("span practice sequences from config", runP.records().map { it.details["sequence"] } == spf.config.practiceSequences.map { it.joinToString("-") })

    // timeout, premature tap, echo
    val (runT, _) = runSpan("SPF", Phase.SCORED, maxCorrect = 9) { child, _, _ -> child.silentTrials = setOf(0); child.tapDuringPresentationOnTrial = 1 }
    val rt = runT.records()
    T.check("span: no taps -> OMISSION after 30 s", rt[0].outcome == Outcome.OMISSION &&
        abs((rt[0].endNanos - rt[0].offsetNanos!!) / 1e6 - (500 + 30_000)) <= 17, "${(rt[0].endNanos - rt[0].offsetNanos!!) / 1e6}")
    T.check("span: tap during presentation counted premature, trial still scored", rt[1].prematureResponses == 1 && rt[1].outcome == Outcome.CORRECT)

    val echoRun = (cfg.standard("SPF") as SpatialSpanTask).createRun(ctx(Phase.SCORED))
    val es = FrameSim(echoRun, 60.0); val ec = SpanChild(es, false, 9); ec.attach()
    var echoFrames = 0
    val inner = es.onContent
    es.onContent = { t, c -> inner?.invoke(t, c); if (c.echo != null) echoFrames++ }
    while (echoRun.records().isEmpty()) es.frame()
    T.check("span: each tap echoes ~200 ms", echoFrames in 23..25, "$echoFrames frames for 2 taps") // 2 taps x 12 frames

    // interruption: same sequence re-administered
    var interruptedOnce = false
    val (runI, _) = runSpan("SPF", Phase.SCORED, maxCorrect = 5) { child, s, run ->
        val prev = s.onContent
        s.onContent = { t, c ->
            prev?.invoke(t, c)
            if (!interruptedOnce && child.trial == 2 && c.highlight != null) {
                interruptedOnce = true; run.onInterruption(t); run.onInterruption(t); s.t += 3_000 * MS; run.resume()
            }
        }
    }
    val ri = runI.records()
    T.check("span: interrupted trial logged", ri[2].outcome == Outcome.INTERRUPTED, ri.map { it.outcome }.toString())
    T.check("span: same sequence re-administered", ri[3].details["sequence"] == ri[2].details["sequence"] && ri[3].outcome == Outcome.CORRECT)
    T.check("span: re-administered trial flagged in data", ri[3].details["readministered_after_interruption"] == "true" &&
        ri.filterIndexed { i, _ -> i != 3 }.none { it.details["readministered_after_interruption"] == "true" })
    T.check("span: interruption does not change the procedure", ri.count { it.outcome != Outcome.INTERRUPTED } == 10 && runI.interruptions == 1)
}

// ---------------------------------------------------------------- time reproduction

/** Simulated child: presses [pressLatencyMs] after the pad appears and holds for factor x D (per-trial overrides allowed). */
fun runTrp(task: TimeReproductionTask, phase: Phase, factor: Double, policy: (Int) -> String = { "normal" }): Pair<TaskRun, FrameSim> {
    val run = task.createRun(ctx(phase, seed = 77))
    val sim = FrameSim(run, 60.0)
    var trial = 0; var cueSeen = false; var stimStart = 0L; var target = 0L
    sim.onContent = { t, c ->
        if (c.stimulus != null && stimStart == 0L) stimStart = t
        if (c.stimulus == null && stimStart != 0L && target == 0L) target = t - stimStart
        if (c.responseCue && !cueSeen) {
            cueSeen = true
            val d = Math.round(target / 1e6 / 1000.0) * 1000L // nominal D from the observed duration
            when (policy(trial)) {
                "normal" -> { sim.respondAt(t + 300 * MS, ResponseKey.SINGLE); sim.releaseAt(t + 300 * MS + (factor * d * MS).toLong()) }
                "premature_release" -> { sim.respondAt(t + 300 * MS, ResponseKey.SINGLE); sim.releaseAt(t + 400 * MS) }
                "never_release" -> sim.respondAt(t + 300 * MS, ResponseKey.SINGLE)
                "other_finger_release" -> { sim.respondAt(t + 300 * MS, ResponseKey.SINGLE, pointer = 0); sim.releaseAt(t + 500 * MS, pointer = 1)
                    sim.releaseAt(t + 300 * MS + (factor * d * MS).toLong(), pointer = 0) }
                "no_press" -> {}
            }
        }
        if (!c.responseCue && cueSeen) { cueSeen = false; trial++; stimStart = 0L; target = 0L }
    }
    sim.runToEnd()
    return run to sim
}

fun testTimeReproduction() {
    val trp = cfg.standard("TRP") as TimeReproductionTask
    val (run, _) = runTrp(trp, Phase.SCORED, factor = 0.9)
    val r = run.records()
    T.check("trp: 16 trials, 4 per duration", r.size == 16 && r.groupBy { it.details["target_ms"] }.values.all { it.size == 4 })
    T.check("trp: <= 2 consecutive same duration", TimeReproductionTask.maxRunOf(r.map { it.details["target_ms"]!!.toInt() }) <= 2)
    T.check("trp: all completed", r.all { it.outcome == Outcome.CORRECT }, r.map { it.outcome }.toString())
    T.check("trp: displayed duration within half a frame of D", r.all {
        abs(it.details["displayed_ms"]!!.toDouble() - it.details["target_ms"]!!.toDouble()) <= 8.34 })
    T.check("trp: reproduced = release - press exactly", r.all {
        abs(it.details["reproduced_ms"]!!.toDouble() - 0.9 * it.details["target_ms"]!!.toDouble()) < 0.001 })
    val m = trp.score(r).associateBy { it.name }
    T.near("trp: ratio 0.9 at every D", m["ratio_D4000"]!!.value, 0.9, 1e-9)
    T.near("trp: Vierordt slope 0.9", m["vierordt_slope"]!!.value, 0.9, 1e-9)
    T.near("trp: precision 0 for identical reproductions", m["precision_D1000"]!!.value, 0.0, 1e-9)
    T.check("trp: marker on during the circle", r.all { it.stimulusFrames > 0 })

    val (runP, simP) = runTrp(trp, Phase.PRACTICE, factor = 0.9)
    T.check("trp practice: 3 fixed durations, criterion met", runP.records().map { it.details["target_ms"] } == listOf("2000", "4000", "1000")
        && trp.practiceCriterionMet(runP.records()))
    val (runPf, _) = runTrp(trp, Phase.PRACTICE, factor = 5.0)
    T.check("trp practice: 5x over-reproduction fails criterion", !trp.practiceCriterionMet(runPf.records()))
    var bars: Pair<Double, Double>? = null
    val run2 = trp.createRun(ctx(Phase.PRACTICE, seed = 77)); val s2 = FrameSim(run2, 60.0)
    var cue = false
    s2.onContent = { t, c ->
        if (c.responseCue && !cue) { cue = true; s2.respondAt(t + 300 * MS, ResponseKey.SINGLE); s2.releaseAt(t + 2100 * MS) }
        if (c.durationFeedback != null && bars == null) bars = c.durationFeedback
    }
    while (run2.records().isEmpty() || bars == null) s2.frame()
    T.check("trp practice: bar feedback shows (target, reproduced)", bars == (2000.0 to 1800.0), "$bars")

    val edge = runTrp(trp, Phase.SCORED, 0.9) { i -> listOf("premature_release", "no_press", "never_release", "other_finger_release").getOrElse(i) { "normal" } }.first.records()
    T.check("trp: premature release -> ANTICIPATION", edge[0].outcome == Outcome.ANTICIPATION && edge[0].details["flag"] == "premature_release")
    T.check("trp: no press -> OMISSION after 10 s", edge[1].outcome == Outcome.OMISSION)
    T.check("trp: never released -> INCORRECT after max hold", edge[2].outcome == Outcome.INCORRECT && edge[2].details["flag"] == "held_too_long")
    T.check("trp: another finger's release is ignored", edge[3].outcome == Outcome.CORRECT &&
        abs(edge[3].details["ratio"]!!.toDouble() - 0.9) < 0.001)
    val me = trp.score(edge).associateBy { it.name }
    T.check("trp: edge counts", me["premature_releases"]!!.value == 1.0 && me["omissions"]!!.value == 1.0 && me["held_too_long"]!!.value == 1.0)
    T.check("trp review: one trial per duration", (cfg.review("TRP") as TimeReproductionTask).let { t ->
        runTrp(t, Phase.SCORED, 1.0).first.records().size == 4 })
}

// ---------------------------------------------------------------- choice-delay

/** Simulated child: choices by policy ("LL", "SS", "none"); optionally taps the inactive side first. */
fun runCdt(task: ChoiceDelayTask, phase: Phase, choose: (Int) -> String, tapInactiveFirst: Boolean = false,
           onFrame: (Long, FrameContent, TaskRun, FrameSim) -> Unit = { _, _, _, _ -> }): Pair<TaskRun, FrameSim> {
    val run = task.createRun(ctx(phase, seed = 31))
    val sim = FrameSim(run, 60.0)
    var trial = 0; var inChoice = false
    sim.onContent = { t, c ->
        val ch = c.choice
        if (ch != null && ch.stage == ChoiceStage.CHOOSE && !inChoice) {
            inChoice = true
            val left = ch.left!!; val right = ch.right!!
            val want = choose(trial)
            val wantLong = want == "LL"
            val side = when {
                !left.active -> ResponseKey.RIGHT
                !right.active -> ResponseKey.LEFT
                left.longDelay == wantLong -> ResponseKey.LEFT
                else -> ResponseKey.RIGHT
            }
            if (tapInactiveFirst && (!left.active || !right.active))
                sim.respondAt(t + 400 * MS, if (side == ResponseKey.LEFT) ResponseKey.RIGHT else ResponseKey.LEFT)
            if (want != "none") sim.respondAt(t + 800 * MS, side)
        }
        if ((ch == null || ch.stage != ChoiceStage.CHOOSE) && inChoice) { inChoice = false; trial++ }
        onFrame(t, c, run, sim)
    }
    sim.runToEnd()
    return run to sim
}

fun testChoiceDelay() {
    val cdt = cfg.standard("CDT") as ChoiceDelayTask
    var waitShowsOnlyChosen = true; var rewardTokens = mutableListOf<Int>()
    val (runP, _) = runCdt(cdt, Phase.PRACTICE, { "x" }, tapInactiveFirst = true) { _, c, _, _ ->
        val ch = c.choice
        if (ch != null && ch.stage == ChoiceStage.WAIT && (ch.left != null) == (ch.right != null)) waitShowsOnlyChosen = false
        if (ch != null && ch.stage == ChoiceStage.REWARD && (rewardTokens.isEmpty() || rewardTokens.last() != ch.rewardTokens)) rewardTokens += ch.rewardTokens
    }
    val p = runP.records()
    T.check("cdt practice: forced choices follow config order", p.map { it.details["choice"] } == listOf("SS", "LL", "SS", "LL"))
    T.check("cdt practice: taps on the inactive side ignored", p.all { it.offTargetTouches == 1 && it.outcome == Outcome.CORRECT })
    T.check("cdt practice: criterion met", cdt.practiceCriterionMet(p))
    T.check("cdt: wait display shows only the chosen option (static)", waitShowsOnlyChosen)
    T.check("cdt: reward tokens 1 and 2, no running total", rewardTokens.toSet() == setOf(1, 2) && rewardTokens.maxOrNull() == 2)
    for (rec in p) {
        val delay = rec.details["delay_ms"]!!.toDouble()
        T.near("cdt: ${rec.details["choice"]} trial = choice latency + delay + reward display",
            (rec.endNanos - rec.onsetNanos!!) / 1e6, 800 + delay + 1000, 17.0)
    }

    val (runLL, _) = runCdt(cdt, Phase.SCORED, { "LL" })
    val ll = runLL.records()
    T.check("cdt free: 12 trials, sides balanced 6/6", ll.size == 12 && ll.count { it.plan.blockCondition == "ss_left" } == 6)
    val mLL = cdt.score(ll).associateBy { it.name }
    T.check("cdt free: always-LL child -> proportion 1.0", mLL["prop_larger_later"]!!.value == 1.0)
    T.near("cdt free: choice latency recorded as RT", mLL["median_choice_latency"]!!.value, 800.0, 1e-6)
    val (runSS, _) = runCdt(cdt, Phase.SCORED, { "SS" })
    val mSS = cdt.score(runSS.records()).associateBy { it.name }
    T.check("cdt free: always-SS child -> proportion 0.0 and a shorter task",
        mSS["prop_larger_later"]!!.value == 0.0 && mSS["scored_duration"]!!.value!! < mLL["scored_duration"]!!.value!! - 150)

    var interrupted = false; var leadInFixation = 0
    val (runE, _) = runCdt(cdt, Phase.SCORED, { i -> if (i == 0) "none" else "LL" }) { t, c, run, sim ->
        if (!interrupted && c.choice?.stage == ChoiceStage.WAIT) {
            interrupted = true
            sim.respondAt(t + 100 * MS, ResponseKey.LEFT) // impatient tap while waiting (before the interruption)
            run.onInterruption(t + 5_000 * MS); run.onInterruption(t + 5_000 * MS); sim.t += 2_000 * MS; run.resume()
        }
        if (interrupted && c.fixation && c.choice == null) leadInFixation++
    }
    val e = runE.records()
    T.check("cdt: no choice -> OMISSION after 30 s", e[0].outcome == Outcome.OMISSION &&
        abs((e[0].endNanos - e[0].onsetNanos!!) / 1e6 - 30_000) <= 17)
    T.check("cdt: interruption during the wait -> INTERRUPTED, counted once", e[1].outcome == Outcome.INTERRUPTED && runE.interruptions == 1)
    T.check("cdt: task continues after resume", e.size == 12 && e.drop(2).all { it.outcome == Outcome.CORRECT })
    T.check("cdt review: 2 forced + 4 free", (cfg.review("CDT") as ChoiceDelayTask).let { t ->
        runCdt(t, Phase.PRACTICE, { "x" }).first.records().size == 2 && runCdt(t, Phase.SCORED, { "LL" }).first.records().size == 4 })
}
