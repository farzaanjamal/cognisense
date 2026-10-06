package org.cognisense.core.data

import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.motion.MotionSourceInfo
import org.cognisense.core.rng.SeededRandom
import org.cognisense.core.scoring.Metric
import org.cognisense.core.timing.TimingSourceInfo
import org.cognisense.core.util.Portable

const val SCHEMA_VERSION = "0.2"

/**
 * Participant IDs are generated, never typed, from an alphabet with no vowels, so an ID
 * cannot spell a name or word. Any link between an ID and a real child is kept offline
 * by the supervising researcher, outside the app. STATUS: IMPLEMENTED.
 */
object ParticipantId {
    const val ALPHABET = "BCDFGHJKLMNPQRSTVWXZ23456789" // no vowels, no 0/1 (confusable with O/I)
    private val PATTERN = Regex("^CS-[$ALPHABET]{6}$")

    /** In the app, [nextIndex] is backed by java.security.SecureRandom. */
    fun generate(nextIndex: (bound: Int) -> Int): String =
        "CS-" + (1..6).map { ALPHABET[nextIndex(ALPHABET.length)] }.joinToString("")

    fun generate(rng: SeededRandom): String = generate { rng.nextInt(it) }

    fun isValid(id: String): Boolean = PATTERN.matches(id)
}

/** One row per session. Field definitions: docs/data_schema.md. */
data class SessionMetadata(
    val participantId: String,
    val sessionId: String,
    /** "EXPERT_REVIEW" or "SESSION". */
    val mode: String,
    val startedAtUtc: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val osVersion: String,
    val sdkInt: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val xdpi: Float,
    val ydpi: Float,
    val nominalRefreshHz: Double,
    val measuredRefreshHz: Double?,
    val appVersion: String,
    val gitCommit: String,
    /** config/tasks.json "config_version" and SHA-256 of the exact file bytes used for this session. */
    val taskConfigVersion: String,
    val taskConfigSha256: String,
    val sessionSeed: Long,
    /** Window brightness fixed for the session, 0.0-1.0. */
    val brightness: Float,
    val uiLanguage: String,
    val onsetMarkerEnabled: Boolean,
    val primaryTimingSource: String,
    val timingSources: List<TimingSourceInfo>,
    val motionSource: MotionSourceInfo,
    /** SystemClock.uptimeMillis()*1e6 minus System.nanoTime() at session start; expected near 0. */
    val clockCheckNanos: Long?,
    val doNotDisturbActive: Boolean?,
    /** Administrator pre-session checklist answers (fixed keys, fixed answer options; never free text about the child). */
    val checklist: Map<String, String> = emptyMap(),
    val schemaVersion: String = SCHEMA_VERSION,
) {
    init {
        require(ParticipantId.isValid(participantId)) { "participant ID must be a generated CS- ID" }
        require(mode == "EXPERT_REVIEW" || mode == "SESSION")
        require(brightness in 0f..1f)
    }
}

object CsvExport {
    val TRIAL_COLUMNS = listOf(
        "schema_version", "participant_id", "session_id", "device_model", "app_version", "git_commit",
        "task_config_version", "task_config_sha256",
        "task_id", "task_version", "phase", "attempt", "trial_seq", "trial_index", "block", "block_condition",
        "condition", "stimulus", "expected_response", "planned_foreperiod_ms", "planned_stimulus_ms",
        "response_window_ms", "trial_start_ns", "onset_ns", "offset_ns", "end_ns", "measured_stimulus_ms",
        "stimulus_frames", "response_ns", "response_key", "touch_x", "touch_y", "rt_ms", "outcome",
        "premature_responses", "extra_responses", "late_responses", "off_target_touches",
        "dropped_frames", "interrupted", "timing_sources", "hw_rt_us", "details",
    )

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\"" else field

    private fun f(x: Any?): String = when (x) {
        null -> ""
        is Float -> if (x.isNaN()) "" else x.toString()
        is Double -> if (x.isNaN()) "" else Portable.fixed(x, 3)
        else -> x.toString()
    }

