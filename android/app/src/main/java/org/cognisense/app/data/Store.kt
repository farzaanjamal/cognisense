package org.cognisense.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.cognisense.core.data.CsvExport
import org.cognisense.core.data.JsonExport
import org.cognisense.core.data.SessionMetadata
import org.cognisense.core.engine.EndMode
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.ResponseKey
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialPlan
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.motion.MotionSourceInfo
import org.cognisense.core.scoring.Metric
import org.cognisense.core.scoring.TimingClass
import org.cognisense.core.timing.TimingMeasurement
import org.cognisense.core.timing.TimingSourceInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private SQLite storage. Written only between phases, never while trials run.
 * Each trial is stored losslessly as JSON plus a few indexed columns for queries.
 */
class Store(ctx: Context) : SQLiteOpenHelper(ctx, "cognisense.db", null, 1) {

    data class SessionRow(
        val sessionId: String, val participantId: String, val mode: String,
        val startedAtUtc: String, val status: String, val deviceModel: String, val appVersion: String,
    )

    data class RunRow(
        val taskId: String, val taskVersion: String, val phase: String, val attempt: Int, val result: String,
        val measuredHz: Double?, val engineHz: Double?, val droppedFrames: Int, val interruptions: Int,
        val unassignedResponses: Int,
    )

