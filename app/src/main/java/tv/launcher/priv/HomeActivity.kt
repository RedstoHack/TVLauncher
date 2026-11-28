package tv.launcher.priv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.FileInputStream

class HomeActivity : Activity() {
    var allApps: List<AppEntry> = emptyList()
        private set

    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var bg: BackgroundView
    private lateinit var content: FrameLayout
    private lateinit var header: Header
    private lateinit var homePage: FrameLayout
    private lateinit var appsPage: FrameLayout
    private lateinit var favRv: RecyclerView
    private lateinit var appsRv: RecyclerView
    private lateinit var favAdapter: CardAdapter
    private lateinit var appsAdapter: CardAdapter
    private lateinit var cfgFav: CardCfg
    private lateinit var cfgApps: CardCfg
    private lateinit var menu: MenuPopup
    lateinit var panel: SidePanel
        private set
    private lateinit var move: MoveOverlay

    private var currentTab = -1
    private var started = false
    private var browserBase = 1
    private var returnFocus: View? = null

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { scheduleReload() }
    }
    private val reloadRunnable = Runnable { reloadApps() }
    private val clockRunnable = object : Runnable {
        override fun run() {
            header.updateClock()
            main.postDelayed(this, 15000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        buildUi()
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_CHANGED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, f)
        val p = Store.current
        header.bindProfile(p)
        bg.set(p)
        showTab(0)
        header.selectTab(0)
        reloadApps()
        handleIntent(intent, true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, false)
    }

    private fun handleIntent(i: Intent?, first: Boolean) {
        if (i == null) return
        val isHome = i.hasCategory(Intent.CATEGORY_HOME)
        val fromIcon = !isHome && (i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) || i.hasCategory(Intent.CATEGORY_LAUNCHER))
        if (fromIcon) {
            main.postDelayed({ openProfilePanel() }, 200)
        } else if (!first) {
            // кнопка «Домой»: закрыть всё и вернуться на главную
            if (menu.open) menu.hide()
            if (panel.open) panel.close()
            showTab(0)
            header.selectTab(0)
            favRv.scrollToPosition(0)
            favRv.post { favRv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.clipChildren = false
        setContentView(root)

        bg = BackgroundView(this)
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        content = FrameLayout(this)
        content.clipChildren = false
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val screenW = resources.displayMetrics.widthPixels
        val cw = (screenW - Ui.dp(100)) / 6
        val cardW = cw - Ui.dp(12)
        cfgApps = CardCfg(cardW, cardW * 9 / 16, cw, Ui.dp(114), 1.28f, 12f, false, 6)
        cfgFav = CardCfg(Ui.dp(96), Ui.dp(54), Ui.dp(108), Ui.dp(92), 1.29f, 11f, true, 8)

        header = Header(this)
        header.onTabFocus = { i -> showTab(i) }

        buildHomePage()
        buildAppsPage()
        content.addView(homePage, FrameLayout.LayoutParams(-1, -1))
        content.addView(appsPage, FrameLayout.LayoutParams(-1, -1))
        appsPage.visibility = View.GONE

        root.addView(header, FrameLayout.LayoutParams(-1, Ui.dp(84)))

        move = MoveOverlay(this, this)
        root.addView(move, FrameLayout.LayoutParams(-1, -1))
        panel = SidePanel(this)
        panel.onClosed = { onOverlayClosed() }
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        menu = MenuPopup(this)
        menu.onHidden = { onOverlayClosed() }
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))

        header.gear.setOnClickListener { openSystemSettings() }
        header.profileBtn.setOnClickListener { openProfilePanel() }
    }

    private fun sectionTitle(t: String): TextView {
        val tv = Ui.text(this, t, 11f, 0xFFD0D3D6.toInt())
        val lp = FrameLayout.LayoutParams(-2, -2)
        lp.leftMargin = Ui.dp(54)
        lp.topMargin = Ui.dp(88)
        tv.layoutParams = lp
        return tv
    }

    private fun buildHomePage() {
        homePage = FrameLayout(this)
        homePage.clipChildren = false
        homePage.addView(sectionTitle("Любимые приложения"))

        favRv = RecyclerView(this)
        favRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favRv.clipChildren = false
        favRv.clipToPadding = false
        favRv.setPadding(Ui.dp(54), 0, Ui.dp(54), 0)
        favRv.itemAnimator = null
        favAdapter = CardAdapter(this, cfgFav)
        favAdapter.upFocusId = header.tabs[0].id
        favAdapter.onClick = { app -> openApp(app) }
        favAdapter.onLong = { app, cell -> showMenu(app, cell, true) }
        favAdapter.onPlus = { showPicker() }
        favRv.adapter = favAdapter
        val rlp = FrameLayout.LayoutParams(-1, Ui.dp(110))
        rlp.topMargin = Ui.dp(117)
        homePage.addView(favRv, rlp)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        val c1 = homeCard(R.drawable.ic_customize, "Настройте главный экран",
            "Добавляйте приложения в избранное или удаляйте их оттуда.") { showPicker() }
        val c2 = homeCard(R.drawable.ic_person, "Профили и фон",
            "Меняйте профиль, фон, звук и значки приложений.") { openProfilePanel() }
        val l1 = LinearLayout.LayoutParams(0, -1, 1f)
        l1.rightMargin = Ui.dp(8)
        val l2 = LinearLayout.LayoutParams(0, -1, 1f)
        l2.leftMargin = Ui.dp(8)
        row.addView(c1, l1)
        row.addView(c2, l2)
        val clp = FrameLayout.LayoutParams(-1, Ui.dp(176))
        clp.leftMargin = Ui.dp(54)
        clp.rightMargin = Ui.dp(54)
        clp.topMargin = Ui.dp(253)
        homePage.addView(row, clp)
    }

    private fun homeCard(icon: Int, title: String, desc: String, onClick: () -> Unit): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.setPadding(Ui.dp(24), Ui.dp(22), Ui.dp(24), Ui.dp(16))
        v.isFocusable = true
        v.isClickable = true
        v.background = Ui.rounded(0xFF373737.toInt(), 9f)
        v.addView(Ui.icon(this, icon, 22, 0xFFD0D3D6.toInt()))
        val sp = View(this)
        v.addView(sp, LinearLayout.LayoutParams(1, 0, 1f))
        val t = Ui.text(this, title, 16f, 0xFFF1F3F4.toInt())
        v.addView(t)
        val d = Ui.text(this, desc, 11f, 0xFFD0D3D6.toInt())
        d.setPadding(0, Ui.dp(8), 0, Ui.dp(28))
        v.addView(d)
        v.setOnClickListener { onClick() }
        v.setOnFocusChangeListener { _, has ->
            v.background = Ui.rounded(if (has) 0xFF565656.toInt() else 0xFF373737.toInt(), 9f)
            v.animate().scaleX(if (has) 1.03f else 1f).scaleY(if (has) 1.03f else 1f)
                .translationZ(if (has) Ui.dpf(8f) else 0f).setDuration(150).start()
        }
        return v
    }

    private fun buildAppsPage() {
        appsPage = FrameLayout(this)
        appsPage.clipChildren = false
        appsPage.addView(sectionTitle("Установленные приложения"))

        appsRv = RecyclerView(this)
        appsRv.layoutManager = GridLayoutManager(this, 6)
        appsRv.clipChildren = false
        appsRv.clipToPadding = false
        appsRv.setPadding(Ui.dp(50), Ui.dp(12), 0, Ui.dp(40))
        appsRv.itemAnimator = null
        appsAdapter = CardAdapter(this, cfgApps)
        appsAdapter.upFocusId = header.tabs[1].id
        appsAdapter.onClick = { app -> openApp(app) }
        appsAdapter.onLong = { app, cell -> showMenu(app, cell, false) }
        appsRv.adapter = appsAdapter
        val lp = FrameLayout.LayoutParams(-1, -1)
        lp.topMargin = Ui.dp(106)
        appsPage.addView(appsRv, lp)
    }

    private fun showTab(i: Int) {
        if (i == currentTab) return
        val old = if (currentTab == 0) homePage else if (currentTab == 1) appsPage else null
        val nw = if (i == 0) homePage else appsPage
        currentTab = i
        header.selectTab(i)
        old?.animate()?.cancel()
        old?.visibility = View.GONE
        nw.visibility = View.VISIBLE
        nw.alpha = 0f
        nw.animate().alpha(1f).setDuration(220).start()
    }

    // ---------------------------------------------------------------- данные

    fun visibleApps(): List<AppEntry> {
        val p = Store.current
        return Store.sortApps(p, allApps).filter {
            !p.hidden.contains(it.key) && !(it.isSelf && p.hideSelf)
        }
    }

    private fun scheduleReload() {
        main.removeCallbacks(reloadRunnable)
        main.postDelayed(reloadRunnable, 600)
    }

    private fun reloadApps() {
        val ctx = applicationContext
        Thread {
            val l = AppRepo.load(ctx)
            main.post {
                allApps = l
                refreshLists()
            }
        }.start()
    }

    fun refreshLists() {
        val p = Store.current
        val vis = visibleApps()
        val byKey = HashMap<String, AppEntry>()
        for (a in vis) byKey[a.key] = a
        val favs = ArrayList<AppEntry>()
        for (k in p.favorites) byKey[k]?.let { favs.add(it) }
        favAdapter.items = favs
        favAdapter.alphaPct = p.iconAlpha
        favAdapter.notifyDataSetChanged()
        appsAdapter.items = vis
        appsAdapter.alphaPct = p.iconAlpha
        appsAdapter.notifyDataSetChanged()
        if (!started || currentFocus == null) {
            main.post { if (currentFocus == null && !panel.open && !menu.open) focusDefault() }
        }
    }

    private fun focusDefault() {
        val rv = if (currentTab == 0) favRv else appsRv
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    fun applyBackground() { bg.set(Store.current) }

    fun applyIcons() {
        val a = Store.current.iconAlpha
        favAdapter.alphaPct = a
        appsAdapter.alphaPct = a
        favAdapter.notifyDataSetChanged()
        appsAdapter.notifyDataSetChanged()
    }

    fun applySound() {
        val p = Store.current
        SoundPlayer.apply(p, started)
        if (p.soundEverywhere && SoundPlayer.pathFor(p) != null) SoundService.start(this)
        else SoundService.stop(this)
    }

    fun onProfileMeta() {
        header.bindProfile(Store.current)
    }

    fun switchProfile(p: Profile) {
        Store.select(p)
        header.bindProfile(p)
        bg.set(p)
        applySound()
        refreshLists()
    }

    fun addFavorite(app: AppEntry) {
        val p = Store.current
        if (!p.favorites.contains(app.key)) p.favorites.add(app.key)
        Store.save()
        panel.close()
        refreshLists()
        showTab(0)
        main.postDelayed({ focusKey(app.key, true) }, 250)
    }

    private fun focusKey(key: String, fav: Boolean) {
        val rv = if (fav) favRv else appsRv
        val ad = if (fav) favAdapter else appsAdapter
        val idx = ad.items.indexOfFirst { it.key == key }
        if (idx < 0) return
        rv.scrollToPosition(idx)
        rv.postDelayed({
            rv.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus()
        }, 80)
    }

    // ---------------------------------------------------------------- действия

    private fun openApp(app: AppEntry) {
        if (app.isSelf) { openProfilePanel(); return }
        if (!AppRepo.launch(this, app)) Toast.makeText(this, "Не удалось открыть " + app.label, Toast.LENGTH_SHORT).show()
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show()
        }
    }

    fun openProfilePanel() {
        if (menu.open) menu.hide()
        returnFocus = currentFocus
        updateBlock(true)
        panel.show(Pages.profiles(this))
    }

    private fun showPicker() {
        returnFocus = currentFocus
        updateBlock(true)
        panel.show(Pages.picker(this))
    }

    private fun showMenu(app: AppEntry, cell: CardCell, favTab: Boolean) {
        val p = Store.current
        val isFav = p.favorites.contains(app.key)
        val items = ArrayList<MenuPopup.Item>()
        items.add(MenuPopup.Item(R.drawable.ic_open, "Открыть") { openApp(app) })
        items.add(MenuPopup.Item(R.drawable.ic_swap, "Переместить") { startMove(app, favTab) })
        if (favTab || isFav) {
            items.add(MenuPopup.Item(R.drawable.ic_heart, "Удалить из избранного") {
                p.favorites.remove(app.key); Store.save(); refreshLists()
                main.postDelayed({ if (currentFocus == null) focusDefault() }, 100)
            })
        } else {
            items.add(MenuPopup.Item(R.drawable.ic_heart, "Добавить в избранное") {
                p.favorites.add(app.key); Store.save(); refreshLists()
            })
        }
        if (!favTab && !app.isSelf) {
            items.add(MenuPopup.Item(R.drawable.ic_info, "О приложении") {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.key)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Недоступно", Toast.LENGTH_SHORT).show()
                }
            })
            items.add(MenuPopup.Item(R.drawable.ic_delete, "Удалить") {
                try {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.key)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось удалить", Toast.LENGTH_SHORT).show()
                }
            })
        }
        val r = Rect()
        cell.scaledBounds(r)
        val shot = menu.snapshot(cell)
        returnFocus = cell
        updateBlock(true)
        menu.show(r, shot, app.label, items)
    }

    private fun startMove(app: AppEntry, favTab: Boolean) {
        val p = Store.current
        updateBlock(true)
        if (favTab) {
            move.start(ArrayList(favAdapter.items), app.key, cfgFav, 54, p.iconAlpha) { list ->
                p.favorites.clear()
                for (a in list) p.favorites.add(a.key)
                Store.save()
                updateBlock(false)
                refreshLists()
                main.postDelayed({ focusKey(app.key, true) }, 250)
            }
        } else {
            move.start(ArrayList(appsAdapter.items), app.key, cfgApps, 50, p.iconAlpha) { list ->
                val keys = ArrayList<String>()
                for (a in list) keys.add(a.key)
                val set = HashSet<String>(keys)
                for (k in p.order) if (!set.contains(k)) keys.add(k)
                p.order.clear()
                p.order.addAll(keys)
                Store.save()
                updateBlock(false)
                refreshLists()
                main.postDelayed({ focusKey(app.key, false) }, 250)
            }
        }
    }

    // ---------------------------------------------------------------- фокус и клавиши

    private fun updateBlock(block: Boolean) {
        val mode = if (block) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        content.descendantFocusability = mode
        header.descendantFocusability = mode
    }

    private fun onOverlayClosed() {
        if (menu.open || panel.open || move.active) return
        updateBlock(false)
        val rf = returnFocus
        returnFocus = null
        if (rf != null && rf.isAttachedToWindow && rf.isShown) rf.requestFocus()
        else main.postDelayed({ if (currentFocus == null) focusDefault() }, 100)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (move.active) return move.handleKey(e)
        if (e.keyCode == KeyEvent.KEYCODE_MENU && e.action == KeyEvent.ACTION_UP) {
            val f = currentFocus
            if (f is CardCell) { f.performLongClick(); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (menu.open) { menu.hide(); return }
        if (panel.open) { panel.back(); return }
        val f = currentFocus
        if (f != null && f !is TabView && f !is HeaderButton) {
            header.tabs[if (currentTab < 0) 0 else currentTab].requestFocus()
            return
        }
        if (currentTab == 1) { header.tabs[0].requestFocus(); return }
        // на главном экране «Назад» ничего не закрывает
    }

    // ---------------------------------------------------------------- файлы

    fun pickMedia(p: Profile, kind: Kind, prefix: String, onSaved: (String) -> Unit) {
        FilePicker.pick(this, kind) { picked ->
            Toast.makeText(this, "Копирование файла…", Toast.LENGTH_SHORT).show()
            Thread {
                var name: String? = null
                try {
                    name = if (picked.uri != null) Store.copyInUri(p, prefix, picked.uri, picked.ext)
                    else if (picked.file != null) Store.copyIn(p, prefix, picked.ext, FileInputStream(picked.file))
                    else null
                } catch (e: Exception) { name = null }
                runOnUiThread {
                    if (name != null) onSaved(name)
                    else Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
    }

    fun showFileBrowser(kind: Kind) {
        browserBase = panel.depth
        panel.push(FileBrowser.build(this, kind, null, panel))
    }

    fun closeFileBrowser() {
        panel.popTo(browserBase)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FilePicker.onResult(this, requestCode, resultCode == RESULT_OK, data)
    }

    // ---------------------------------------------------------------- жизненный цикл

    override fun onStart() {
        super.onStart()
        started = true
        bg.resume()
        applySound()
        header.updateClock()
        main.removeCallbacks(clockRunnable)
        main.postDelayed(clockRunnable, 15000)
        if (allApps.isNotEmpty()) scheduleReload()
    }

    override fun onStop() {
        started = false
        bg.pause()
        main.removeCallbacks(clockRunnable)
        val p = Store.current
        if (!(p.soundEverywhere && SoundPlayer.pathFor(p) != null)) SoundPlayer.apply(p, false)
        Store.saveNow()
        super.onStop()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
