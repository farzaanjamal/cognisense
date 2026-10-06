package org.cognisense.core.verify

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.engine.ChoiceStage
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseEvent
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.RunContext
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.tasks.FlankerTask
import org.cognisense.core.tasks.GoNoGoTask
import org.cognisense.core.tasks.SpatialSpanTask
import org.cognisense.core.util.Portable

/**
 * VERIFICATION UTILITY — not used by the app or the preview UI.
 *
 * Runs one task phase with a deterministic scripted child at a simulated 60 Hz and returns a digest
 * of every trial record and metric. The same function is compiled for the JVM (Android) and for
 * JavaScript (browser preview); identical digests for the same inputs show the two builds behave
 * identically (sequences, timing logic, outcome classification, scoring, number formatting).
 */
object CrossPlatformCheck {
    private const val PERIOD = 16_666_667L

    fun digest(cfg: BatteryConfig, partId: String, phase: Phase, seed: Long, review: Boolean): String {
        val def = if (review) cfg.review(partId) else cfg.standard(partId)
        val run = def.createRun(RunContext(phase, seed, 60.0, cfg.common))
        val rng = SeededRandom(seed xor 0x5EEDL)
        val pending = mutableListOf<ResponseEvent>()
        fun at(t: Long, key: ResponseKey?, target: Int? = null, release: Boolean = false) {
            pending += ResponseEvent(t, key, 0f, 0f, target = target, release = release)
        }
        var t = 1_000_000_000L
        var prev = FrameContent()
        val seen = mutableListOf<Int>()
        var stimStart = 0L
        var stimDur = 0L
        var frames = 0
        while (!run.isFinished) {
            require(++frames < 3_000_000) { "simulation did not finish" }
            pending.sortBy { it.eventTimeNanos }
            while (pending.isNotEmpty() && pending[0].eventTimeNanos <= t) run.onResponse(pending.removeAt(0))
            val c = run.onFrame(t)
            if (c.awaitingContinue) run.continueAfterBreak()
            // stimulus onset in timeline tasks
            if (c.stimulus != null && prev.stimulus == null) {
                stimStart = t
                when (def) {
                    is GoNoGoTask -> if (rng.nextDouble() < 0.8) at(t + (250 + rng.nextInt(450)) * 1_000_000L, ResponseKey.SINGLE)
                    is FlankerTask -> at(t + (350 + rng.nextInt(600)) * 1_000_000L,
                        if (rng.nextDouble() < 0.5) ResponseKey.LEFT else ResponseKey.RIGHT)
                }
            }
            if (c.stimulus == null && prev.stimulus != null) stimDur = t - stimStart
            // spatial span: watch, then recall (10% of trials with one swapped pair)
            if (c.highlight != null && c.highlight != prev.highlight) seen += c.highlight!!
            if (c.board != null && c.responseCue && !prev.responseCue) {
                val order = if ((def as SpatialSpanTask).config.backward) seen.reversed() else seen.toList()
                val taps = if (rng.nextDouble() < 0.1 && order.size > 1) order.toMutableList().also { it.add(it.removeAt(0)) } else order
                taps.forEachIndexed { k, target -> at(t + (500 + 400L * k) * 1_000_000L, null, target) }
                seen.clear()
            }
            // time reproduction: press, hold 0.7-1.3 x the observed duration, release
            if (c.board == null && c.responseCue && !prev.responseCue) {
                val press = t + 300 * 1_000_000L
                at(press, ResponseKey.SINGLE)
                at(press + (stimDur * (0.7 + 0.6 * rng.nextDouble())).toLong(), null, release = true)
            }
            // choice-delay: the active side; on free trials larger-later with p = 0.5
            val ch = c.choice
            if (ch != null && ch.stage == ChoiceStage.CHOOSE && prev.choice?.stage != ChoiceStage.CHOOSE) {
                val left = ch.left!!; val right = ch.right!!
                val side = when {
                    !left.active -> ResponseKey.RIGHT
                    !right.active -> ResponseKey.LEFT
                    (rng.nextDouble() < 0.5) == left.longDelay -> ResponseKey.LEFT
                    else -> ResponseKey.RIGHT
                }
                at(t + 700 * 1_000_000L, side)
            }
            prev = c
            t += PERIOD
        }
        val sb = StringBuilder()
        for (r in run.records()) {
            sb.append(r.taskId).append('|').append(r.taskVersion).append('|').append(r.trialSeq).append('|')
                .append(r.plan.condition).append('|').append(r.plan.stimulus).append('|').append(r.outcome).append('|')
                .append(r.rtMs?.let { Portable.fixed(it, 3) } ?: "-").append('|').append(r.stimulusFrames).append('|')
                .append(r.onsetNanos?.minus(r.trialStartNanos) ?: -1).append('|')
                .append(r.details.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }).append('\n')
        }
        for (m in def.score(run.records())) {
            sb.append(m.name).append('=').append(m.value?.let { Portable.fixed(it, 6) } ?: "null")
                .append(' ').append(m.timingClass).append(' ').append(m.n).append('\n')
        }
        return Portable.sha256Hex(sb.toString().encodeToByteArray()) + " " + run.records().size
    }
}
