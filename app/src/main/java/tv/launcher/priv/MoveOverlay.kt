package tv.launcher.priv

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Режим «Переместить»: тёмный экран с сеткой, выбранная карточка с белой рамкой двигается стрелками. */
class MoveOverlay(ctx: Context, private val host: HomeActivity) : FrameLayout(ctx) {
    var active = false
        private set

    private val rv = RecyclerView(ctx)
    private var adapter: CardAdapter? = null
    private val list = ArrayList<AppEntry>()
    private var pos = 0
    private var span = 6
    private var done: ((List<AppEntry>) -> Unit)? = null

    init {
        visibility = View.GONE
        setBackgroundColor(Ui.PANEL_BG)
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
        rv.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        addView(rv, LayoutParams(-1, -1))
    }

    fun start(
        items: List<AppEntry>, grabbedKey: String, cfg: CardCfg,
        leftPadDp: Int, alphaPct: Int, onDone: (List<AppEntry>) -> Unit
    ) {
        list.clear()
        list.addAll(items)
        pos = list.indexOfFirst { it.key == grabbedKey }.let { if (it < 0) 0 else it }
        span = cfg.span
        done = onDone
        val ad = CardAdapter(host, CardCfg(cfg.cardW, cfg.cardH, cfg.cellW, cfg.cellH, cfg.scale, cfg.labelSp, false, cfg.span))
        ad.items = list
        ad.grabbedKey = grabbedKey
        ad.alphaPct = alphaPct
        adapter = ad
        rv.layoutManager = GridLayoutManager(context, span)
        rv.adapter = ad
        val rows = (list.size + span - 1) / span
        val screenH = resources.displayMetrics.heightPixels
        val contentH = rows * cfg.cellH
        val top = if (contentH < screenH) (screenH - contentH) / 2 else Ui.dp(20)
        rv.setPadding(Ui.dp(leftPadDp), top, 0, Ui.dp(20))
        active = true
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(220).start()
        rv.post { rv.scrollToPosition(pos) }
    }

    private fun moveTo(np: Int) {
        if (np < 0 || np >= list.size || np == pos) return
        val item = list.removeAt(pos)
        list.add(np, item)
        adapter?.notifyItemMoved(pos, np)
        pos = np
        rv.scrollToPosition(np)
    }

    private fun finish() {
        active = false
        animate().alpha(0f).setDuration(180).withEndAction {
            visibility = View.GONE
            rv.adapter = null
        }.start()
        done?.invoke(ArrayList(list))
    }

    /** Возвращает true, если клавиша обработана (в режиме перемещения обрабатываются все клавиши). */
    fun handleKey(e: KeyEvent): Boolean {
        if (!active) return false
        val k = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moveTo(pos - 1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveTo(pos + 1)
                KeyEvent.KEYCODE_DPAD_UP -> moveTo(pos - span)
                KeyEvent.KEYCODE_DPAD_DOWN -> moveTo(pos + span)
            }
        } else if (e.action == KeyEvent.ACTION_UP) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER ||
                k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE ||
                k == KeyEvent.KEYCODE_NUMPAD_ENTER
            ) finish()
        }
        return true
    }
}
