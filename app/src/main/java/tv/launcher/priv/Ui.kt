package tv.launcher.priv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    var density = 1f

    const val PANEL_BG = 0xFF1E1F22.toInt()
    const val PANEL_HEADER = 0xFF191A1D.toInt()
    const val ROW_FOCUS = 0xFF393A3D.toInt()
    const val TEXT_MAIN = 0xFFE8EAED.toInt()
    const val TEXT_DIM = 0xFF9AA0A6.toInt()
    const val ACCENT = 0xFF8AB4F8.toInt()

    fun init(ctx: Context) { density = ctx.resources.displayMetrics.density }

    fun dp(v: Float): Int = (v * density + 0.5f).toInt()
    fun dp(v: Int): Int = dp(v.toFloat())
    fun dpf(v: Float): Float = v * density

    fun rounded(color: Int, radiusDp: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dpf(radiusDp)
        return g
    }

    fun focusBg(focusColor: Int, radiusDp: Float): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), rounded(focusColor, radiusDp))
        s.addState(intArrayOf(), rounded(Color.TRANSPARENT, radiusDp))
        return s
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun icon(ctx: Context, res: Int, sizeDp: Int, tint: Int): ImageView {
        val v = ImageView(ctx)
        v.setImageResource(res)
        v.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return v
    }

    fun lp(w: Int, h: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h)
}

/** Круглый аватар профиля: картинка либо цвет + первая буква имени. */
class AvatarView(ctx: Context) : View(ctx) {
    var color: Int = 0xFF5F6A72.toInt()
    var letter: String = "A"
    var bitmap: android.graphics.Bitmap? = null
    var ring: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    fun bind(p: Profile) {
        color = p.avatarColor
        letter = p.letter()
        val f = Store.mediaFile(p, p.avatarImage)
        bitmap = if (f != null) Images.decodeFile(f, 256, 256) else null
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2f
        val bmp = bitmap
        if (bmp != null) {
            val sh = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val m = android.graphics.Matrix()
            val s = maxOf(w / bmp.width, h / bmp.height)
            m.setScale(s, s)
            m.postTranslate((w - bmp.width * s) / 2f, (h - bmp.height * s) / 2f)
            sh.setLocalMatrix(m)
            paint.shader = sh
            c.drawCircle(w / 2f, h / 2f, r, paint)
            paint.shader = null
        } else {
            paint.color = color
            c.drawCircle(w / 2f, h / 2f, r, paint)
            tp.color = Color.WHITE
            tp.textAlign = Paint.Align.CENTER
            tp.textSize = r * 1.05f
            tp.typeface = Typeface.DEFAULT_BOLD
            val y = h / 2f - (tp.descent() + tp.ascent()) / 2f
            c.drawText(letter, w / 2f, y, tp)
        }
        if (ring) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Ui.dpf(2f)
            paint.color = Color.WHITE
            c.drawCircle(w / 2f, h / 2f, r - Ui.dpf(1f), paint)
            paint.style = Paint.Style.FILL
        }
    }
}

class ToggleView(ctx: Context) : View(ctx) {
    var checked = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(Ui.dp(38), Ui.dp(22))
    }

    override fun onDraw(c: Canvas) {
        val tw = Ui.dpf(34f)
        val th = Ui.dpf(14f)
        val cy = height / 2f
        val left = (width - tw) / 2f
        p.color = if (checked) 0xFF4A6285.toInt() else 0xFF5F6368.toInt()
        c.drawRoundRect(RectF(left, cy - th / 2, left + tw, cy + th / 2), th / 2, th / 2, p)
        p.color = if (checked) Ui.ACCENT else 0xFFBDC1C6.toInt()
        val tx = if (checked) left + tw - Ui.dpf(8f) else left + Ui.dpf(2f) + Ui.dpf(6f)
        c.drawCircle(tx, cy, Ui.dpf(10f), p)
    }
}

class BarView(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        p.color = 0xFF4A4C50.toInt()
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), h / 2, h / 2, p)
        p.color = Ui.ACCENT
        c.drawRoundRect(RectF(0f, 0f, width * fraction, h), h / 2, h / 2, p)
    }
}

/** Строка в боковой панели: [значок] заголовок / подпись ........ значение. */
open class PanelRow(ctx: Context) : LinearLayout(ctx) {
    val leading = FrameLayout(ctx)
    val column = LinearLayout(ctx)
    val titleView = Ui.text(ctx, "", 14f, Ui.TEXT_MAIN)
    val subView = Ui.text(ctx, "", 11f, Ui.TEXT_DIM)
    val trailView = Ui.text(ctx, "", 13f, Ui.TEXT_DIM)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(52)
        setPadding(Ui.dp(20), Ui.dp(6), Ui.dp(20), Ui.dp(6))
        isFocusable = true
        isClickable = true
        background = Ui.focusBg(Ui.ROW_FOCUS, 0f)

        leading.visibility = View.GONE
        addView(leading, Ui.lp(Ui.dp(36), Ui.dp(36)).also { it.rightMargin = Ui.dp(14) })

        column.orientation = VERTICAL
        column.addView(titleView)
        subView.visibility = View.GONE
        column.addView(subView)
        addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        trailView.visibility = View.GONE
        addView(trailView)
    }

    fun set(title: String, sub: String? = null, trail: String? = null): PanelRow {
        titleView.text = title
        setSub(sub)
        setTrail(trail)
        return this
    }

    fun setSub(sub: String?) {
        if (sub == null) subView.visibility = View.GONE
        else { subView.text = sub; subView.visibility = View.VISIBLE }
    }

    fun setTrail(t: String?) {
        if (t == null) trailView.visibility = View.GONE
        else { trailView.text = t; trailView.visibility = View.VISIBLE }
    }

    fun setLeadingView(v: View, sizeDp: Int) {
        leading.removeAllViews()
        leading.addView(v, FrameLayout.LayoutParams(Ui.dp(sizeDp), Ui.dp(sizeDp), Gravity.CENTER))
        leading.visibility = View.VISIBLE
    }

    fun setLeadingSized(v: View, wDp: Int, hDp: Int) {
        leading.removeAllViews()
        val lp = LinearLayout.LayoutParams(Ui.dp(wDp), Ui.dp(hDp))
        lp.rightMargin = Ui.dp(14)
        leading.layoutParams = lp
        leading.addView(v, FrameLayout.LayoutParams(-1, -1))
        leading.visibility = View.VISIBLE
    }

    fun click(f: () -> Unit): PanelRow {
        setOnClickListener { f() }
        return this
    }
}

class SwitchRow(ctx: Context) : PanelRow(ctx) {
    private val toggle = ToggleView(ctx)
    var onChanged: ((Boolean) -> Unit)? = null
    var checked: Boolean
        get() = toggle.checked
        set(v) { toggle.checked = v }

    init {
        addView(toggle)
        setOnClickListener {
            toggle.checked = !toggle.checked
            onChanged?.invoke(toggle.checked)
        }
    }
}

class SeekRow(ctx: Context) : PanelRow(ctx) {
    private val bar = BarView(ctx)
    var value = 50
        set(v) { field = v.coerceIn(0, 100); bar.fraction = field / 100f; setTrail("$field%") }
    var minValue = 0
    var step = 5
    var onChanged: ((Int) -> Unit)? = null

    init {
        val lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(4))
        lp.topMargin = Ui.dp(8)
        lp.bottomMargin = Ui.dp(4)
        column.addView(bar, lp)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            val d = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step
            val nv = (value + d).coerceIn(minValue, 100)
            if (nv != value) {
                value = nv
                onChanged?.invoke(nv)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
