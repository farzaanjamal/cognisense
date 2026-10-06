package org.cognisense.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.cognisense.app.data.Store
import org.cognisense.app.task.Audio
import org.cognisense.app.task.StimulusPainter
import org.cognisense.app.task.TaskRunner
import org.cognisense.app.task.WindowControl
import org.cognisense.app.ui.Settings
import org.cognisense.app.ui.Strings
import org.cognisense.app.ui.Ui
import org.cognisense.core.config.BatteryConfig
import java.io.OutputStream

/**
 * Single activity. Screens are plain Views swapped by show(). No third-party libraries:
 * the app depends only on the Android framework and the pure-Kotlin core.
 */
class MainActivity : Activity() {
    lateinit var settings: Settings; private set
    lateinit var store: Store; private set
    /** config/tasks.json, packaged as an asset: the single source of every task parameter. */
    lateinit var config: BatteryConfig; private set
    lateinit var catalog: TaskCatalog; private set
    lateinit var painter: StimulusPainter; private set
    private lateinit var flow: AppFlow
    private var backHandler: (() -> Unit)? = null
    private var pendingWrite: ((OutputStream) -> Unit)? = null

    /** Non-null while trials are running: back is ignored and interruptions are forwarded. */
    var activeRunner: TaskRunner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        store = Store(this)
        try {
            Strings.load(assets.open("strings.json").use { it.readBytes() })
            config = BatteryConfig.parse(assets.open("tasks.json").use { it.readBytes() })
            catalog = TaskCatalog(config)
            painter = StimulusPainter(this, config)
        } catch (e: Exception) {
            // Fail loudly: never run tasks with missing or invalid parameters.
            val (root, c) = Ui.page(this, settings.lang)
            // The only text kept in code: shown when the text file itself may be the thing that failed.
            val title = if (Strings.loaded) Strings.get("config_error", settings.lang)
                else "The configuration could not be loaded. The app cannot run tasks."
            c.addView(Ui.title(this, title, settings.lang))
            c.addView(Ui.text(this, e.message ?: e.toString(), org.cognisense.app.ui.Lang.EN, 13f, Ui.MUTED))
            setContentView(root)
            return
        }
        flow = AppFlow(this)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, OnBackInvokedCallback { handleBack() },
            )
        }
        flow.welcome()
    }

    fun show(view: View, onBack: (() -> Unit)?) {
        Audio.stop()
        setContentView(view)
        backHandler = onBack
    }

    private fun handleBack() {
        if (activeRunner != null) return // children cannot leave a task with the back gesture
        backHandler?.invoke() ?: finish()
    }

    @Deprecated("Used below API 33 only")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { handleBack() }

    override fun onPause() {
        super.onPause()
        activeRunner?.interrupt(System.nanoTime())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (activeRunner == null) return
        if (!hasFocus) activeRunner?.interrupt(System.nanoTime()) else WindowControl.hideSystemBars(this)
    }

    override fun onDestroy() {
        activeRunner?.stop()
        Audio.stop()
        store.close()
        super.onDestroy()
    }

    /** Export through the system file picker: the user chooses where data go; the app has no network access. */
    fun saveDocument(fileName: String, mime: String, write: (OutputStream) -> Unit) {
        pendingWrite = write
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE, fileName),
            REQ_EXPORT,
        )
    }

    @Deprecated("Framework Activity result API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXPORT) return
        val write = pendingWrite.also { pendingWrite = null } ?: return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val ok = try {
            contentResolver.openOutputStream(uri)?.use { write(it); true } ?: false
        } catch (e: Exception) { false }
        Toast.makeText(this, Strings.get(if (ok) "exported" else "export_failed", settings.lang), Toast.LENGTH_SHORT).show()
    }

    companion object { private const val REQ_EXPORT = 41 }
}
