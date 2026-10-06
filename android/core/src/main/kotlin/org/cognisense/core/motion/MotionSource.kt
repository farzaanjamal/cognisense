package org.cognisense.core.motion

/**
 * Where motor-activity sensing attaches. The session layer calls start() at session
 * start, mark() at every task/block boundary, and stop() at the end.
 *
 * Markers carry CLOCK_MONOTONIC timestamps from the phone. A hardware source must
 * map them onto its own clock (see the data contract on [ImuMotionSource]).
 */
interface MotionSource {
    val info: MotionSourceInfo
    fun start(sessionId: String)
    fun mark(marker: TaskMarker)
    fun stop(): MotionSummary?
}

data class MotionSourceInfo(
    val id: String,
    val version: String,
    /** IMPLEMENTED, DESIGNED or PLANNED. */
    val status: String,
    /** Body placement, or "none". */
    val placement: String,
    val sampleRateHz: Double?,
    val description: String,
)

/** e.g. label "GNG:block=2:start". */
data class TaskMarker(val timeNanos: Long, val label: String)

data class MotionSummary(val sourceId: String, val samples: Long, val markers: Int, val note: String)

/** STATUS: IMPLEMENTED. Records nothing; used in the demo so the session layer exercises the interface. */
class NoMotionSource : MotionSource {
    override val info = MotionSourceInfo(
        id = "none", version = "0.1", status = "IMPLEMENTED", placement = "none",
        sampleRateHz = null, description = "No motion sensing (demo)",
    )
    private var markers = 0
    override fun start(sessionId: String) { markers = 0 }
    override fun mark(marker: TaskMarker) { markers++ }
    override fun stop(): MotionSummary = MotionSummary(info.id, 0, markers, "no sensor attached")
}

/**
 * STATUS: DESIGNED — every method throws.
 *
 * Intended hardware: a wearable inertial measurement unit (accelerometer + gyroscope)
 * on an ESP32, worn at a placement to be decided (waist, ankle or headband; see
 * hardware/README.md), and/or force-sensitive resistors under the seat. No camera,
 * no microphone.
 *
 * Data contract (v0.1):
 *   Sampling      : fixed rate (proposed 50 Hz) on the device clock, stored on the device
 *                   and transferred after the session (no live streaming requirement).
 *   Sample record : t_us, ax, ay, az (m/s^2), gx, gy, gz (deg/s)
 *   Sync          : at start, at end, and at every marker the app sends "SYNC,<k>,<phone_ns>";
 *                   the device logs "S,<k>,<device_us>". Clock mapping = linear fit of
 *                   device_us on phone_ns over all sync pairs; residuals are stored so
 *                   alignment error is reportable.
 *   Markers       : task/block start and end labels, so activity can be analysed by
 *                   cognitive demand (the meta-analytic literature finds the largest
 *                   activity differences under high executive demand).
 */
class ImuMotionSource : MotionSource {
    override val info = MotionSourceInfo(
        id = "imu-esp32", version = "0.0-design", status = "DESIGNED",
        placement = "TBD", sampleRateHz = 50.0,
        description = "Wearable IMU on ESP32 with marker-based clock alignment",
    )
    override fun start(sessionId: String): Unit = notBuilt()
    override fun mark(marker: TaskMarker): Unit = notBuilt()
    override fun stop(): MotionSummary? = notBuilt()
    private fun notBuilt(): Nothing =
        throw NotImplementedError("ImuMotionSource is DESIGNED, not IMPLEMENTED")
}
