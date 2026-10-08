package org.cognisense.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Shell-screen styling, shared in spirit with the web demo (browser/web/style.css): "record white" paper,
 * ink, one action colour, Atkinson Hyperlegible for Latin text and Noto Nastaliq Urdu for Urdu, both
 * bundled so every phone shows the same letterforms. Task screens never use any of this (see task/TaskView).
 */
object Ui {
    val BG = Color.rgb(250, 250, 248)       // --record
    val SURFACE = Color.WHITE
    val TEXT = Color.rgb(34, 39, 46)        // --ink
    val MUTED = Color.rgb(89, 97, 109)      // --muted
    val RULE = Color.rgb(213, 216, 220)     // --rule
    val WASH = Color.rgb(239, 241, 242)     // --wash
    val ACCENT = Color.rgb(0, 114, 178)     // --signal: actions only
    private val ACCENT_PRESSED = Color.rgb(0, 90, 140)

    /** Content never grows wider than this, so tablets and landscape keep a readable line length. */
    private const val MAX_CONTENT_DP = 640f

    private var latin: Typeface = Typeface.DEFAULT
    private var latinBold: Typeface = Typeface.DEFAULT_BOLD
    private var urdu: Typeface? = null

    /** Loads the bundled fonts once. Missing fonts fall back to the system font; nothing else depends on them. */
    fun init(ctx: Context) {
        fun load(name: String): Typeface? =
            try { Typeface.createFromAsset(ctx.assets, "fonts/$name") } catch (e: RuntimeException) { null }
        load("atkinson-hyperlegible-400.ttf")?.let { latin = it }
        load("atkinson-hyperlegible-700.ttf")?.let { latinBold = it }
        urdu = load("noto-nastaliq-urdu-400.ttf")
    }

    fun typeface(lang: Lang, bold: Boolean = false): Typeface = when {
        lang == Lang.UR && urdu != null -> if (bold) Typeface.create(urdu, Typeface.BOLD) else urdu!!
        bold -> latinBold
        else -> latin
    }

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun locale(lang: Lang): Locale = if (lang == Lang.UR) Locale.forLanguageTag("ur-PK") else Locale.ENGLISH

