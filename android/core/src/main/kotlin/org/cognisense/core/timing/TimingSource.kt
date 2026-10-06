package org.cognisense.core.timing

/**
 * Where sensor timing attaches to the task engine.
 *
 * The engine calls every registered TimingSource at three points of each trial:
 *   armTrial(seq)            — trial begins (hardware: tell the device which trial is next)
 *   onOnsetFrame(seq, t)     — first frame containing the stimulus was produced
 *   onResponseEvent(seq, t)  — first in-window touch response (touchscreen timestamp)
 * and, when the task finishes, collects measurements() and attaches them to trial
 * records by trial sequence number. Every source's measurements are stored, so a
 * session can carry software and hardware timing for the same trials side by side —
 * which is exactly what the planned timing-validation study needs.
 */
interface TimingSource {
    val info: TimingSourceInfo
    fun armTrial(trialSeq: Long)
    fun onOnsetFrame(trialSeq: Long, frameTimeNanos: Long)
    fun onResponseEvent(trialSeq: Long, eventTimeNanos: Long)
    fun measurements(): List<TimingMeasurement>
}

data class TimingSourceInfo(
    val id: String,
    val version: String,
    /** Implementation status label: IMPLEMENTED, DESIGNED or PLANNED. */
    val status: String,
    /** Clock that onset/response times refer to. */
    val clock: String,
    val description: String,
)

data class TimingMeasurement(
    val sourceId: String,
    val trialSeq: Long,
    /** Onset time on [TimingSourceInfo.clock], in the unit given by [timeUnit]; null if not observed. */
    val onsetTime: Long?,
    val responseTime: Long?,
    val timeUnit: String,
    /** Reaction time in microseconds, computed on a single clock; null if either event is missing. */
    val rtMicros: Long?,
    val flags: Set<String> = emptySet(),
)

/**
 * STATUS: IMPLEMENTED.
 * Onset = Choreographer frame time (vsync timestamp) of the first frame that contains
 * the stimulus. Response = MotionEvent event time (getEventTime / getEventTimeNanos),
 * not the time the app's callback ran. Both are CLOCK_MONOTONIC on Android; the app
 * checks this at session start and logs the result (see docs/measurement_precision.md).
 *
 * What this does NOT remove: display pipeline latency after the frame time, and touch
 * controller latency before the event timestamp. These form the device-specific
 * constant offset C and jitter e described in docs/measurement_precision.md.
 */
class SoftwareTimingSource : TimingSource {
    override val info = TimingSourceInfo(
        id = ID,
        version = "0.1",
        status = "IMPLEMENTED",
        clock = "CLOCK_MONOTONIC (ns)",
        description = "Frame-callback onset and MotionEvent response timestamps",
    )
    private val onsets = LinkedHashMap<Long, Long>()
    private val responses = HashMap<Long, Long>()

    override fun armTrial(trialSeq: Long) {}

    override fun onOnsetFrame(trialSeq: Long, frameTimeNanos: Long) {
        if (trialSeq !in onsets) onsets[trialSeq] = frameTimeNanos
    }

    override fun onResponseEvent(trialSeq: Long, eventTimeNanos: Long) {
        if (trialSeq !in responses) responses[trialSeq] = eventTimeNanos
    }

    override fun measurements(): List<TimingMeasurement> = onsets.map { (seq, onset) ->
        val resp = responses[seq]
        TimingMeasurement(
            sourceId = ID, trialSeq = seq, onsetTime = onset, responseTime = resp,
            timeUnit = "ns", rtMicros = resp?.let { (it - onset) / 1_000 },
        )
    }

    companion object { const val ID = "software" }
}

/**
 * STATUS: DESIGNED — interface and data contract only. Every method throws, so this
 * source cannot be used by accident before the firmware exists.
 *
 * Intended hardware (see hardware/README.md):
 *   ESP32 with (a) a photodiode over the on-screen onset marker, (b) a physical
 *   response button, and optionally (c) a piezo disc on the device to timestamp the
 *   finger striking the touchscreen. All edges are timestamped by GPIO interrupt on
 *   the ESP32's own microsecond timer (esp_timer_get_time). RT is computed on the
 *   ESP32, so display, touch and radio latency never enter the measurement; BLE only
 *   carries the finished result.
 *
 * Data contract (v0.1, BLE GATT, UTF-8 lines):
 *   App -> device : "ARM,<seq>\n"            arm photodiode + inputs for trial <seq>
 *   Device -> app : "R,<seq>,<onset_us>,<resp_us>,<rt_us>,<input>,<flags>\n"
 *       onset_us/resp_us : device clock, microseconds since device boot
 *       rt_us            : resp_us - onset_us, or empty if either missing
 *       input            : BUTTON | PIEZO
 *       flags            : '|'-separated subset of NO_ONSET, NO_RESPONSE, MULTI_ONSET,
 *                          BOUNCE, LATE_ARM
 *   Results are matched to trials by <seq>, never by arrival order. A result that
 *   never arrives leaves the trial's hardware measurement empty (not imputed).
 *
 * Known design caveat: with BUTTON input the hardware measures a different response
 * modality from the touchscreen. Only PIEZO input measures the same physical act.
 */
class HardwareTimingSource : TimingSource {
    override val info = TimingSourceInfo(
        id = ID, version = "0.0-design", status = "DESIGNED",
        clock = "ESP32 esp_timer (us since device boot)",
        description = "Photodiode onset + button/piezo response on a single microcontroller clock",
    )
    override fun armTrial(trialSeq: Long): Unit = notBuilt()
    override fun onOnsetFrame(trialSeq: Long, frameTimeNanos: Long): Unit = notBuilt()
    override fun onResponseEvent(trialSeq: Long, eventTimeNanos: Long): Unit = notBuilt()
    override fun measurements(): List<TimingMeasurement> = notBuilt()

    private fun notBuilt(): Nothing =
        throw NotImplementedError("HardwareTimingSource is DESIGNED, not IMPLEMENTED")

    companion object { const val ID = "hardware-esp32" }
}
