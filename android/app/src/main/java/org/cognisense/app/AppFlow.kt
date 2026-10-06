package org.cognisense.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import org.cognisense.app.dashboard.AccuracyChart
import org.cognisense.app.dashboard.RtHistogram
import org.cognisense.app.dashboard.RtSeriesChart
import org.cognisense.app.data.Store
import org.cognisense.app.task.Audio
import org.cognisense.app.task.DeviceInfo
import org.cognisense.app.task.IllustrationView
import org.cognisense.app.task.TaskRunner
import org.cognisense.app.task.TaskView
import org.cognisense.app.task.WindowControl
import org.cognisense.app.ui.Lang
import org.cognisense.app.ui.Strings
import org.cognisense.app.ui.Ui
import org.cognisense.core.data.ParticipantId
import org.cognisense.core.data.SessionMetadata
import org.cognisense.core.engine.Outcome
import org.cognisense.core.engine.Phase
import org.cognisense.core.engine.RunContext
import org.cognisense.core.engine.TaskDefinition
import org.cognisense.core.engine.TaskRun
import org.cognisense.core.engine.TrialRecord
import org.cognisense.core.motion.MotionSource
import org.cognisense.core.motion.NoMotionSource
import org.cognisense.core.motion.TaskMarker
import org.cognisense.core.scoring.Metric
import org.cognisense.core.tasks.SpatialSpanTask
import org.cognisense.core.timing.SoftwareTimingSource
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Per-session state held in memory while a session runs. */
class SessionCtx(var meta: SessionMetadata) {
    private var runIndex = 0
    val motion: MotionSource = NoMotionSource()

    /** Each run gets its own block of 1000 trial sequence numbers, unique within the session. */
    fun nextSeqBase(): Long = (runIndex++).toLong() * 1000L
}

private data class PartResult(val part: PartInfo, val scored: List<TrialRecord>, val metrics: List<Metric>, val practiceMet: Boolean)

class AppFlow(private val a: MainActivity) {
    private val s get() = a.settings
    private val lang get() = s.lang
    private val common get() = a.config.common
    private fun t(key: String) = Strings.get(key, lang)
    private val handler = Handler(Looper.getMainLooper())

    // ---------------------------------------------------------------- entry