    private fun direction(lang: Lang) = if (lang == Lang.UR) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    /** A column capped at MAX_CONTENT_DP and centred by its parent. */
    private class Column(ctx: Context, private val maxPx: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val spec = if (w > maxPx) MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY) else widthMeasureSpec
            super.onMeasure(spec, heightMeasureSpec)
        }
    }

    /**
     * A scrolling page; right-to-left for Urdu. Returns (root, content column).
     * centred = true places short pages (ready, rest, done) in the middle of the screen.
     * The page pads itself by the system bars, so on Android 15 (which draws apps edge to edge)
     * nothing sits under the status bar or the navigation bar.
     */
    fun page(ctx: Context, lang: Lang, centred: Boolean = false): Pair<ScrollView, LinearLayout> {
        val pad = dp(ctx, 24f)
        val content = Column(ctx, dp(ctx, MAX_CONTENT_DP)).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(ctx, 40f))
            if (centred) gravity = Gravity.CENTER_VERTICAL
        }
        val frame = FrameLayout(ctx).apply {
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (centred) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            ))
        }
        val root = ScrollView(ctx).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            layoutDirection = direction(lang)
            addView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    v.setPadding(i.left, i.top, i.right, i.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        return root to content
    }

    /** Shell screens: bars in the page colour with dark icons. Task mode hides the bars itself. */
    fun shellBars(a: Activity) {
        val w = a.window
        @Suppress("DEPRECATION")
        run {
            w.statusBarColor = BG
            w.navigationBarColor = BG
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(light, light)
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (Build.VERSION.SDK_INT >= 27) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
    }

    fun text(ctx: Context, s: String, lang: Lang, sizeSp: Float = 16f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            typeface = typeface(lang, bold)
            textLocale = locale(lang)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setLineSpacing(0f, if (lang == Lang.UR) 1.25f else 1.2f)
            val v = dp(ctx, 4f)
            setPadding(0, v, 0, v)
        }

    fun title(ctx: Context, s: String, lang: Lang) = text(ctx, s, lang, 27f, TEXT, bold = true).apply {
        letterSpacing = if (lang == Lang.UR) 0f else -0.01f
        setPadding(0, 0, 0, dp(ctx, 8f))
    }

    fun heading(ctx: Context, s: String, lang: Lang) = text(ctx, s, lang, 18f, TEXT, bold = true).apply {
        setPadding(0, dp(ctx, 16f), 0, dp(ctx, 2f))
    }

    /** A small, muted label above a group (e.g. "Measures"). */
    fun label(ctx: Context, s: String, lang: Lang) = text(ctx, s, lang, 13f, MUTED, bold = true).apply {
        setPadding(0, dp(ctx, 14f), 0, dp(ctx, 2f))
    }

    private fun shape(ctx: Context, fill: ColorStateList, stroke: Int? = null, radiusDp: Float = 10f) =
        GradientDrawable().apply {
            color = fill
            cornerRadius = dp(ctx, radiusDp).toFloat()
            stroke?.let { setStroke(dp(ctx, 1f), it) }
        }

    private fun ripple(ctx: Context, content: Drawable?, radiusDp: Float = 10f) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(40, 34, 39, 46)), content,
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(ctx, radiusDp).toFloat() },
    )

    /**
     * One primary (filled) action per screen; everything else is secondary (outlined).
     * Disabled buttons look disabled, so a locked "Continue" is never mistaken for an active one.
     */
    fun button(ctx: Context, s: String, lang: Lang, primary: Boolean = true, onClick: () -> Unit) =
        Button(ctx).apply {
            text = s
            isAllCaps = false
            textSize = 17f
            typeface = typeface(lang, bold = primary)
            textLocale = locale(lang)
            minHeight = dp(ctx, 56f)
            stateListAnimator = null // no Material shadow
            val ph = dp(ctx, 20f)
            setPadding(ph, dp(ctx, 10f), ph, dp(ctx, 10f))
            val disabled = intArrayOf(-android.R.attr.state_enabled)
            val any = intArrayOf()
            if (primary) {
                setTextColor(ColorStateList(arrayOf(disabled, any), intArrayOf(MUTED, Color.WHITE)))
                background = ripple(ctx, shape(ctx, ColorStateList(
                    arrayOf(disabled, intArrayOf(android.R.attr.state_pressed), any),
                    intArrayOf(WASH, ACCENT_PRESSED, ACCENT))))
            } else {
                setTextColor(ColorStateList(arrayOf(disabled, any), intArrayOf(RULE, TEXT)))
                background = ripple(ctx, shape(ctx, ColorStateList.valueOf(SURFACE), RULE))
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
        background = shape(ctx, ColorStateList.valueOf(SURFACE), RULE)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(ctx, 12f) }
    }

    /**
     * A card that is itself the button: title, optional description, and a chevron pointing
     * in the reading direction. Used for menus (home, expert review, sessions).
     */
    fun linkCard(ctx: Context, title: String, sub: String?, lang: Lang, onClick: () -> Unit): LinearLayout =
        card(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 64f)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(ctx, title, lang, 17f, TEXT, bold = true).apply { setPadding(0, 0, 0, 0) })
                sub?.let { addView(text(ctx, it, lang, 14f, MUTED).apply { setPadding(0, dp(ctx, 2f), 0, 0) }) }
            }
            addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = if (lang == Lang.UR) "\u2039" else "\u203A"
                textSize = 26f
                setTextColor(MUTED)
                setPadding(dp(ctx, 12f), 0, 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            isClickable = true
            isFocusable = true
            foreground = ripple(ctx, null)
            contentDescription = if (sub != null) "$title. $sub" else title
            setOnClickListener { onClick() }
        }

    /** A label–value row for facts and measures; the value can carry a small badge (e.g. a timing class). */
    fun row(ctx: Context, key: String, value: String, badge: String? = null, lang: Lang = Lang.EN): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = direction(lang)
            val v = dp(ctx, 7f)
            setPadding(0, v, 0, v)
            addView(text(ctx, key, lang, 14f, MUTED).apply { setPadding(0, 0, dp(ctx, 12f), 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(ctx, value, Lang.EN, 14f, TEXT).apply {
                setPadding(0, 0, 0, 0)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                fontFeatureSettings = "tnum"
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            badge?.let { addView(badge(ctx, it)) }
        }

    /** A thin rule between rows. */
    fun rule(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(RULE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(ctx, 0.5f)))
    }

    /** Rows separated by rules, inside a card. */
    fun rows(ctx: Context, items: List<View>): LinearLayout = card(ctx).apply {
        val p = dp(ctx, 16f)
        setPadding(p, dp(ctx, 6f), p, dp(ctx, 6f))
        items.forEachIndexed { i, v -> if (i > 0) addView(rule(ctx)); addView(v) }
    }

    fun badge(ctx: Context, s: String): TextView = TextView(ctx).apply {
        text = s
        textSize = 12f
        typeface = latinBold
        setTextColor(TEXT)
        gravity = Gravity.CENTER
        minWidth = dp(ctx, 26f)
        val h = dp(ctx, 7f)
        setPadding(h, dp(ctx, 2f), h, dp(ctx, 2f))
        background = shape(ctx, ColorStateList.valueOf(WASH), null, 6f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(ctx, 10f) }
    }

    fun checkbox(ctx: Context, s: String, lang: Lang): CheckBox = CheckBox(ctx).apply {
        text = s
        textSize = 16f
        typeface = typeface(lang)
        textLocale = locale(lang)
        setTextColor(TEXT)
        buttonTintList = ColorStateList.valueOf(ACCENT)
        minHeight = dp(ctx, 52f)
        val v = dp(ctx, 4f)
        setPadding(dp(ctx, 8f), v, 0, v)
    }

    /** Tints framework controls (seek bars, switches, progress bars) with the action colour. */
    fun tint(view: View) {
        val accent = ColorStateList.valueOf(ACCENT)
        when (view) {
            is Switch -> {
                view.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(ACCENT, SURFACE))
                view.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.argb(110, 0, 114, 178), RULE))
            }
            is SeekBar -> { view.progressTintList = accent; view.thumbTintList = accent; view.progressBackgroundTintList = ColorStateList.valueOf(RULE) }
            is ProgressBar -> { view.progressTintList = accent; view.progressBackgroundTintList = ColorStateList.valueOf(RULE) }
        }
    }

    fun fixedHeight(ctx: Context, view: View, heightDp: Float): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp))
            .apply { topMargin = dp(ctx, 8f) }
    }

    /** Vertical space. */
    fun gap(ctx: Context, heightDp: Float): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp))
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
