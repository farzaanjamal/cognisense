package org.cognisense.core

import org.cognisense.core.data.CsvExport
import org.cognisense.core.data.JsonExport
import org.cognisense.core.data.ParticipantId
import org.cognisense.core.data.SessionMetadata
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.TrialEngine
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.motion.NoMotionSource
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Stats
import org.cognisense.core.tasks.FlankerTask
import org.cognisense.core.tasks.GoNoGoTask
import org.cognisense.core.timing.HardwareTimingSource
import org.cognisense.core.timing.SoftwareTimingSource
import kotlin.math.abs
import kotlin.system.exitProcess

fun testRng() {
    val g = SeededRandom(0)
    T.check("splitmix seed0 #1", g.nextLong() == -2152535657050944081L)
    T.check("splitmix seed0 #2", g.nextLong() == 7960286522194355700L)
    T.check("splitmix seed0 #3", g.nextLong() == 487617019471545679L)
    T.check("splitmix seed12345", SeededRandom(12345).nextLong() == 2454886589211414944L)
    T.check("fnv empty", SeededRandom.fnv1a64("") == -3750763034362895579L)
    T.check("fnv label", SeededRandom.fnv1a64("GNG:1.0:SCORED") == 2123787353528013946L)
    val a = SeededRandom.derive(42, "x"); val b = SeededRandom.derive(42, "x"); val c = SeededRandom.derive(42, "y")
    val sa = List(20) { a.nextInt(1000) }; val sb = List(20) { b.nextInt(1000) }; val sc = List(20) { c.nextInt(1000) }
    T.check("derive deterministic", sa == sb)
    T.check("derive label-specific", sa != sc)
    val r = SeededRandom(7)
    val counts = IntArray(5); repeat(50_000) { counts[r.nextInt(5)]++ }
    T.check("nextInt roughly uniform", counts.all { it in 9_400..10_600 }, counts.toList().toString())
}

fun testGngPlans() {
    val task = cfg.standard("GNG") as GoNoGoTask
    val p1 = task.plan(SeededRandom.derive(99, task.rngLabel(Phase.SCORED)), Phase.SCORED)
    val p2 = task.plan(SeededRandom.derive(99, task.rngLabel(Phase.SCORED)), Phase.SCORED)
    val p3 = task.plan(SeededRandom.derive(100, task.rngLabel(Phase.SCORED)), Phase.SCORED)
    T.check("gng plan deterministic", p1 == p2)
    T.check("gng plan seed-specific", p1.map { it.condition } != p3.map { it.condition })
    T.check("gng 128 trials", p1.size == 128)
    val expected = mapOf(1 to (24 to 8), 2 to (24 to 8), 3 to (24 to 8), 4 to (24 to 8))
    // v0.2 design: fast-slow-slow-fast, so a steady drift (fatigue, practice) affects both rates equally,
    // and equal trial and No-Go counts per rate, so both commission rates rest on the same number of trials.
    T.check("gng block order fast-slow-slow-fast", p1.groupBy { it.block }.toSortedMap().values.map { it.first().blockCondition } ==
        listOf("fast", "slow", "slow", "fast"))
    for (rate in listOf("fast", "slow")) {
        val r = p1.filter { it.blockCondition == rate }
        T.check("gng $rate rate: 64 trials, 16 No-Go", r.size == 64 && r.count { it.condition == "nogo" } == 16,
            "${r.size}/${r.count { it.condition == "nogo" }}")
    }
    T.check("gng mean block position equal for both rates",
        p1.filter { it.blockCondition == "fast" }.map { it.block }.distinct().average() ==
        p1.filter { it.blockCondition == "slow" }.map { it.block }.distinct().average())
    for ((b, plans) in p1.groupBy { it.block }) {
        val go = plans.count { it.condition == "go" }; val nogo = plans.count { it.condition == "nogo" }
        T.check("gng block $b counts", (go to nogo) == expected[b], "$go/$nogo")
        T.check("gng block $b leading go", plans.take(3).all { it.condition == "go" })
        T.check("gng block $b nogo runs <= 2", SeededRandom.longestRun(plans) { it.condition == "nogo" } <= 2)
        T.check("gng block $b lead-in", plans.first().foreperiodMs == 1000 && plans.drop(1).all { it.foreperiodMs == 0 })
    }
    T.check("gng slow endMs from config", p1.filter { it.block == 2 }.all { it.endMs == 4300 })
    val prac = task.plan(SeededRandom.derive(99, task.rngLabel(Phase.PRACTICE)), Phase.PRACTICE)
    T.check("gng practice 9/3", prac.count { it.condition == "go" } == 9 && prac.count { it.condition == "nogo" } == 3)
}