    fun trials(session: SessionMetadata, records: List<TrialRecord>): String = buildString {
        append(TRIAL_COLUMNS.joinToString(",")).append("\r\n")
        for (r in records) {
            val p = r.plan
            val row = listOf(
                session.schemaVersion, session.participantId, session.sessionId, session.deviceModel,
                session.appVersion, session.gitCommit, session.taskConfigVersion, session.taskConfigSha256, r.taskId, r.taskVersion, r.phase, r.attempt, r.trialSeq, p.index,
                p.block, p.blockCondition, p.condition, p.stimulus, p.expectedResponse ?: "WITHHOLD",
                p.foreperiodMs, p.stimulusMs, p.responseWindowMs, r.trialStartNanos, r.onsetNanos, r.offsetNanos,
                r.endNanos, r.measuredStimulusMs, r.stimulusFrames, r.responseNanos, r.responseKey, r.touchX,
                r.touchY, r.rtMs, r.outcome, r.prematureResponses, r.extraResponses, r.lateResponses,
                r.offTargetTouches, r.droppedFrames, r.interrupted, r.timing.keys.sorted().joinToString("|"),
                r.timing["hardware-esp32"]?.rtMicros, JsonExport.value(r.details),
            )
            append(row.joinToString(",") { escape(f(it)) }).append("\r\n")
        }
    }
}

/** Minimal JSON writer (no third-party dependency). */
object JsonExport {
    fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u").append(Portable.hex4(ch.code)) else append(ch)
        }
        append('"')
    }

    fun value(x: Any?): String = when (x) {
        null -> "null"
        is String -> quote(x)
        is Boolean -> x.toString()
        is Int, is Long -> x.toString()
        is Float -> if (x.isNaN() || x.isInfinite()) "null" else x.toString()
        is Double -> if (x.isNaN() || x.isInfinite()) "null" else x.toString()
        is Enum<*> -> quote(x.name)
        is Map<*, *> -> x.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + value(it.value) }
        is Iterable<*> -> x.joinToString(",", "[", "]") { value(it) }
        else -> quote(x.toString())
    }

    fun session(session: SessionMetadata, tasks: Map<String, Pair<List<TrialRecord>, List<Metric>>>): String {
        val s = session
        val meta = linkedMapOf<String, Any?>(
            "schema_version" to s.schemaVersion, "participant_id" to s.participantId, "session_id" to s.sessionId,
            "mode" to s.mode, "started_at_utc" to s.startedAtUtc, "device_manufacturer" to s.deviceManufacturer,
            "device_model" to s.deviceModel, "os_version" to s.osVersion, "sdk_int" to s.sdkInt,
            "screen_width_px" to s.screenWidthPx, "screen_height_px" to s.screenHeightPx, "xdpi" to s.xdpi,
            "ydpi" to s.ydpi, "nominal_refresh_hz" to s.nominalRefreshHz, "measured_refresh_hz" to s.measuredRefreshHz,
            "app_version" to s.appVersion, "git_commit" to s.gitCommit,
            "task_config_version" to s.taskConfigVersion, "task_config_sha256" to s.taskConfigSha256, "session_seed" to s.sessionSeed.toString(),
            "brightness" to s.brightness, "ui_language" to s.uiLanguage, "onset_marker_enabled" to s.onsetMarkerEnabled,
            "primary_timing_source" to s.primaryTimingSource,
            "timing_sources" to s.timingSources.map { mapOf("id" to it.id, "version" to it.version, "status" to it.status, "clock" to it.clock) },
            "motion_source" to mapOf("id" to s.motionSource.id, "status" to s.motionSource.status, "placement" to s.motionSource.placement),
            "clock_check_ns" to s.clockCheckNanos, "do_not_disturb_active" to s.doNotDisturbActive,
            "checklist" to s.checklist,
        )
        val taskJson = tasks.mapValues { (_, v) ->
            mapOf(
                "metrics" to v.second.map {
                    mapOf("name" to it.name, "value" to it.value, "unit" to it.unit,
                        "timing_class" to it.timingClass, "n" to it.n, "note" to it.note)
                },
                "trial_count" to v.first.size,
            )
        }
        return value(mapOf("session" to meta, "tasks" to taskJson))
    }
}
