package org.cognisense.app.task

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import org.cognisense.core.engine.FrameContent
import org.cognisense.core.engine.TaskRun
import kotlin.math.abs

/**
 * Drives a TaskRun (any task) from Choreographer vsync callbacks.
 *
 * 1. Probe: for the first [probeFrames] frames the view shows fixation while frame intervals are
 *    measured. The median interval gives the actual refresh rate, which is logged.
 * 2. The engine is created with the nominal display rate if the measured rate agrees within 3%,
 *    otherwise with the measured rate. Both are logged so any mismatch is visible in the data.
 * 3. Each frame: engine.onFrame(frameTimeNanos) -> view.render(content). Rendering is invalidated
 *    inside the frame callback, so it is drawn in the same frame whose time was recorded as onset.
 *
 * No database or file I/O happens while trials run; records are saved after the phase finishes.
 */
class TaskRunner(
    private val view: TaskView,
    private val displayHz: Double,
    private val makeEngine: (engineHz: Double) -> TaskRun,
    private val onFinished: (Result) -> Unit,
    /** From config/tasks.json common.refresh_probe_frames. */
    private val probeFrames: Int,
) : Choreographer.FrameCallback {

    class Result(val engine: TaskRun, val measuredHz: Double, val engineHz: Double)

    private val choreographer = Choreographer.getInstance()
    private var engine: TaskRun? = null
    private val probe = ArrayList<Long>(probeFrames + 1)
    private var measuredHz = 0.0
    private var engineHz = 0.0
    private var running = false

    fun start() {
        view.onResponse = { e -> engine?.onResponse(e) }
        view.onContinueTap = { engine?.continueAfterBreak() }
        view.onResumeTap = { engine?.resume() }
        view.render(FrameContent(fixation = true))
        running = true
        choreographer.postFrameCallback(this)
    }

    /** onPause / focus loss. During the probe, the probe simply restarts. */
    fun interrupt(nowNanos: Long) {
        val e = engine
        if (e == null) probe.clear() else e.onInterruption(nowNanos)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val e = engine
        if (e == null) {
            probe += frameTimeNanos
            view.invalidate() // keep the display active so adaptive-refresh panels do not idle down
            if (probe.size > probeFrames) {
                val deltas = probe.zipWithNext { a, b -> (b - a).toDouble() }.sorted()
                measuredHz = 1e9 / deltas[deltas.size / 2]
                engineHz = if (abs(measuredHz - displayHz) / displayHz < 0.03) displayHz else measuredHz
                engine = makeEngine(engineHz)
            }
            choreographer.postFrameCallback(this)
            return
        }
        val content = e.onFrame(frameTimeNanos)
        view.render(content)
        if (content.finished) {
            running = false
            onFinished(Result(e, measuredHz, engineHz))
            return
        }
        choreographer.postFrameCallback(this)
    }
}

/** Window configuration for task screens. */
object WindowControl {
    fun display(a: Activity): Display =
        if (Build.VERSION.SDK_INT >= 30) a.display!! else @Suppress("DEPRECATION") a.windowManager.defaultDisplay

    /**
     * Display mode at the current resolution closest to 60 Hz. Decision (docs/measurement_precision.md):
     * a common 60 Hz rate across heterogeneous devices is preferred over each device's maximum, so frame
     * quantisation is identical everywhere. The measured rate is logged regardless.
     */
    fun target60HzMode(d: Display): Display.Mode? {
        val cur = d.mode
        return d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .minByOrNull { abs(it.refreshRate - 60f) }
    }

    fun nominalHz(a: Activity): Double {
        val d = display(a)
        return (target60HzMode(d)?.refreshRate ?: d.refreshRate).toDouble()
    }

    fun enterTaskMode(a: Activity, brightness: Float) {
        val w = a.window
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = w.attributes
        lp.screenBrightness = brightness
        target60HzMode(display(a))?.let { lp.preferredDisplayModeId = it.modeId }
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        w.attributes = lp
        hideSystemBars(a)
    }

    fun exitTaskMode(a: Activity) {
        val w = a.window
        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = w.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        lp.preferredDisplayModeId = 0
        w.attributes = lp
        showSystemBars(a)
    }

    fun hideSystemBars(a: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            a.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    private fun showSystemBars(a: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            a.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun screenSizePx(a: Activity): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            a.windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") display(a).getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
}

/** Device facts logged with every session. */
object DeviceInfo {
    /**
     * Uptime clock (MotionEvent base) minus nanoTime clock (Choreographer base), read back to back.
     * Both should be CLOCK_MONOTONIC; uptimeMillis has 1 ms resolution, so |value| < ~1-2 ms is expected.
     * A larger value means software RTs on this device are invalid (flagged in the dashboard).
     */
    fun clockCheckNanos(): Long {
        val a = System.nanoTime()
        val u = SystemClock.uptimeMillis()
        val b = System.nanoTime()
        return u * 1_000_000L - (a + b) / 2
    }

    /** true = some Do-Not-Disturb filter active; false = all interruptions allowed; null = unknown. */
    fun doNotDisturb(ctx: Context): Boolean? = try {
        when (ctx.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter) {
            null -> null
            NotificationManager.INTERRUPTION_FILTER_ALL -> false
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> null
            else -> true
        }
    } catch (e: Exception) { null }

    fun batteryPercent(ctx: Context): Int? = try {
        ctx.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    } catch (e: Exception) { null }
}

/** Playback-only instruction narration from assets/audio/<taskId>_<lang>.ogg, if present. Never during trials. */
object Audio {
    private var player: MediaPlayer? = null

    fun available(ctx: Context, name: String): Boolean =
        ctx.assets.list("audio")?.contains(name) == true

    fun play(ctx: Context, name: String) {
        stop()
        try {
            ctx.assets.openFd("audio/$name").use { fd ->
                player = MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    prepare(); start()
                }
            }
        } catch (e: Exception) { stop() }
    }

    fun stop() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
    }
}