fun testFlankerPlans() {
    val task = cfg.standard("FLK") as FlankerTask
    val p = task.plan(SeededRandom.derive(5, task.rngLabel(Phase.SCORED)), Phase.SCORED)
    T.check("flanker 80 trials", p.size == 80)
    for (b in 1..2) {
        val blk = p.filter { it.block == b }
        T.check("flanker block $b balanced", blk.groupBy { it.condition to it.expectedResponse }.values.all { it.size == 10 })
        T.check("flanker block $b runs <= 3", FlankerTask.maxRun(blk.map { it.condition }) <= 3)
    }
    T.check("flanker foreperiod 400-800", p.all { it.foreperiodMs in 400..800 })
    T.check("flanker stimulus ids", p.all { it.stimulus == "flanker_${it.condition}_${it.expectedResponse!!.name.lowercase()}" })
}

fun testEngineTimingAndOutcomes() {
    val plans = listOf(gngPlan(0, "go"), gngPlan(1, "nogo", 0), gngPlan(2, "go", 0),
        gngPlan(3, "go", 0), gngPlan(4, "go", 500), gngPlan(5, "go", 0))
    val e = engineFor(plans)
    val sim = FrameSim(e, 60.0)
    val onsets = mutableListOf<Long>()
    sim.onsetHandler = { t, _ ->
        onsets += t
        when (onsets.size) {
            1 -> sim.respondAt(t + 400 * MS, ResponseKey.SINGLE)
            2 -> sim.respondAt(t + 350 * MS, ResponseKey.SINGLE)
            4 -> sim.respondAt(t + 100 * MS, ResponseKey.SINGLE)
            5 -> { sim.respondAt(t + 450 * MS, ResponseKey.SINGLE); sim.respondAt(t + 600 * MS, ResponseKey.SINGLE) }
            6 -> sim.respondAt(t + 1100 * MS, ResponseKey.SINGLE)
        }
    }
    var injected = false; var n = 0
    while (!e.isFinished) {
        // premature response for trial index 4: its onset = onset3 + 1300 + 500 ms; respond 100 ms before it
        if (!injected && onsets.size == 4) { sim.respondAt(onsets[3] + 1700 * MS, ResponseKey.SINGLE); injected = true }
        sim.frame(); require(++n < 100_000)
    }
    val r = e.records()
    T.check("6 records", r.size == 6)
    T.check("t0 correct", r[0].outcome == Outcome.CORRECT)
    T.near("t0 RT exact", r[0].rtMs, 400.0, 1e-6)
    T.check("t1 commission", r[1].outcome == Outcome.COMMISSION)
    T.check("t2 omission", r[2].outcome == Outcome.OMISSION && r[2].rtMs == null)
    T.check("t3 anticipation (threshold from config)", r[3].outcome == Outcome.ANTICIPATION)
    T.check("t4 premature counted", r[4].prematureResponses == 1, "${r[4].prematureResponses}")
    T.check("t4 correct with extra", r[4].outcome == Outcome.CORRECT && r[4].extraResponses == 1)
    T.check("t5 late -> omission", r[5].outcome == Outcome.OMISSION && r[5].lateResponses == 1)
    T.near("t5 late response latency kept", r[5].lateRtMs, 1100.0, 1e-6)
    T.check("no late latency when nothing was late", r.take(5).all { it.lateRtMs == null })
    T.near("first onset at 1000 ms", (r[0].onsetNanos!! - r[0].trialStartNanos) / 1e6, 1000.0, 8.4)
    T.check("18 stimulus frames", r.all { it.stimulusFrames == 18 }, r.map { it.stimulusFrames }.toString())
    T.check("SOA 1300 ms", (1 until r.size).filter { r[it].plan.foreperiodMs == 0 }
        .all { abs((r[it].onsetNanos!! - r[it - 1].onsetNanos!!) / 1e6 - 1300.0) <= 8.4 })
    T.check("marker mirrors stimulus", sim.markerMismatch == 0 && sim.markerFrames == sim.stimulusFrames)
    T.check("software timing attached", r[0].timing[SoftwareTimingSource.ID]?.rtMicros == 400_000L)
    T.check("releases ignored by timeline engine", run {
        val e2 = engineFor(listOf(gngPlan(0, "go"))); val s2 = FrameSim(e2, 60.0)
        s2.onsetHandler = { t, _ -> s2.releaseAt(t + 200 * MS); s2.respondAt(t + 400 * MS, ResponseKey.SINGLE) }
        s2.runToEnd(); e2.records().single().let { it.outcome == Outcome.CORRECT && it.extraResponses == 0 && e2.unassignedResponses == 0 }
    })
}

