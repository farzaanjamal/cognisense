package org.cognisense.app.data

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.data.ParticipantId
import org.cognisense.core.data.SessionMetadata
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.motion.NoMotionSource
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.timing.SoftwareTimingSource
import org.cognisense.core.verify.CrossPlatformCheck
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Storage round trip: every value the app stores must come back unchanged.
 *
 * Trial records come from the core's scripted child (the same driver as the cross-platform check)
 * running every part of all five core tasks, practice and scored, standard and review variants.
 * Each record and the session metadata are encoded with [Codec], written as JSON text (as the app
 * stores them in SQLite), parsed and decoded, and compared field by field (data-class equality).
 *
 * org.json is part of the Android framework; this JVM test uses the org.json library instead,
 * because the android.jar on the unit-test classpath contains only stubs.
 */
class CodecRoundTripTest {
    private val cfg: BatteryConfig by lazy {
        val f = File(System.getProperty("cognisense.config") ?: "../../config/tasks.json")
        BatteryConfig.parse(f.readBytes())
    }

    private fun scriptedRecords(): List<TrialRecord> =
        listOf("GNG", "SPF", "SPB", "FLK", "TRP", "CDT").flatMap { part ->
            Phase.values().flatMap { phase ->
                listOf(false, true).flatMap { review -> CrossPlatformCheck.run(cfg, part, phase, 1L, review).second }
            }
        }

    private fun roundTrip(r: TrialRecord): TrialRecord = Codec.trial(JSONObject(Codec.trial(r).toString()))

    @Test
    fun everyScriptedTrialRecordSurvivesStorage() {
        val records = scriptedRecords()
        assertEquals("all six task parts present", setOf("GNG", "SPF", "SPB", "FLK", "TRP", "CDT"),
            records.map { it.taskId }.toSet())
        records.forEach { r -> assertEquals("record ${r.taskId} ${r.taskVersion} ${r.phase} #${r.trialSeq}", r, roundTrip(r)) }
        println("CodecRoundTripTest: ${records.size} scripted trial records round-tripped unchanged")
    }

    @Test
    fun lateResponseLatencySurvivesStorage() {
        val base = scriptedRecords().first { it.taskId == "GNG" && it.phase == Phase.SCORED }
        val late = base.copy(lateResponses = 1, lateRtMs = 1100.25)
        val back = roundTrip(late)
        assertEquals(late, back)
        assertEquals(1100.25, back.lateRtMs!!, 0.0)
    }

    @Test
    fun sessionMetadataSurvivesStorage() {
        val meta = SessionMetadata(
            participantId = ParticipantId.generate(SeededRandom(3)), sessionId = "round-trip-session", mode = "SESSION",
            startedAtUtc = "2026-10-19T09:00:00Z", deviceManufacturer = "Test", deviceModel = "Model \"A\", 2",
            osVersion = "16", sdkInt = 36, screenWidthPx = 2340, screenHeightPx = 1080, xdpi = 396.5f, ydpi = 397.25f,
            nominalRefreshHz = 60.0, measuredRefreshHz = 59.94, appVersion = "0.6.2-dev", gitCommit = "abc1234",
            taskConfigVersion = cfg.configVersion, taskConfigSha256 = cfg.sha256, sessionSeed = -1667787037531930038L,
            brightness = 0.8f, uiLanguage = "ur", onsetMarkerEnabled = true,
            primaryTimingSource = SoftwareTimingSource.ID, timingSources = listOf(SoftwareTimingSource().info),
            motionSource = NoMotionSource().info, clockCheckNanos = 123_456L, doNotDisturbActive = true,
            checklist = mapOf("chk_quiet" to "yes", "session_form" to "retest", "battery_percent" to "87"),
        )
        val back = Codec.session(JSONObject(Codec.session(meta).toString()))
        assertEquals(meta, back)
        assertTrue("optional fields kept", back.measuredRefreshHz == 59.94 && back.clockCheckNanos == 123_456L)
    }
}