    fun welcome() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("app_name"), lang))
        c.addView(Ui.card(a).apply { addView(Ui.text(a, t("disclaimer"), lang, 15f)) })
        if (lang == Lang.UR) c.addView(Ui.text(a, t("draft_translation_notice"), lang, 13f, Ui.MUTED))
        c.addView(Ui.button(a, t("lang_switch"), lang, primary = false) {
            s.lang = if (lang == Lang.UR) Lang.EN else Lang.UR
            welcome()
        })
        // Reviewer build: straight into Expert Review; Session mode and the dashboard do not exist in it.
        c.addView(Ui.button(a, t("continue"), lang) {
            if (BuildConfig.REVIEWER) expertMenu(newSession("EXPERT_REVIEW", emptyMap())) else home()
        })
        a.show(root, null)
    }

    private fun leaveExpert(sc: SessionCtx) {
        closeSession(sc, "completed")
        if (BuildConfig.REVIEWER) welcome() else home()
    }

    fun home() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("app_name"), lang))
        fun entry(titleKey: String, subKey: String?, action: () -> Unit) {
            c.addView(Ui.card(a).apply {
                addView(Ui.heading(a, t(titleKey), lang))
                subKey?.let { addView(Ui.text(a, t(it), lang, 14f, Ui.MUTED)) }
                addView(Ui.button(a, t("open"), lang) { action() })
            })
        }
        entry("home_expert", "home_expert_sub") { expertMenu(newSession("EXPERT_REVIEW", emptyMap())) }
        entry("home_session", "home_session_sub") { pinGate({ checklist() }, { home() }) }
        entry("home_dashboard", null) { pinGate({ dashboard() }, { home() }) }
        entry("home_settings", null) { pinGate({ settingsScreen() }, { home() }) }
        c.addView(Ui.text(a, "v${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT} · tasks ${a.config.configVersion}",
            Lang.EN, 12f, Ui.MUTED))
        a.show(root) { welcome() }
    }

    private fun pinGate(onSuccess: () -> Unit, onCancel: () -> Unit) {
        val (root, c) = Ui.page(a, lang)
        val setting = !s.hasPin
        c.addView(Ui.title(a, t(if (setting) "pin_set_title" else "pin_enter_title"), lang))
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            textSize = 24f
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        c.addView(input)
        val msg = Ui.text(a, "", lang, 14f, Ui.ACCENT)
        c.addView(msg)
        c.addView(Ui.button(a, t("continue"), lang) {
            val pin = input.text.toString()
            if (!pin.matches(Regex("\\d{4,8}"))) {
                msg.text = t("pin_invalid")
            } else if (setting) {
                s.setPin(pin); onSuccess()
            } else if (s.checkPin(pin)) {
                onSuccess()
            } else {
                msg.text = t("pin_wrong"); input.setText("")
            }
        })
        c.addView(Ui.button(a, t("back"), lang, primary = false) { onCancel() })
        a.show(root, onCancel)
    }

    private fun settingsScreen() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("home_settings"), lang))
        c.addView(Ui.heading(a, t("settings_brightness"), lang))
        val label = Ui.text(a, "${(s.brightness * 100).roundToInt()}%", Lang.EN)
        c.addView(label)
        c.addView(SeekBar(a).apply {
            max = 90
            progress = ((s.brightness - 0.1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    s.brightness = 0.1f + p / 100f
                    label.text = "${(s.brightness * 100).roundToInt()}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        c.addView(Ui.heading(a, t("settings_marker"), lang))
        c.addView(Switch(a).apply {
            isChecked = s.markerEnabled
            text = t(if (isChecked) "on" else "off")
            setOnCheckedChangeListener { b, v -> s.markerEnabled = v; b.text = t(if (v) "on" else "off") }
        })
        c.addView(Ui.text(a, t("settings_marker_note"), lang, 13f, Ui.MUTED))
        c.addView(Ui.button(a, t("back"), lang, primary = false) { home() })
        a.show(root) { home() }
    }

    // ---------------------------------------------------------------- sessions

    private fun newSession(mode: String, checklist: Map<String, String>): SessionCtx {
        val rnd = SecureRandom()
        val (w, h) = WindowControl.screenSizePx(a)
        val dm = a.resources.displayMetrics
        val meta = SessionMetadata(
            participantId = ParticipantId.generate { rnd.nextInt(it) },
            sessionId = UUID.randomUUID().toString(), mode = mode, startedAtUtc = Instant.now().toString(),
            deviceManufacturer = Build.MANUFACTURER, deviceModel = Build.MODEL, osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT, screenWidthPx = max(w, h), screenHeightPx = min(w, h),
            xdpi = dm.xdpi, ydpi = dm.ydpi, nominalRefreshHz = WindowControl.nominalHz(a), measuredRefreshHz = null,
            appVersion = BuildConfig.VERSION_NAME, gitCommit = BuildConfig.GIT_COMMIT,
            taskConfigVersion = a.config.configVersion, taskConfigSha256 = a.config.sha256, sessionSeed = rnd.nextLong(),
            brightness = s.brightness, uiLanguage = lang.code, onsetMarkerEnabled = s.markerEnabled,
            primaryTimingSource = SoftwareTimingSource.ID, timingSources = listOf(SoftwareTimingSource().info),
            motionSource = NoMotionSource().info, clockCheckNanos = DeviceInfo.clockCheckNanos(),
            doNotDisturbActive = DeviceInfo.doNotDisturb(a), checklist = checklist,
        )
        val sc = SessionCtx(meta)
        sc.motion.start(meta.sessionId)
        a.store.saveSession(meta, "in_progress")
        return sc
    }

    private fun closeSession(sc: SessionCtx, status: String) {
        sc.motion.stop()
        a.store.setStatus(sc.meta.sessionId, status)
    }

    /** Runs one phase of one task part full-screen, then saves the run. No I/O happens while trials run. */
    private fun runPhase(sc: SessionCtx, def: TaskDefinition, part: PartInfo, phase: Phase, attempt: Int,
                         done: (TaskRun) -> Unit) {
        val view = TaskView(a, lang, part.pads, s.markerEnabled, common.blockBreakMs, a.painter,
            boardId = (def as? SpatialSpanTask)?.config?.board)
        a.show(view) {}
        WindowControl.enterTaskMode(a, s.brightness)
        val runner = TaskRunner(
            view = view,
            displayHz = WindowControl.nominalHz(a),
            probeFrames = common.refreshProbeFrames,
            makeEngine = { hz ->
                def.createRun(RunContext(phase, sc.meta.sessionSeed, hz, common, sc.nextSeqBase(), attempt,
                    onEvent = { label, time -> sc.motion.mark(TaskMarker(time, "${def.id}:${phase.name}:$attempt:$label")) }))
            },
            onFinished = { r ->
                a.activeRunner = null
                WindowControl.exitTaskMode(a)
                if (sc.meta.measuredRefreshHz == null) {
                    sc.meta = sc.meta.copy(measuredRefreshHz = r.measuredHz)
                    a.store.saveSession(sc.meta, "in_progress")
                }
                val result = when {
                    phase == Phase.SCORED -> "completed"
                    def.practiceCriterionMet(r.engine.records()) -> "criterion_met"
                    else -> "criterion_not_met"
                }
                a.store.saveRun(sc.meta.sessionId, def.id, def.version, phase, attempt, result,
                    r.measuredHz, r.engineHz, r.engine)
                done(r.engine)
            },
        )
        a.activeRunner = runner
        runner.start()
    }

    private fun instructions(part: PartInfo, leadKey: String, onBack: () -> Unit, onStart: () -> Unit) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t(part.childNameKey), lang))
        c.addView(Ui.text(a, t(leadKey), lang, 18f))
        c.addView(Ui.fixedHeight(a, IllustrationView(a, part.id, a.painter, part.standard), 200f))
        c.addView(Ui.text(a, t(part.instructionKey), lang, 20f))
        val audio = "${part.id.lowercase()}_${lang.code}.ogg"
        if (Audio.available(a, audio)) c.addView(Ui.button(a, t("play_audio"), lang, primary = false) { Audio.play(a, audio) })
        c.addView(Ui.button(a, t("start"), lang) { onStart() })
        a.show(root, onBack)
    }

    private fun realStart(onStart: () -> Unit) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("real_start"), lang))
        c.addView(Ui.button(a, t("start"), lang) { onStart() })
        a.show(root) {}
    }

    // ---------------------------------------------------------------- expert review

    private fun expertMenu(sc: SessionCtx) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("expert_title"), lang))
        c.addView(Ui.text(a, t("expert_note"), lang, 14f, Ui.MUTED))
        for (info in a.catalog.pool) {
            c.addView(Ui.card(a).apply {
                addView(Ui.heading(a, "${info.code} · ${info.docName}", Lang.EN))
                addView(Ui.text(a, info.construct, Lang.EN, 14f))
                if (info.implemented) addView(Ui.button(a, t("open"), lang) { expertIntro(sc, info) })
                else addView(Ui.text(a, t("not_available"), lang, 13f, Ui.MUTED))
            })
        }
        c.addView(Ui.button(a, t("back"), lang, primary = false) { leaveExpert(sc) })
        a.show(root) { leaveExpert(sc) }
    }

    private fun expertIntro(sc: SessionCtx, info: TaskInfo) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, "${info.code} · ${info.docName}", Lang.EN))
        listOf("intro_construct" to info.construct, "intro_measures" to info.measures,
            "intro_duration" to info.reviewDuration).forEach { (k, v) ->
            c.addView(Ui.heading(a, t(k), lang))
            c.addView(Ui.text(a, v, Lang.EN, 15f))
        }
        c.addView(Ui.text(a, t("intro_timing"), lang, 13f, Ui.MUTED))
        c.addView(Ui.button(a, t("start"), lang) { expertPart(sc, info, 0, emptyList()) })
        a.show(root) { expertMenu(sc) }
    }

    /** Each part: instructions, practice, shortened scored block; then one summary for all parts. */
    private fun expertPart(sc: SessionCtx, info: TaskInfo, i: Int, results: List<PartResult>) {
        if (i >= info.parts.size) { summary(sc, results); return }
        val part = info.parts[i]
        val def = part.review
        instructions(part, "practice_first", onBack = { expertMenu(sc) }) {
            runPhase(sc, def, part, Phase.PRACTICE, 1) { practice ->
                val met = def.practiceCriterionMet(practice.records())
                realStart {
                    runPhase(sc, def, part, Phase.SCORED, 1) { scored ->
                        val metrics = def.score(scored.records())
                        a.store.saveMetrics(sc.meta.sessionId, def, metrics)
                        expertPart(sc, info, i + 1, results + PartResult(part, scored.records(), metrics, met))
                    }
                }
            }
        }
    }

    private fun summary(sc: SessionCtx, results: List<PartResult>) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("summary_title"), lang))
        c.addView(Ui.text(a, t("summary_note"), lang, 14f, Ui.MUTED))
        for (r in results) {
            c.addView(Ui.heading(a, "${r.part.id} ${r.part.review.version}", Lang.EN))
            c.addView(Ui.text(a, "${t("practice_result")}: ${t(if (r.practiceMet) "yes" else "no")}", lang))
            val counts = r.scored.groupingBy { it.outcome }.eachCount()
            c.addView(Ui.card(a).apply {
                addView(Ui.text(a, "Trials: ${r.scored.size}", Lang.EN, 14f))
                Outcome.values().forEach { o -> addView(Ui.text(a, "${o.name}: ${counts[o] ?: 0}", Lang.EN, 14f)) }
            })
            c.addView(Ui.text(a, t("metrics"), lang, 13f, Ui.MUTED))
            c.addView(Ui.card(a).apply { r.metrics.forEach { addView(Ui.text(a, formatMetric(it), Lang.EN, 13f)) } })
        }
        c.addView(Ui.button(a, t("back"), lang) { expertMenu(sc) })
        a.show(root) { expertMenu(sc) }
    }

    // ---------------------------------------------------------------- field session

    private fun checklist() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("checklist_title"), lang))
        val required = listOf("chk_quiet", "chk_table", "chk_consent")
        val boxes = required.associateWith { key ->
            CheckBox(a).apply { text = t(key); textSize = 16f; textLocale = Ui.locale(lang) }.also { c.addView(it) }
        }
        val protector = CheckBox(a).apply { text = t("chk_protector"); textSize = 16f; textLocale = Ui.locale(lang) }
        c.addView(protector)
        val dnd = DeviceInfo.doNotDisturb(a)
        c.addView(Ui.text(a, "Do Not Disturb detected: ${dnd?.toString() ?: "unknown"}", Lang.EN, 13f, Ui.MUTED))
        val battery = DeviceInfo.batteryPercent(a)
        c.addView(Ui.text(a, "${t("battery")}: ${battery?.let { "$it%" } ?: "?"}", lang))
        if (battery != null && battery < 30) c.addView(Ui.text(a, t("battery_low"), lang, 14f, Ui.ACCENT, bold = true))
        val go = Ui.button(a, t("continue"), lang) {
            val answers = required.associateWith { if (boxes.getValue(it).isChecked) "yes" else "no" } +
                mapOf("chk_protector" to if (protector.isChecked) "yes" else "no",
                    "battery_percent" to (battery?.toString() ?: "unknown"))
            participantScreen(newSession("SESSION", answers))
        }
        go.isEnabled = false
        boxes.values.forEach { b -> b.setOnCheckedChangeListener { _, _ -> go.isEnabled = boxes.values.all { it.isChecked } } }
        c.addView(go)
        a.show(root) { home() }
    }

    private fun participantScreen(sc: SessionCtx) {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("participant_title"), lang))
        c.addView(Ui.text(a, sc.meta.participantId, Lang.EN, 40f, Ui.ACCENT, bold = true).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
        })
        c.addView(Ui.text(a, t("participant_note"), lang, 14f, Ui.MUTED))
        c.addView(Ui.button(a, t("continue"), lang) { battery(sc, a.catalog.coreOrder, 0) })
        a.show(root) { closeSession(sc, "abandoned"); home() }
    }

    /** Core battery in fixed order (config core_battery_order). Unbuilt tasks are skipped and logged, never hidden. */
    private fun battery(sc: SessionCtx, order: List<TaskInfo>, i: Int) {
        if (i >= order.size) { completion(sc); return }
        val info = order[i]
        if (!info.implemented) {
            a.store.saveRun(sc.meta.sessionId, info.taskId, "-", Phase.SCORED, 1, "skipped_not_implemented", null, null, null)
            battery(sc, order, i + 1)
            return
        }
        runParts(sc, info, 0) { afterTask(sc, order, i) }
    }

    /**
     * Parts run in order, each with instructions, practice (up to the configured attempts) and a scored
     * block. If a part's practice criterion is not met, that part and every later part of the same task
     * are skipped and logged (e.g. backward span is not attempted if forward practice failed).
     */
    private fun runParts(sc: SessionCtx, info: TaskInfo, j: Int, onDone: () -> Unit) {
        if (j >= info.parts.size) { onDone(); return }
        val part = info.parts[j]
        val def = part.standard
        practiceLoop(sc, part, def, 1,
            onPassed = {
                runPhase(sc, def, part, Phase.SCORED, 1) { e ->
                    a.store.saveMetrics(sc.meta.sessionId, def, def.score(e.records()))
                    runParts(sc, info, j + 1, onDone)
                }
            },
            onFailed = {
                info.parts.drop(j).forEachIndexed { k, p ->
                    a.store.saveRun(sc.meta.sessionId, p.standard.id, p.standard.version, Phase.SCORED, 1,
                        if (k == 0) "skipped_practice_criterion_not_met" else "skipped_after_earlier_part_failed",
                        null, null, null)
                }
                onDone()
            },
        )
    }

    private fun afterTask(sc: SessionCtx, order: List<TaskInfo>, i: Int) {
        if (order.drop(i + 1).any { it.implemented }) rest { battery(sc, order, i + 1) } else battery(sc, order, i + 1)
    }

    private fun practiceLoop(sc: SessionCtx, part: PartInfo, def: TaskDefinition, attempt: Int,
                             onPassed: () -> Unit, onFailed: () -> Unit) {
        instructions(part, if (attempt == 1) "practice_first" else "practice_again", onBack = {}) {
            runPhase(sc, def, part, Phase.PRACTICE, attempt) { e ->
                when {
                    def.practiceCriterionMet(e.records()) -> realStart(onPassed)
                    attempt < common.maxPracticeAttempts -> practiceLoop(sc, part, def, attempt + 1, onPassed, onFailed)
                    else -> onFailed()
                }
            }
        }
    }

    /** Between-task rest: neutral progress bar; Continue unlocks after the configured minimum rest. */
    private fun rest(next: () -> Unit) {
        val restMs = common.betweenTaskRestMs
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("rest"), lang))
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = restMs.toInt() }
        c.addView(bar)
        val go = Ui.button(a, t("continue"), lang) { next() }.apply { isEnabled = false }
        c.addView(go)
        val start = SystemClock.uptimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                bar.progress = elapsed.coerceAtMost(restMs).toInt()
                if (elapsed >= restMs) go.isEnabled = true else handler.postDelayed(this, 250)
            }
        })
        a.show(root) {}
    }

    private fun completion(sc: SessionCtx) {
        closeSession(sc, "completed")
        completionScreen()
    }

    private fun completionScreen() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("done_child"), lang))
        c.addView(Ui.button(a, t("done_admin"), lang, primary = false) { pinGate({ home() }, { completionScreen() }) })
        a.show(root) {}
    }

    // ---------------------------------------------------------------- dashboard

    private fun dashboard() {
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, t("dash_title"), lang))
        c.addView(Ui.card(a).apply { addView(Ui.text(a, t("dash_rationale"), lang, 14f)) })
        val sessions = a.store.sessions()
        if (sessions.isEmpty()) {
            c.addView(Ui.text(a, t("no_sessions"), lang))
        } else {
            c.addView(Ui.button(a, t("export_all"), lang, primary = false) {
                a.saveDocument("cognisense_all_trials.csv", "text/csv") { out ->
                    out.write(a.store.trialsCsv(sessions.map { it.sessionId }).toByteArray(Charsets.UTF_8))
                }
            })
        }
        for (row in sessions) {
            c.addView(Ui.card(a).apply {
                addView(Ui.heading(a, "${row.participantId} · ${row.mode}", Lang.EN))
                addView(Ui.text(a, "${row.startedAtUtc} · ${row.deviceModel} · v${row.appVersion} · ${row.status}",
                    Lang.EN, 13f, Ui.MUTED))
                setOnClickListener { sessionDetail(row.sessionId) }
            })
        }
        a.show(root) { home() }
    }

    private fun sessionDetail(id: String) {
        val (meta, status) = a.store.session(id) ?: return dashboard()
        val trials = a.store.trials(id)
        val metrics = a.store.metrics(id)
        val runs = a.store.runs(id)
        val (root, c) = Ui.page(a, lang)
        c.addView(Ui.title(a, meta.participantId, Lang.EN))
        val facts = listOf(
            "mode" to meta.mode, "status" to status, "started (UTC)" to meta.startedAtUtc,
            "device" to "${meta.deviceManufacturer} ${meta.deviceModel} (Android ${meta.osVersion}, API ${meta.sdkInt})",
            "touch timestamp resolution" to if (meta.sdkInt >= 34) "ns" else "ms",
            "app" to "${meta.appVersion} @ ${meta.gitCommit}",
            "task config" to "${meta.taskConfigVersion} (sha256 ${meta.taskConfigSha256.take(12)}…)",
            "refresh nominal / measured" to "${fmt(meta.nominalRefreshHz)} / ${meta.measuredRefreshHz?.let { fmt(it) } ?: "—"} Hz",
            "session seed" to meta.sessionSeed.toString(), "brightness" to fmt(meta.brightness.toDouble()),
            "language" to meta.uiLanguage, "onset marker" to meta.onsetMarkerEnabled.toString(),
            "timing source" to meta.primaryTimingSource,
            "checklist" to meta.checklist.entries.joinToString { "${it.key}=${it.value}" },
        )
        c.addView(Ui.card(a).apply { facts.forEach { (k, v) -> addView(Ui.text(a, "$k: $v", Lang.EN, 13f)) } })

        c.addView(Ui.heading(a, t("quality"), lang))
        c.addView(Ui.card(a).apply { qualityFlags(meta, trials, runs).forEach { addView(Ui.text(a, it, Lang.EN, 13f)) } })

        val scoredByTask = trials.filter { it.phase == Phase.SCORED }.groupBy { it.taskId to it.taskVersion }
        for ((key, recs) in scoredByTask) {
            c.addView(Ui.heading(a, "${key.first} ${key.second}", Lang.EN))
            c.addView(Ui.text(a, t("metrics"), lang, 13f, Ui.MUTED))
            c.addView(Ui.card(a).apply {
                metrics.filter { it.taskId == key.first && it.taskVersion == key.second }
                    .forEach { addView(Ui.text(a, formatMetric(it.metric), Lang.EN, 13f)) }
            })
            if (recs.any { it.rtMs != null }) {
                c.addView(Ui.text(a, t("chart_rt"), lang, 13f, Ui.MUTED))
                c.addView(Ui.fixedHeight(a, RtSeriesChart(a, recs), 160f))
                c.addView(Ui.text(a, t("chart_hist"), lang, 13f, Ui.MUTED))
                c.addView(Ui.fixedHeight(a, RtHistogram(a, recs), 140f))
            }
            c.addView(Ui.text(a, t("chart_acc"), lang, 13f, Ui.MUTED))
            c.addView(Ui.fixedHeight(a, AccuracyChart(a, recs), 140f))
        }

        c.addView(Ui.heading(a, t("runs"), lang))
        c.addView(Ui.card(a).apply {
            runs.forEach { r ->
                addView(Ui.text(a, "${r.taskId} ${r.taskVersion} ${r.phase} #${r.attempt}: ${r.result} · " +
                    "${r.measuredHz?.let { fmt(it) } ?: "—"} Hz · dropped ${r.droppedFrames} · " +
                    "interruptions ${r.interruptions} · unassigned touches ${r.unassignedResponses}", Lang.EN, 13f))
            }
        })
        val base = "cognisense_${meta.participantId}_${id.take(8)}"
        c.addView(Ui.button(a, t("export_csv"), lang) {
            a.saveDocument("${base}_trials.csv", "text/csv") { it.write(a.store.trialsCsv(listOf(id)).toByteArray(Charsets.UTF_8)) }
        })
        c.addView(Ui.button(a, t("export_json"), lang, primary = false) {
            a.saveDocument("${base}_session.json", "application/json") { out ->
                a.store.sessionJson(id)?.let { out.write(it.toByteArray(Charsets.UTF_8)) }
            }
        })
        a.show(root) { dashboard() }
    }

    private fun qualityFlags(meta: SessionMetadata, trials: List<TrialRecord>, runs: List<Store.RunRow>): List<String> {
        val scored = trials.filter { it.phase == Phase.SCORED }
        val flags = mutableListOf<String>()
        meta.clockCheckNanos?.let {
            if (abs(it) > 2_000_000) flags += "CLOCK CHECK FAILED: clocks differ by ${it / 1_000} µs; software RTs unreliable"
        }
        if (meta.doNotDisturbActive == false) flags += "Do Not Disturb was off at session start"
        meta.measuredRefreshHz?.let {
            if (abs(it - meta.nominalRefreshHz) / meta.nominalRefreshHz > 0.03)
                flags += "Refresh mismatch: measured ${fmt(it)} Hz vs nominal ${fmt(meta.nominalRefreshHz)} Hz"
        }
        if (meta.taskConfigSha256 != a.config.sha256) flags += "Task config differs from the one in this app build"
        fun count(label: String, n: Int) { flags += "$label: $n" }
        count("anticipations / premature releases", scored.count { it.outcome == Outcome.ANTICIPATION })
        count("omissions", scored.count { it.outcome == Outcome.OMISSION })
        count("interrupted trials", scored.count { it.interrupted })
        count("re-administered span trials", scored.count { it.details["readministered_after_interruption"] == "true" })
        count("premature responses", scored.sumOf { it.prematureResponses })
        count("late responses", scored.sumOf { it.lateResponses })
        count("extra responses", scored.sumOf { it.extraResponses })
        count("off-target touches", scored.sumOf { it.offTargetTouches })
        count("dropped frames", scored.sumOf { it.droppedFrames })
        count("app interruptions (all runs)", runs.sumOf { it.interruptions })
        count("practice runs not meeting criterion", runs.count { it.result == "criterion_not_met" })
        count("task parts skipped", runs.count { it.result.startsWith("skipped") })
        return flags
    }

    private fun formatMetric(m: Metric) =
        "${m.name} = ${m.value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"} ${m.unit} [${m.timingClass}] (n=${m.n})"

    private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)
}