fun testRefreshRates() {
    for ((hz, expectFrames) in listOf(60.0 to 18, 90.0 to 27, 120.0 to 36, 144.0 to 43)) {
        val e = engineFor(listOf(gngPlan(0, "go"), gngPlan(1, "go", 0)), hz)
        FrameSim(e, hz).runToEnd()
        val r = e.records()
        T.check("$hz Hz frames", r.all { it.stimulusFrames == expectFrames }, r.map { it.stimulusFrames }.toString())
        T.check("$hz Hz duration within half frame", r.all { abs(it.measuredStimulusMs!! - 300.0) <= 1000.0 / hz / 2 + 1e-6 })
        T.check("$hz Hz no dropped", r.all { it.droppedFrames == 0 })
    }
}

fun testDroppedFrames() {
    val e = engineFor(listOf(gngPlan(0, "go")))
    val sim = FrameSim(e, 60.0)
    repeat(70) { sim.frame() }
    sim.frame(skip = 2)
    sim.runToEnd()
    val r = e.records().single()
    T.check("dropped frames counted", r.droppedFrames == 2 && e.droppedFramesTotal == 2, "${r.droppedFrames}")
    T.near("duration still ~300 ms", r.measuredStimulusMs, 300.0, 8.4)
}

fun testFlankerResponseTerminated() {
    val task = cfg.standard("FLK") as FlankerTask
    val plans = task.plan(SeededRandom(1), Phase.PRACTICE).take(2).mapIndexed { i, p -> p.copy(index = i) }
    val e = TrialEngine(task, ctx(Phase.SCORED), plans)
    val sim = FrameSim(e, 60.0)
    var k = 0
    sim.onsetHandler = { t, _ -> if (k++ == 0) sim.respondAt(t + 500 * MS, plans[0].expectedResponse) }
    sim.runToEnd()
    val r = e.records()
    T.check("flanker t0 correct", r[0].outcome == Outcome.CORRECT)
    T.near("flanker RT 500", r[0].rtMs, 500.0, 1e-6)
    val lag = (r[0].offsetNanos!! - r[0].responseNanos!!) / 1e6
    T.check("offset on first frame after response", lag > 0 && lag <= 16.7, "$lag")
    T.near("ITI 500 ms", (r[0].endNanos - r[0].offsetNanos!!) / 1e6, 500.0, 8.4)
    T.check("flanker t1 omission after 2000 ms window", r[1].outcome == Outcome.OMISSION)
    T.near("window close at 2000", r[1].measuredStimulusMs, 2000.0, 8.4)
}

