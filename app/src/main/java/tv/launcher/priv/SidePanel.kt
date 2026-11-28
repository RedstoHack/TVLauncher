package tv.launcher.priv

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Страница боковой панели: заголовок + прокручиваемый список строк. */
class Page(ctx: Context, val title: String) {
    val root = LinearLayout(ctx)
    val list = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    var lastFocused: View? = null
    var defaultFocus: View? = null

    init {
        root.orientation = LinearLayout.VERTICAL
        val head = Ui.text(ctx, title, 18f, 0xFFFFFFFF.toInt(), true)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.dp(20), 0, Ui.dp(20), 0)
        head.setBackgroundColor(Ui.PANEL_HEADER)
        root.addView(head, LinearLayout.LayoutParams(-1, Ui.dp(64)))
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, Ui.dp(4), 0, Ui.dp(24))
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFillViewport = true
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun add(v: View): View {
        list.addView(v, LinearLayout.LayoutParams(-1, -2))
        if (defaultFocus == null && v.isFocusable) defaultFocus = v
        return v
    }

    fun section(ctx: Context, t: String) {
        val tv = Ui.text(ctx, t, 11f, Ui.ACCENT, true)
        tv.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(4))
        list.addView(tv, LinearLayout.LayoutParams(-1, -2))
    }

    fun focusFirst() {
        val d = lastFocused ?: defaultFocus
        if (d != null && d.isAttachedToWindow) d.requestFocus()
        else root.post { (lastFocused ?: defaultFocus)?.requestFocus() }
    }
}

/** Панель справа (как «Выберите приложение» в оригинале): затемнение + выезжающая панель. */
class SidePanel(ctx: Context) : FrameLayout(ctx) {
    private val scrim = View(ctx)
    private val panel = FrameLayout(ctx)
    private val stack = ArrayList<Page>()
    var open = false
        private set
    var onClosed: (() -> Unit)? = null

    init {
        visibility = View.GONE
        scrim.setBackgroundColor(0xB8000000.toInt())
        addView(scrim, LayoutParams(-1, -1))
        panel.setBackgroundColor(Ui.PANEL_BG)
        panel.elevation = Ui.dpf(16f)
        addView(panel, LayoutParams(Ui.dp(360), -1, Gravity.END))
    }

    val current: Page? get() = stack.lastOrNull()

    fun show(page: Page) {
        stack.clear()
        stack.add(page)
        panel.removeAllViews()
        panel.addView(page.root, LayoutParams(-1, -1))
        if (!open) {
            open = true
            visibility = View.VISIBLE
            scrim.alpha = 0f
            scrim.animate().alpha(1f).setDuration(250).start()
            panel.translationX = Ui.dpf(360f)
            panel.animate().translationX(0f).setDuration(250)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        page.focusFirst()
    }

    fun push(page: Page) {
        val cur = stack.lastOrNull()
        if (cur != null) cur.lastFocused = panel.findFocus()
        stack.add(page)
        panel.removeAllViews()
        panel.addView(page.root, LayoutParams(-1, -1))
        page.root.alpha = 0f
        page.root.animate().alpha(1f).setDuration(150).start()
        page.focusFirst()
    }

    /** Назад на предыдущую страницу или закрыть панель. */
    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            val page = stack.last()
            panel.removeAllViews()
            panel.addView(page.root, LayoutParams(-1, -1))
            page.root.alpha = 0f
            page.root.animate().alpha(1f).setDuration(150).start()
            page.focusFirst()
        } else {
            close()
        }
    }

    val depth: Int get() = stack.size

    /** Вернуться на страницу с заданной глубиной стека. */
    fun popTo(n: Int) {
        if (stack.isEmpty()) return
        while (stack.size > n && stack.size > 1) stack.removeAt(stack.size - 1)
        val page = stack.last()
        panel.removeAllViews()
        panel.addView(page.root, LayoutParams(-1, -1))
        page.focusFirst()
    }

    /** Перестроить текущую страницу (после изменений) с сохранением положения. */
    fun replaceCurrent(page: Page) {
        if (stack.isEmpty()) return
        stack[stack.size - 1] = page
        panel.removeAllViews()
        panel.addView(page.root, LayoutParams(-1, -1))
        page.focusFirst()
    }

    fun close() {
        if (!open) return
        open = false
        scrim.animate().alpha(0f).setDuration(200).start()
        panel.animate().translationX(Ui.dpf(360f)).setDuration(200).withEndAction {
            visibility = View.GONE
            panel.removeAllViews()
            stack.clear()
        }.start()
        onClosed?.invoke()
    }
}
