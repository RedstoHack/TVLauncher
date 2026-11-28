package tv.launcher.priv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class CaretView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { p.color = 0xFFEDEDED.toInt() }
    override fun onDraw(c: Canvas) {
        val path = Path()
        path.moveTo(0f, height.toFloat())
        path.lineTo(width / 2f, 0f)
        path.lineTo(width.toFloat(), height.toFloat())
        path.close()
        c.drawPath(path, p)
    }
}

/** Всплывающее меню приложения: затемнение, карточка сверху, список действий. */
class MenuPopup(ctx: Context) : FrameLayout(ctx) {
    class Item(val icon: Int, val title: String, val action: () -> Unit)

    var onHidden: (() -> Unit)? = null
    var open = false
        private set

    private val scrim = View(ctx)
    private val snap = ImageView(ctx)
    private val name = TextView(ctx)
    private val menu = LinearLayout(ctx)
    private val caret = CaretView(ctx)

    init {
        visibility = View.GONE
        scrim.setBackgroundColor(0xD9000000.toInt())
        addView(scrim, LayoutParams(-1, -1))
        addView(snap, LayoutParams(-2, -2))
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        name.setTextColor(0xFFF1F3F4.toInt())
        name.gravity = Gravity.CENTER
        addView(name, LayoutParams(-2, -2))
        menu.orientation = LinearLayout.VERTICAL
        menu.background = Ui.rounded(0xFFEDEDED.toInt(), 4f)
        menu.clipToOutline = true
        menu.elevation = Ui.dpf(12f)
        addView(caret, LayoutParams(Ui.dp(14), Ui.dp(7)))
        addView(menu, LayoutParams(Ui.dp(229), -2))
    }

    fun snapshot(cell: CardCell): Bitmap? {
        return try {
            val w = (cell.card.width * cell.card.scaleX).toInt()
            val h = (cell.card.height * cell.card.scaleY).toInt()
            if (w <= 0 || h <= 0) return null
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(cell.card.scaleX, cell.card.scaleY)
            cell.card.draw(c)
            b
        } catch (e: Throwable) {
            null
        }
    }

    fun show(anchor: Rect, shot: Bitmap?, label: String, items: List<Item>) {
        menu.removeAllViews()
        val ctx = context
        for (it in items) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.isFocusable = true
            row.isClickable = true
            row.background = Ui.focusBg(0xFFD3D3D3.toInt(), 0f)
            row.setPadding(Ui.dp(16), 0, Ui.dp(16), 0)
            val ic = Ui.icon(ctx, it.icon, 24, 0xFF202124.toInt())
            row.addView(ic)
            val t = Ui.text(ctx, it.title, 14f, 0xFF202124.toInt())
            val tlp = LinearLayout.LayoutParams(-2, -2)
            tlp.leftMargin = Ui.dp(16)
            row.addView(t, tlp)
            row.setOnClickListener { _ ->
                hide()
                it.action()
            }
            menu.addView(row, LinearLayout.LayoutParams(-1, Ui.dp(48)))
        }

        // картинка карточки и подпись
        val sw = shot?.width ?: anchor.width()
        val sh = shot?.height ?: anchor.height()
        snap.setImageBitmap(shot)
        val slp = snap.layoutParams as LayoutParams
        slp.width = sw; slp.height = sh
        slp.leftMargin = anchor.left; slp.topMargin = anchor.top
        snap.layoutParams = slp
        name.text = label
        val nlp = name.layoutParams as LayoutParams
        nlp.width = sw
        nlp.leftMargin = anchor.left
        nlp.topMargin = anchor.bottom + Ui.dp(3)
        name.layoutParams = nlp

        // меню: под карточкой, если не помещается — над ней
        val menuH = items.size * Ui.dp(48)
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val menuW = Ui.dp(229)
        val gap = Ui.dp(16)
        var top = anchor.bottom + gap
        var below = true
        if (top + menuH > screenH - Ui.dp(12)) {
            top = anchor.top - gap - menuH
            below = false
        }
        if (top < Ui.dp(8)) top = Ui.dp(8)
        var left = anchor.left
        if (left + menuW > screenW - Ui.dp(12)) left = screenW - Ui.dp(12) - menuW
        if (left < Ui.dp(8)) left = Ui.dp(8)
        val mlp = menu.layoutParams as LayoutParams
        mlp.leftMargin = left; mlp.topMargin = top
        menu.layoutParams = mlp

        val clp = caret.layoutParams as LayoutParams
        clp.leftMargin = left + Ui.dp(19)
        clp.topMargin = top - Ui.dp(7)
        caret.layoutParams = clp
        caret.visibility = if (below) View.VISIBLE else View.GONE
        name.visibility = if (below) View.VISIBLE else View.GONE

        open = true
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(140).start()
        menu.scaleX = 0.94f; menu.scaleY = 0.94f
        menu.pivotX = 0f; menu.pivotY = 0f
        menu.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        if (menu.childCount > 0) menu.getChildAt(0).requestFocus()
    }

    fun hide() {
        if (!open) return
        open = false
        animate().alpha(0f).setDuration(100).withEndAction {
            visibility = View.GONE
            menu.removeAllViews()
            snap.setImageBitmap(null)
        }.start()
        onHidden?.invoke()
    }
}