fun testInterruptionAndBreak() {
    val plans = listOf(gngPlan(0, "go"), gngPlan(1, "go", 0), gngPlan(2, "go", 1000, block = 2))
    val e = engineFor(plans, breakMs = 10_000)
    val sim = FrameSim(e, 60.0)
    repeat(70) { sim.frame() }
    val interruptedAt = sim.t
    e.onInterruption(sim.t)
    e.onInterruption(sim.t) // focus loss + onPause for the same event
    T.check("paused", e.isPaused && e.onFrame(sim.t + 1).paused)
    sim.t += 5_000 * MS
    e.resume()
    sim.runToEnd()
    val r = e.records()
    T.check("interrupted trial recorded", r[0].outcome == Outcome.INTERRUPTED && r[0].interrupted)
    T.check("no dropped frames from pause gap", e.droppedFramesTotal == 0, "${e.droppedFramesTotal}")
    T.check("all trials logged; interruption counted once", r.size == 3 && e.interruptions == 1)
    T.check("1 s fixation lead-in after resume", (r[1].onsetNanos!! - interruptedAt) / 1e6 >= 6_000.0 - 8.4)
    T.check("block break >= 10 s", (r[2].trialStartNanos - r[1].endNanos) / 1e6 >= 10_000.0)
}

fun testPracticeFeedbackAndVariants() {
    val plans = listOf(gngPlan(0, "go"), gngPlan(1, "nogo", 0))
    val e = engineFor(plans, phase = Phase.PRACTICE, attempt = 2)
    val sim = FrameSim(e, 60.0)
    sim.onsetHandler = { t, _ -> sim.respondAt(t + 400 * MS, ResponseKey.SINGLE) } // go: correct; nogo: commission
    val feedback = mutableListOf<Boolean>(); var prev: Boolean? = null
    while (!e.isFinished) {
        sim.frame()
        val f = sim.lastContent.feedback
        if (f != null && prev == null) feedback += f
        prev = f
    }
    T.check("feedback after each practice trial", feedback == listOf(true, false), feedback.toString())
    T.check("attempt recorded", e.records().all { it.attempt == 2 })
    T.near("feedback + gap from config (500 + 500 ms)", (e.records()[1].trialStartNanos - e.records()[0].endNanos) / 1e6, 1000.0, 8.4)
    val scored = engineFor(plans); val s2 = FrameSim(scored, 60.0)
    var anyFeedback = false
    while (!scored.isFinished) { s2.frame(); if (s2.lastContent.feedback != null) anyFeedback = true }
    T.check("no feedback in scored phase", !anyFeedback)
    T.check("review variant versions", cfg.review("GNG").version == "0.2-review" && cfg.review("FLK").version == "0.1-review"
        && cfg.standard("GNG").version == "0.2")
    T.check("review GNG 24 trials", (cfg.review("GNG") as GoNoGoTask).plan(SeededRandom(1), Phase.SCORED).size == 24)
    T.check("review FLK 16 trials", (cfg.review("FLK") as FlankerTask).plan(SeededRandom(1), Phase.SCORED).size == 16)
}

fun testHardwareStubs() {
    T.throws("hardware timing is DESIGNED-only") { HardwareTimingSource().armTrial(1) }
    T.check("hardware status label", HardwareTimingSource().info.status == "DESIGNED")
    val m = NoMotionSource(); m.start("s")
    T.check("no-motion source runs", m.stop()?.samples == 0L)
}

fun testStats() {
    for ((p, z) in listOf(0.001 to -3.090232306167813, 0.02 to -2.0537489106318225, 0.5 to 0.0,
        0.8 to 0.8416212335729144, 0.975 to 1.9599639845400536, 0.999 to 3.090232306167813)) {
        T.near("probit($p)", Stats.probit(p), z, 1e-6)
    }
    T.near("dprime log-linear", Stats.dPrime(8, 10, 1, 4), 1.2722591074713427, 1e-6)
    T.near("median even", Stats.median(listOf(4.0, 1.0, 3.0, 2.0)), 2.5, 1e-12)
    T.near("sd", Stats.sd(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)), 2.138089935299395, 1e-9)
}

