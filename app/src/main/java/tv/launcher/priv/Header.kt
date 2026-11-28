package tv.launcher.priv

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

class TabView(ctx: Context, title: String) : TextView(ctx) {
    var sel = false
        set(v) { field = v; refresh() }

    init {
        text = title
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(Ui.dp(14), 0, Ui.dp(14), 0)
        isFocusable = true
        isClickable = true
        refresh()
    }

    fun refresh() {
        if (isFocused) {
            background = Ui.rounded(0xFFF1F3F4.toInt(), 16f)
            setTextColor(0xFF202124.toInt())
        } else if (sel) {
            background = Ui.rounded(0xFF3D3D3D.toInt(), 16f)
            setTextColor(0xFFE8EAED.toInt())
        } else {
            background = null
            setTextColor(0xFFA8ABAF.toInt())
        }
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect)
        refresh()
    }
}

/** Круглая кнопка в шапке (шестерёнка / профиль): при фокусе — белый круг и подпись снизу. */
class HeaderButton(ctx: Context, private val labelText: String) : FrameLayout(ctx) {
    val gearIcon = ImageView(ctx)
    val avatar = AvatarView(ctx)
    private val label = Ui.text(ctx, labelText, 11f, 0xFFE8EAED.toInt())

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        isClickable = true
        gearIcon.visibility = View.GONE
        avatar.visibility = View.GONE
        addView(gearIcon, LayoutParams(Ui.dp(20), Ui.dp(20), Gravity.CENTER))
        addView(avatar, LayoutParams(Ui.dp(20), Ui.dp(20), Gravity.CENTER))
        label.alpha = 0f
        label.gravity = Gravity.CENTER
        val llp = LayoutParams(Ui.dp(80), -2)
        llp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        llp.topMargin = Ui.dp(40)
        addView(label, llp)
        refresh(false)
    }

    fun useGear() {
        gearIcon.setImageResource(R.drawable.ic_gear)
        gearIcon.visibility = View.VISIBLE
        refresh(isFocused)
    }

    fun useAvatar() {
        avatar.visibility = View.VISIBLE
        refresh(isFocused)
    }

    private fun refresh(f: Boolean) {
        if (f) {
            background = Ui.rounded(0xFFF1F3F4.toInt(), 18f)
            gearIcon.setColorFilter(0xFF202124.toInt(), PorterDuff.Mode.SRC_IN)
            avatar.ring = false
        } else {
            background = null
            gearIcon.setColorFilter(0xFFB8BBBF.toInt(), PorterDuff.Mode.SRC_IN)
        }
        avatar.invalidate()
        label.animate().alpha(if (f) 1f else 0f).setDuration(120).start()
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect)
        refresh(gained)
    }
}

class Header(ctx: Context) : FrameLayout(ctx) {
    val tabs = ArrayList<TabView>()
    val gear = HeaderButton(ctx, "Настройки")
    val profileBtn = HeaderButton(ctx, "Профили")
    private val clock = Ui.text(ctx, "", 14f, 0xFFD0D3D6.toInt())
    var onTabFocus: ((Int) -> Unit)? = null
    private val tabRow = LinearLayout(ctx)

    init {
        clipChildren = false
        clipToPadding = false
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.clipChildren = false
        for ((i, t) in listOf("Главная", "Приложения").withIndex()) {
            val tv = TabView(ctx, t)
            tv.id = View.generateViewId()
            tv.setOnFocusChangeListener { _, has -> if (has) onTabFocus?.invoke(i) }
            tabs.add(tv)
            val lp = LinearLayout.LayoutParams(-2, Ui.dp(31))
            lp.rightMargin = Ui.dp(8)
            tabRow.addView(tv, lp)
        }
        val tlp = LayoutParams(-2, -2)
        tlp.leftMargin = Ui.dp(54)
        tlp.topMargin = Ui.dp(26)
        addView(tabRow, tlp)

        gear.useGear()
        profileBtn.useAvatar()
        val right = LinearLayout(ctx)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL
        right.clipChildren = false
        right.addView(gear, LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)))
        val plp = LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36))
        plp.leftMargin = Ui.dp(13)
        right.addView(profileBtn, plp)
        val clp = LinearLayout.LayoutParams(-2, -2)
        clp.leftMargin = Ui.dp(22)
        right.addView(clock, clp)
        val rlp = LayoutParams(-2, -2)
        rlp.gravity = Gravity.END or Gravity.TOP
        rlp.rightMargin = Ui.dp(54)
        rlp.topMargin = Ui.dp(24)
        addView(right, rlp)
        updateClock()
    }

    fun selectTab(i: Int) {
        for ((k, t) in tabs.withIndex()) t.sel = (k == i)
    }

    fun updateClock() {
        clock.text = DateFormat.getTimeFormat(context).format(Date())
    }

    fun bindProfile(p: Profile) {
        profileBtn.avatar.bind(p)
    }
}