    data class StoredMetric(val taskId: String, val taskVersion: String, val metric: Metric)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions(session_id TEXT PRIMARY KEY, participant_id TEXT NOT NULL,
            mode TEXT NOT NULL, started_at_utc TEXT NOT NULL, status TEXT NOT NULL, meta_json TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE task_runs(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL,
            task_id TEXT NOT NULL, task_version TEXT NOT NULL, phase TEXT NOT NULL, attempt INTEGER NOT NULL,
            result TEXT NOT NULL, measured_hz REAL, engine_hz REAL, dropped_frames INTEGER NOT NULL,
            interruptions INTEGER NOT NULL, unassigned_responses INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE trials(session_id TEXT NOT NULL, task_id TEXT NOT NULL, phase TEXT NOT NULL,
            attempt INTEGER NOT NULL, trial_seq INTEGER NOT NULL, condition TEXT, outcome TEXT, rt_ms REAL,
            record_json TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX trials_session ON trials(session_id)")
        db.execSQL("""CREATE TABLE metrics(session_id TEXT NOT NULL, task_id TEXT NOT NULL, task_version TEXT NOT NULL,
            name TEXT NOT NULL, value REAL, unit TEXT NOT NULL, timing_class TEXT NOT NULL, n INTEGER NOT NULL,
            note TEXT NOT NULL)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema v1 only. Future versions must migrate, never drop: participant data must not be lost.
    }

    fun saveSession(meta: SessionMetadata, status: String) {
        writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("session_id", meta.sessionId); put("participant_id", meta.participantId); put("mode", meta.mode)
            put("started_at_utc", meta.startedAtUtc); put("status", status); put("meta_json", Codec.session(meta).toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setStatus(sessionId: String, status: String) {
        writableDatabase.update("sessions", ContentValues().apply { put("status", status) }, "session_id=?", arrayOf(sessionId))
    }

    /** Logs a task run. [engine] is null for runs that never started (e.g. task skipped as not implemented). */
    fun saveRun(sessionId: String, taskId: String, taskVersion: String, phase: Phase, attempt: Int, result: String,
                measuredHz: Double?, engineHz: Double?, engine: TaskRun?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insert("task_runs", null, ContentValues().apply {
                put("session_id", sessionId); put("task_id", taskId); put("task_version", taskVersion)
                put("phase", phase.name); put("attempt", attempt); put("result", result)
                put("measured_hz", measuredHz); put("engine_hz", engineHz)
                put("dropped_frames", engine?.droppedFramesTotal ?: 0); put("interruptions", engine?.interruptions ?: 0)
                put("unassigned_responses", engine?.unassignedResponses ?: 0)
            })
            engine?.records()?.forEach { r ->
                db.insert("trials", null, ContentValues().apply {
                    put("session_id", sessionId); put("task_id", r.taskId); put("phase", r.phase.name)
                    put("attempt", r.attempt); put("trial_seq", r.trialSeq); put("condition", r.plan.condition)
                    put("outcome", r.outcome.name); put("rt_ms", r.rtMs); put("record_json", Codec.trial(r).toString())
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveMetrics(sessionId: String, def: TaskDefinition, metrics: List<Metric>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            metrics.forEach { m ->
                db.insert("metrics", null, ContentValues().apply {
                    put("session_id", sessionId); put("task_id", def.id); put("task_version", def.version)
                    put("name", m.name); put("value", m.value); put("unit", m.unit)
                    put("timing_class", m.timingClass.name); put("n", m.n); put("note", m.note)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun sessions(): List<SessionRow> = readableDatabase.rawQuery(
        "SELECT session_id, participant_id, mode, started_at_utc, status, meta_json FROM sessions ORDER BY started_at_utc DESC", null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val meta = JSONObject(c.getString(5))
                add(SessionRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                    meta.optString("deviceModel"), meta.optString("appVersion")))
            }
        }
    }

    fun session(id: String): Pair<SessionMetadata, String>? = readableDatabase.rawQuery(
        "SELECT meta_json, status FROM sessions WHERE session_id=?", arrayOf(id),
    ).use { c -> if (c.moveToFirst()) Codec.session(JSONObject(c.getString(0))) to c.getString(1) else null }

    fun trials(sessionId: String): List<TrialRecord> = readableDatabase.rawQuery(
        "SELECT record_json FROM trials WHERE session_id=? ORDER BY trial_seq", arrayOf(sessionId),
    ).use { c -> buildList { while (c.moveToNext()) add(Codec.trial(JSONObject(c.getString(0)))) } }

    fun metrics(sessionId: String): List<StoredMetric> = readableDatabase.rawQuery(
        "SELECT task_id, task_version, name, value, unit, timing_class, n, note FROM metrics WHERE session_id=?", arrayOf(sessionId),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(StoredMetric(c.getString(0), c.getString(1), Metric(
                c.getString(2), if (c.isNull(3)) null else c.getDouble(3), c.getString(4),
                TimingClass.valueOf(c.getString(5)), c.getInt(6), c.getString(7))))
        }
    }

    fun runs(sessionId: String): List<RunRow> = readableDatabase.rawQuery(
        """SELECT task_id, task_version, phase, attempt, result, measured_hz, engine_hz, dropped_frames,
           interruptions, unassigned_responses FROM task_runs WHERE session_id=? ORDER BY id""", arrayOf(sessionId),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(RunRow(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4),
                if (c.isNull(5)) null else c.getDouble(5), if (c.isNull(6)) null else c.getDouble(6),
                c.getInt(7), c.getInt(8), c.getInt(9)))
        }
    }

    // --- exports (core formats) ---------------------------------------------------------------

    fun trialsCsv(sessionIds: List<String>): String = buildString {
        var first = true
        for (id in sessionIds) {
            val meta = session(id)?.first ?: continue
            val csv = CsvExport.trials(meta, trials(id))
            append(if (first) csv else csv.substringAfter("\r\n"))
            first = false
        }
        if (first) append(CsvExport.TRIAL_COLUMNS.joinToString(",")).append("\r\n")
    }

    fun sessionJson(sessionId: String): String? {
        val meta = session(sessionId)?.first ?: return null
        val records = trials(sessionId)
        val metrics = metrics(sessionId)
        val keys = (records.map { it.taskId to it.taskVersion } + metrics.map { it.taskId to it.taskVersion }).distinct()
        val tasks = keys.associate { (id, v) ->
            "$id $v" to (records.filter { it.taskId == id && it.taskVersion == v } to
                metrics.filter { it.taskId == id && it.taskVersion == v }.map { it.metric })
        }
        return JsonExport.session(meta, tasks)
    }
}

/** Lossless JSON round-trip for core types (org.json is part of the Android framework). */
object Codec {
    private fun JSONObject.num(k: String, v: Number?): JSONObject = put(k,
        when (v) {
            null -> JSONObject.NULL
            is Double -> if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v
            is Float -> if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v.toDouble()
            else -> v
        })
    private fun JSONObject.str(k: String, v: String?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.doubleOrNull(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.strOrNull(k: String): String? = if (isNull(k)) null else getString(k)

    fun plan(p: TrialPlan) = JSONObject().put("index", p.index).put("block", p.block)
        .put("blockCondition", p.blockCondition).put("condition", p.condition).put("stimulus", p.stimulus)
        .str("expectedResponse", p.expectedResponse?.name).put("foreperiodMs", p.foreperiodMs)
        .put("fixationInForeperiod", p.fixationInForeperiod).num("stimulusMs", p.stimulusMs)
        .put("responseWindowMs", p.responseWindowMs).put("endMode", p.endMode.name).put("endMs", p.endMs)
        .put("fixationAfterStimulus", p.fixationAfterStimulus)

    fun plan(o: JSONObject) = TrialPlan(o.getInt("index"), o.getInt("block"), o.getString("blockCondition"),
        o.getString("condition"), o.getString("stimulus"), o.strOrNull("expectedResponse")?.let { ResponseKey.valueOf(it) },
        o.getInt("foreperiodMs"), o.getBoolean("fixationInForeperiod"), o.intOrNull("stimulusMs"),
        o.getInt("responseWindowMs"), EndMode.valueOf(o.getString("endMode")), o.getInt("endMs"),
        o.getBoolean("fixationAfterStimulus"))

    private fun timing(m: TimingMeasurement) = JSONObject().put("sourceId", m.sourceId).put("trialSeq", m.trialSeq)
        .num("onsetTime", m.onsetTime).num("responseTime", m.responseTime).put("timeUnit", m.timeUnit)
        .num("rtMicros", m.rtMicros).put("flags", JSONArray(m.flags.sorted()))

    private fun timing(o: JSONObject) = TimingMeasurement(o.getString("sourceId"), o.getLong("trialSeq"),
        o.longOrNull("onsetTime"), o.longOrNull("responseTime"), o.getString("timeUnit"), o.longOrNull("rtMicros"),
        o.getJSONArray("flags").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })

    fun trial(r: TrialRecord): JSONObject = JSONObject().put("taskId", r.taskId).put("taskVersion", r.taskVersion)
        .put("phase", r.phase.name).put("attempt", r.attempt).put("trialSeq", r.trialSeq).put("plan", plan(r.plan))
        .put("trialStartNanos", r.trialStartNanos).num("onsetNanos", r.onsetNanos).num("offsetNanos", r.offsetNanos)
        .put("endNanos", r.endNanos).put("stimulusFrames", r.stimulusFrames).num("responseNanos", r.responseNanos)
        .str("responseKey", r.responseKey?.name).num("touchX", r.touchX).num("touchY", r.touchY).num("rtMs", r.rtMs)
        .put("outcome", r.outcome.name).put("prematureResponses", r.prematureResponses)
        .put("extraResponses", r.extraResponses).put("lateResponses", r.lateResponses).num("lateRtMs", r.lateRtMs)
        .put("offTargetTouches", r.offTargetTouches).put("droppedFrames", r.droppedFrames)
        .put("interrupted", r.interrupted)
        .put("timing", JSONObject().also { t -> r.timing.forEach { (k, v) -> t.put(k, timing(v)) } })
        .put("details", JSONObject(r.details))

    fun trial(o: JSONObject): TrialRecord {
        val t = o.getJSONObject("timing")
        return TrialRecord(o.getString("taskId"), o.getString("taskVersion"), Phase.valueOf(o.getString("phase")),
            o.getInt("attempt"), o.getLong("trialSeq"), plan(o.getJSONObject("plan")), o.getLong("trialStartNanos"),
            o.longOrNull("onsetNanos"), o.longOrNull("offsetNanos"), o.getLong("endNanos"), o.getInt("stimulusFrames"),
            o.longOrNull("responseNanos"), o.strOrNull("responseKey")?.let { ResponseKey.valueOf(it) },
            o.doubleOrNull("touchX")?.toFloat(), o.doubleOrNull("touchY")?.toFloat(), o.doubleOrNull("rtMs"),
            Outcome.valueOf(o.getString("outcome")), o.getInt("prematureResponses"), o.getInt("extraResponses"),
            o.getInt("lateResponses"), o.getInt("offTargetTouches"), o.getInt("droppedFrames"),
            o.getBoolean("interrupted"), t.keys().asSequence().associateWith { timing(t.getJSONObject(it)) },
            o.getJSONObject("details").let { dt -> dt.keys().asSequence().associateWith { dt.getString(it) } },
            lateRtMs = o.doubleOrNull("lateRtMs"))
    }

    fun session(m: SessionMetadata): JSONObject = JSONObject().put("participantId", m.participantId)
        .put("sessionId", m.sessionId).put("mode", m.mode).put("startedAtUtc", m.startedAtUtc)
        .put("deviceManufacturer", m.deviceManufacturer).put("deviceModel", m.deviceModel).put("osVersion", m.osVersion)
        .put("sdkInt", m.sdkInt).put("screenWidthPx", m.screenWidthPx).put("screenHeightPx", m.screenHeightPx)
        .num("xdpi", m.xdpi).num("ydpi", m.ydpi).put("nominalRefreshHz", m.nominalRefreshHz)
        .num("measuredRefreshHz", m.measuredRefreshHz).put("appVersion", m.appVersion).put("gitCommit", m.gitCommit)
        .put("taskConfigVersion", m.taskConfigVersion).put("taskConfigSha256", m.taskConfigSha256)
        .put("sessionSeed", m.sessionSeed.toString()).num("brightness", m.brightness).put("uiLanguage", m.uiLanguage)
        .put("onsetMarkerEnabled", m.onsetMarkerEnabled).put("primaryTimingSource", m.primaryTimingSource)
        .put("timingSources", JSONArray(m.timingSources.map {
            JSONObject().put("id", it.id).put("version", it.version).put("status", it.status)
                .put("clock", it.clock).put("description", it.description)
        }))
        .put("motionSource", m.motionSource.let {
            JSONObject().put("id", it.id).put("version", it.version).put("status", it.status)
                .put("placement", it.placement).num("sampleRateHz", it.sampleRateHz).put("description", it.description)
        })
        .num("clockCheckNanos", m.clockCheckNanos).put("doNotDisturbActive", m.doNotDisturbActive ?: JSONObject.NULL)
        .put("checklist", JSONObject(m.checklist)).put("schemaVersion", m.schemaVersion)

    fun session(o: JSONObject): SessionMetadata {
        val ts = o.getJSONArray("timingSources")
        val ms = o.getJSONObject("motionSource")
        val cl = o.getJSONObject("checklist")
        return SessionMetadata(
            participantId = o.getString("participantId"), sessionId = o.getString("sessionId"), mode = o.getString("mode"),
            startedAtUtc = o.getString("startedAtUtc"), deviceManufacturer = o.getString("deviceManufacturer"),
            deviceModel = o.getString("deviceModel"), osVersion = o.getString("osVersion"), sdkInt = o.getInt("sdkInt"),
            screenWidthPx = o.getInt("screenWidthPx"), screenHeightPx = o.getInt("screenHeightPx"),
            xdpi = o.getDouble("xdpi").toFloat(), ydpi = o.getDouble("ydpi").toFloat(),
            nominalRefreshHz = o.getDouble("nominalRefreshHz"), measuredRefreshHz = o.doubleOrNull("measuredRefreshHz"),
            appVersion = o.getString("appVersion"), gitCommit = o.getString("gitCommit"),
            taskConfigVersion = o.getString("taskConfigVersion"), taskConfigSha256 = o.getString("taskConfigSha256"),
            sessionSeed = o.getString("sessionSeed").toLong(), brightness = o.getDouble("brightness").toFloat(),
            uiLanguage = o.getString("uiLanguage"), onsetMarkerEnabled = o.getBoolean("onsetMarkerEnabled"),
            primaryTimingSource = o.getString("primaryTimingSource"),
            timingSources = (0 until ts.length()).map { i -> ts.getJSONObject(i).let {
                TimingSourceInfo(it.getString("id"), it.getString("version"), it.getString("status"),
                    it.getString("clock"), it.getString("description"))
            } },
            motionSource = MotionSourceInfo(ms.getString("id"), ms.getString("version"), ms.getString("status"),
                ms.getString("placement"), ms.doubleOrNull("sampleRateHz"), ms.getString("description")),
            clockCheckNanos = o.longOrNull("clockCheckNanos"),
            doNotDisturbActive = if (o.isNull("doNotDisturbActive")) null else o.getBoolean("doNotDisturbActive"),
            checklist = cl.keys().asSequence().associateWith { cl.getString(it) },
            schemaVersion = o.getString("schemaVersion"),
        )
    }
}