fun sessionFor(seed: Long) = SessionMetadata(
    participantId = ParticipantId.generate(SeededRandom(3)), sessionId = "test-session", mode = "SESSION",
    startedAtUtc = "2026-10-02T09:00:00Z", deviceManufacturer = "Sim", deviceModel = "Frame,Sim \"60\"",
    osVersion = "n/a", sdkInt = 0, screenWidthPx = 1600, screenHeightPx = 720, xdpi = 400f, ydpi = 400f,
    nominalRefreshHz = 60.0, measuredRefreshHz = 60.0, appVersion = "0.1.0-dev", gitCommit = "uncommitted",
    taskConfigVersion = cfg.configVersion, taskConfigSha256 = cfg.sha256,
    sessionSeed = seed, brightness = 0.8f, uiLanguage = "ur", onsetMarkerEnabled = false,
    primaryTimingSource = SoftwareTimingSource.ID, timingSources = listOf(SoftwareTimingSource().info),
    motionSource = NoMotionSource().info, clockCheckNanos = null, doNotDisturbActive = null,
)

fun testFullGngRun(): List<TrialRecord> {
    val task = cfg.standard("GNG")
    val e = task.createRun(ctx(Phase.SCORED, seed = 2026))
    val sim = FrameSim(e, 60.0)
    val child = SeededRandom(11)
    sim.onsetHandler = { t, c ->
        val isGo = c.stimulus == "gng_go_circle"
        if (if (isGo) child.nextDouble() < 0.95 else child.nextDouble() < 0.25)
            sim.respondAt(t + (380 + child.nextInt(240)) * MS, ResponseKey.SINGLE)
    }
    sim.runToEnd()
    val r = e.records()
    T.check("full run 128 records", r.size == 128)
    // Arithmetic expectation: 2*(1000+32*1300) + 2*(1000+32*4300) ms of trials + 3 breaks of 10 s
    T.near("full run duration (simulated) ~392.4 s", (r.last().endNanos - r.first().trialStartNanos) / 1e9, 392.4, 0.5)
    val m = task.score(r).associateBy { it.name }
    T.check("scores computed", m["d_prime"]?.value != null && m["event_rate_median_rt_effect"]?.value != null)
    T.check("commission rate plausible", m["commission_rate"]!!.value!! in 0.05..0.5)
    T.check("timing classes present", m["isd_go_rt"]!!.timingClass.name == "B" && m["cv_go_rt"]!!.timingClass.name == "C")
    return r
}

fun testExportAndIds(records: List<TrialRecord>) {
    val session = sessionFor(2026)
    T.check("csv escape", CsvExport.escape("a,\"b\"") == "\"a,\"\"b\"\"\"")
    val csv = CsvExport.trials(session, records)
    val lines = csv.trimEnd().split("\r\n")
    T.check("csv rows", lines.size == records.size + 1)
    T.check("csv header", lines[0].split(",").size == CsvExport.TRIAL_COLUMNS.size)
    T.check("csv has config hash and details columns", "task_config_sha256" in CsvExport.TRIAL_COLUMNS && CsvExport.TRIAL_COLUMNS.last() == "details")
    val tmp = System.getProperty("java.io.tmpdir")
    java.io.File(tmp, "cognisense_test_export.csv").writeText(csv)
    java.io.File(tmp, "cognisense_test_export.json").writeText(
        JsonExport.session(session, mapOf("GNG" to (records to cfg.standard("GNG").score(records)))))
    val rng = SeededRandom(9)
    val ids = List(500) { ParticipantId.generate(rng) }
    T.check("ids valid", ids.all { ParticipantId.isValid(it) })
    T.check("ids contain no vowels", ids.none { id -> id.drop(3).any { it in "AEIOU" } })
    T.check("typed name rejected", !ParticipantId.isValid("ALI-123") && !ParticipantId.isValid("CS-AHMED1"))
}

fun main() {
    testRng(); testConfig(); testGngPlans(); testFlankerPlans(); testEngineTimingAndOutcomes(); testRefreshRates()
    testDroppedFrames(); testFlankerResponseTerminated(); testInterruptionAndBreak(); testPracticeFeedbackAndVariants()
    testHardwareStubs(); testStats()
    testSpan(); testTimeReproduction(); testChoiceDelay()
    val records = testFullGngRun()
    testExportAndIds(records)
    println("config ${cfg.configVersion} sha256=${cfg.sha256}")
    println("passed: ${T.passed}")
    if (T.failures.isNotEmpty()) {
        println("FAILED: ${T.failures.size}"); T.failures.forEach { println("  - $it") }; exitProcess(1)
    }
    println("all tests passed")
}
