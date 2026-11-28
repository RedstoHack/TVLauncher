package tv.launcher.priv

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Outline

class CardCfg(
    val cardW: Int, val cardH: Int,
    val cellW: Int, val cellH: Int,
    val scale: Float, val labelSp: Float,
    val plus: Boolean, val span: Int
)

/** Ячейка: карточка приложения (баннер) + подпись под ней при фокусе. */
class CardCell(ctx: Context, private val cfg: CardCfg) : FrameLayout(ctx) {
    val card = FrameLayout(ctx)
    private val image = ImageView(ctx)
    private val plusIcon = Ui.icon(ctx, R.drawable.ic_add, 28, 0xFFE8EAED.toInt())
    val label = TextView(ctx)
    var tag2: String? = null
    var iconAlpha = 1f
    private val border = GradientDrawable()

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        isClickable = true
        isLongClickable = true
        layoutParams = RecyclerView.LayoutParams(cfg.cellW, cfg.cellH)

        card.setBackgroundColor(0xFF26323F.toInt())
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, Ui.dpf(4f))
            }
        }
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        card.addView(image, FrameLayout.LayoutParams(-1, -1))
        plusIcon.visibility = View.GONE
        card.addView(plusIcon, FrameLayout.LayoutParams(Ui.dp(28), Ui.dp(28), Gravity.CENTER))
        border.setColor(0x00000000)
        border.setStroke(Ui.dp(2), 0xFFFFFFFF.toInt())
        border.cornerRadius = Ui.dpf(4f)
        addView(card, FrameLayout.LayoutParams(cfg.cardW, cfg.cardH))

        val lw = (cfg.cardW * cfg.scale).toInt()
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, cfg.labelSp)
        label.setTextColor(0xFFF1F3F4.toInt())
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.alpha = 0f
        val llp = FrameLayout.LayoutParams(lw, ViewGroup.LayoutParams.WRAP_CONTENT)
        llp.leftMargin = (cfg.cardW - lw) / 2
        llp.topMargin = (cfg.cardH * (1f + cfg.scale) / 2f).toInt() + Ui.dp(3)
        addView(label, llp)

        setOnFocusChangeListener { _, has -> applyFocus(has, true) }
    }

    fun setPlus(on: Boolean) {
        plusIcon.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            image.setImageDrawable(null)
            card.setBackgroundColor(0xFF2D3E50.toInt())
            label.text = ""
        } else {
            card.setBackgroundColor(0xFF26323F.toInt())
        }
    }

    fun setImage(b: android.graphics.Bitmap?) {
        image.setImageBitmap(b)
    }

    fun setGrabbed(g: Boolean) {
        card.foreground = if (g) border else null
    }

    fun applyFocus(has: Boolean, animate: Boolean) {
        val s = if (has) cfg.scale else 1f
        val z = if (has) Ui.dpf(10f) else 0f
        val a = if (has) 1f else iconAlpha
        if (animate) {
            card.animate().scaleX(s).scaleY(s).alpha(a).setDuration(150).start()
            animate().translationZ(z).setDuration(150).start()
            label.animate().alpha(if (has) 1f else 0f).setDuration(150).start()
        } else {
            card.scaleX = s; card.scaleY = s; card.alpha = a
            translationZ = z
            label.alpha = if (has) 1f else 0f
        }
    }

    /** Область карточки на экране с учётом увеличения при фокусе. */
    fun scaledBounds(out: Rect) {
        val loc = IntArray(2)
        card.getLocationOnScreen(loc)
        val w = card.width * card.scaleX
        val h = card.height * card.scaleY
        val cx = loc[0] + card.width / 2f
        val cy = loc[1] + card.height / 2f
        out.set((cx - w / 2).toInt(), (cy - h / 2).toInt(), (cx + w / 2).toInt(), (cy + h / 2).toInt())
    }
}

class CardAdapter(private val host: HomeActivity, private val cfg: CardCfg) :
    RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(val cell: CardCell) : RecyclerView.ViewHolder(cell)

    var items: List<AppEntry> = emptyList()
    var onClick: ((AppEntry) -> Unit)? = null
    var onLong: ((AppEntry, CardCell) -> Unit)? = null
    var onPlus: (() -> Unit)? = null
    var grabbedKey: String? = null
    var upFocusId: Int = View.NO_ID
    var alphaPct: Int = 100

    init { setHasStableIds(true) }

    override fun getItemCount(): Int = items.size + if (cfg.plus) 1 else 0

    override fun getItemId(position: Int): Long =
        if (position < items.size) items[position].key.hashCode().toLong() else -1L

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(CardCell(parent.context, cfg))

    override fun onBindViewHolder(h: VH, position: Int) {
        val cell = h.cell
        cell.nextFocusUpId = if (position < cfg.span) upFocusId else View.NO_ID
        cell.iconAlpha = alphaPct / 100f
        if (position >= items.size) {
            cell.tag2 = null
            cell.setPlus(true)
            cell.setImage(null)
            cell.setGrabbed(false)
            cell.setOnClickListener { onPlus?.invoke() }
            cell.setOnLongClickListener { true }
            cell.applyFocus(cell.hasFocus(), false)
            return
        }
        val app = items[position]
        cell.setPlus(false)
        cell.label.text = app.label
        cell.setGrabbed(app.key == grabbedKey)
        cell.setOnClickListener { onClick?.invoke(app) }
        cell.setOnLongClickListener { onLong?.invoke(app, cell); true }
        cell.applyFocus(cell.hasFocus(), false)
        if (app.key == grabbedKey) {
            cell.card.scaleX = 1.1f; cell.card.scaleY = 1.1f; cell.card.alpha = 1f
            cell.translationZ = Ui.dpf(10f)
        }

        val custom = Store.mediaFile(Store.current, Store.current.customIcons[app.key])
        cell.tag2 = app.key
        val cached = Images.cached(app, custom)
        if (cached != null) {
            cell.setImage(cached)
        } else {
            cell.setImage(null)
            Images.loadCard(host, app, custom) { bmp ->
                if (cell.tag2 == app.key) cell.setImage(bmp)
            }
        }
    }
}
