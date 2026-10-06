package org.cognisense.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Shell-screen styling. Task screens never use these colours (see task/TaskView). */
object Ui {
    val BG = Color.rgb(244, 243, 239)
    val TEXT = Color.rgb(33, 33, 33)
    val MUTED = Color.rgb(100, 100, 100)
    val ACCENT = Color.rgb(38, 70, 83)

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun locale(lang: Lang): Locale = if (lang == Lang.UR) Locale.forLanguageTag("ur-PK") else Locale.ENGLISH

    /** A scrolling page; right-to-left for Urdu. Returns (root, content column). */
    fun page(ctx: Context, lang: Lang): Pair<ScrollView, LinearLayout> {
        val pad = dp(ctx, 24f)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val root = ScrollView(ctx).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            layoutDirection = if (lang == Lang.UR) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return root to content
    }

    fun text(ctx: Context, s: String, lang: Lang, sizeSp: Float = 16f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textLocale = locale(lang) // lets the system pick an Urdu (Nastaliq/Naskh) font where installed
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setLineSpacing(0f, if (lang == Lang.UR) 1.4f else 1.15f)
            val v = dp(ctx, 4f)
            setPadding(0, v, 0, v)
        }

    fun title(ctx: Context, s: String, lang: Lang) = text(ctx, s, lang, 24f, TEXT, bold = true)

    fun heading(ctx: Context, s: String, lang: Lang) = text(ctx, s, lang, 18f, ACCENT, bold = true)

    fun button(ctx: Context, s: String, lang: Lang, primary: Boolean = true, onClick: () -> Unit) =
        Button(ctx).apply {
            text = s
            isAllCaps = false
            textSize = 17f
            textLocale = locale(lang)
            minHeight = dp(ctx, 56f)
            if (primary) {
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(ACCENT); cornerRadius = dp(ctx, 8f).toFloat() }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(ctx, 12f) }
            setOnClickListener { onClick() }
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, 16f)
        setPadding(p, p, p, p)
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(ctx, 8f).toFloat() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(ctx, 12f) }
    }

    fun fixedHeight(ctx: Context, view: View, heightDp: Float): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp))
            .apply { topMargin = dp(ctx, 8f) }
    }
}

/** Persistent settings. The PIN is stored only as a salted SHA-256 hash. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("cognisense", Context.MODE_PRIVATE)

    var lang: Lang
        get() = Lang.valueOf(p.getString("lang", Lang.UR.name) ?: Lang.UR.name)
        set(v) { p.edit().putString("lang", v.name).apply() }

    /** Window brightness fixed during task screens (0.1–1.0). */
    var brightness: Float
        get() = p.getFloat("brightness", 0.8f)
        set(v) { p.edit().putFloat("brightness", v.coerceIn(0.1f, 1f)).apply() }

    var markerEnabled: Boolean
        get() = p.getBoolean("marker", false)
        set(v) { p.edit().putBoolean("marker", v).apply() }

    val hasPin: Boolean get() = p.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit().putString("pin_salt", hex(salt)).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pin_salt", null)?.let { unhex(it) } ?: return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), (p.getString("pin_hash", "") ?: "").toByteArray())
    }

    private fun hash(salt: ByteArray, pin: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8)))

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
