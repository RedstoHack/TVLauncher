package tv.launcher.priv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout

class ColorDot(ctx: Context, val dotColor: Int) : View(ctx) {
    var checked = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = true
        isClickable = true
    }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(Ui.dp(40), Ui.dp(40)) }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (isFocused) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = Ui.dpf(2.5f)
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(cx, cy, Ui.dpf(16f), p)
        }
        p.style = Paint.Style.FILL
        p.color = dotColor
        c.drawCircle(cx, cy, Ui.dpf(12f), p)
        if (checked) {
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(cx, cy, Ui.dpf(4f), p)
        }
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gained, direction, previouslyFocusedRect)
        invalidate()
    }
}

/** Все страницы боковых панелей: профили, настройки профиля, приложения, выбор избранного. */
object Pages {

    // ---------- «Выберите приложение» (добавить в избранное) ----------
    fun picker(h: HomeActivity): Page {
        val page = Page(h, "Выберите приложение")
        val fav = Store.current.favorites.toSet()
        val list = h.visibleApps().filter { !fav.contains(it.key) }.sortedBy { it.label.lowercase() }
        if (list.isEmpty()) page.add(PanelRow(h).set("Все приложения уже в избранном"))
        for (app in list) {
            val row = PanelRow(h)
            row.set(app.label)
            val iv = ImageView(h)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(0xFF2B3036.toInt())
            row.setLeadingSized(iv, 55, 31)
            thumb(h, app, iv)
            row.click { h.addFavorite(app) }
            page.add(row)
        }
        return page
    }

    private fun thumb(h: HomeActivity, app: AppEntry, iv: ImageView) {
        val custom = Store.mediaFile(Store.current, Store.current.customIcons[app.key])
        Images.loadCard(h, app, custom) { b -> iv.setImageBitmap(b) }
    }

    // ---------- Профили ----------
    fun profiles(h: HomeActivity): Page {
        val page = Page(h, "Профили")
        for (p in ArrayList(Store.profiles)) {
            val row = PanelRow(h)
            val av = AvatarView(h)
            av.bind(p)
            row.setLeadingView(av, 32)
            row.set(p.name, if (p.id == Store.currentId) "Выбран" else null)
            row.setTrail(if (p.id == Store.currentId) "✓" else null)
            row.click {
                if (p.id != Store.currentId) {
                    h.switchProfile(p)
                    h.panel.replaceCurrent(profiles(h))
                } else {
                    h.panel.push(settings(h, p))
                }
            }
            row.setOnLongClickListener { h.panel.push(settings(h, p)); true }
            page.add(row)
        }
        page.add(PanelRow(h).set("Настроить профиль «" + Store.current.name + "»", "Имя, аватар, фон, звук, значки").click {
            h.panel.push(settings(h, Store.current))
        })
        page.add(PanelRow(h).set("+ Новый профиль").click {
            val np = Store.create("Профиль " + (Store.profiles.size + 1))
            h.switchProfile(np)
            h.panel.replaceCurrent(profiles(h))
            h.panel.push(settings(h, np))
        })
        return page
    }

    // ---------- Настройки профиля ----------
    fun settings(h: HomeActivity, p: Profile): Page {
        val page = Page(h, "Профиль")

        // имя
        page.section(h, "ИМЯ")
        val et = EditText(h)
        et.setText(p.name)
        et.setSingleLine(true)
        et.setTextColor(Ui.TEXT_MAIN)
        et.setHintTextColor(Ui.TEXT_DIM)
        et.hint = "Имя профиля"
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        et.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        et.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(12))
        et.background = Ui.focusBg(Ui.ROW_FOCUS, 0f)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                p.name = s?.toString() ?: ""
                Store.save()
                h.onProfileMeta()
            }
        })
        page.add(et)

        // аватар
        page.section(h, "АВАТАР")
        val dots = ArrayList<ColorDot>()
        val rowA = LinearLayout(h)
        val rowB = LinearLayout(h)
        rowA.setPadding(Ui.dp(14), 0, 0, 0)
        rowB.setPadding(Ui.dp(14), 0, 0, 0)
        for ((i, col) in Store.AVATAR_COLORS.withIndex()) {
            val d = ColorDot(h, col)
            d.checked = (p.avatarImage == null && p.avatarColor == col)
            d.setOnClickListener {
                p.avatarColor = col
                if (p.avatarImage != null) { Store.deleteMedia(p, p.avatarImage); p.avatarImage = null }
                for (o in dots) o.checked = (o.dotColor == col)
                Store.save()
                h.onProfileMeta()
            }
            dots.add(d)
            if (i < 5) rowA.addView(d) else rowB.addView(d)
        }
        page.add(rowA)
        page.add(rowB)
        page.add(PanelRow(h).set("Своя картинка…").click {
            h.pickMedia(p, Kind.IMAGE, "avatar") { name ->
                Store.deleteMedia(p, p.avatarImage)
                p.avatarImage = name
                for (o in dots) o.checked = false
                Store.save()
                h.onProfileMeta()
            }
        })
        page.add(PanelRow(h).set("Убрать картинку").click {
            Store.deleteMedia(p, p.avatarImage)
            p.avatarImage = null
            for (o in dots) o.checked = (o.dotColor == p.avatarColor)
            Store.save()
            h.onProfileMeta()
        })

        // фон
        page.section(h, "ФОН")
        val bgRow = PanelRow(h)
        fun bgName(): String = when (p.bgType) {
            BgType.IMAGE -> "Картинка"; BgType.GIF -> "GIF"; BgType.VIDEO -> "Видео"; else -> "Нет"
        }
        bgRow.set("Текущий фон", null, bgName())
        page.add(bgRow)
        fun setBg(kind: Kind, type: String) {
            h.pickMedia(p, kind, "bg") { name ->
                Store.deleteMedia(p, p.bgFile)
                p.bgFile = name
                p.bgType = type
                Store.save()
                bgRow.setTrail(bgName())
                h.applyBackground()
                h.applySound()
            }
        }
        page.add(PanelRow(h).set("Выбрать картинку…").click { setBg(Kind.IMAGE, BgType.IMAGE) })
        page.add(PanelRow(h).set("Выбрать GIF…").click { setBg(Kind.GIF, BgType.GIF) })
        page.add(PanelRow(h).set("Выбрать видео…").click { setBg(Kind.VIDEO, BgType.VIDEO) })
        page.add(PanelRow(h).set("Убрать фон").click {
            Store.deleteMedia(p, p.bgFile)
            p.bgFile = null
            p.bgType = BgType.NONE
            Store.save()
            bgRow.setTrail(bgName())
            h.applyBackground()
            h.applySound()
        })

        // звук
        page.section(h, "ЗВУК")
        val snd = SwitchRow(h)
        snd.set("Звук включён")
        snd.checked = p.soundEnabled
        snd.onChanged = { v -> p.soundEnabled = v; Store.save(); h.applySound() }
        page.add(snd)

        val vol = SeekRow(h)
        vol.set("Громкость")
        vol.value = p.volume
        vol.onChanged = { v -> p.volume = v; Store.save(); SoundPlayer.setVolume(p) }
        page.add(vol)

        val srcNone = PanelRow(h)
        val srcVideo = PanelRow(h)
        val srcFile = PanelRow(h)
        fun refreshSrc() {
            srcNone.set("Без звука", null, if (p.soundSource == SoundSource.NONE) "✓" else null)
            srcVideo.set("Звук фонового видео", if (p.bgType == BgType.VIDEO) null else "Сначала выберите видео в качестве фона",
                if (p.soundSource == SoundSource.VIDEO) "✓" else null)
            srcFile.set("Свой аудиофайл", p.soundFile?.let { "Файл выбран" } ?: "Файл не выбран",
                if (p.soundSource == SoundSource.FILE) "✓" else null)
        }
        refreshSrc()
        srcNone.click { p.soundSource = SoundSource.NONE; Store.save(); refreshSrc(); h.applySound() }
        srcVideo.click {
            if (p.bgType == BgType.VIDEO) {
                p.soundSource = SoundSource.VIDEO; Store.save(); refreshSrc(); h.applySound()
            }
        }
        srcFile.click {
            if (p.soundFile != null) {
                p.soundSource = SoundSource.FILE; Store.save(); refreshSrc(); h.applySound()
            } else {
                h.pickMedia(p, Kind.AUDIO, "sound") { name ->
                    p.soundFile = name; p.soundSource = SoundSource.FILE
                    Store.save(); refreshSrc(); h.applySound()
                }
            }
        }
        page.add(srcNone)
        page.add(srcVideo)
        page.add(srcFile)
        page.add(PanelRow(h).set("Выбрать другой аудиофайл…").click {
            h.pickMedia(p, Kind.AUDIO, "sound") { name ->
                Store.deleteMedia(p, p.soundFile)
                p.soundFile = name; p.soundSource = SoundSource.FILE
                Store.save(); refreshSrc(); h.applySound()
            }
        })
        val everywhere = SwitchRow(h)
        everywhere.set("Играть звук в других приложениях")
        everywhere.checked = p.soundEverywhere
        everywhere.onChanged = { v -> p.soundEverywhere = v; Store.save(); h.applySound() }
        page.add(everywhere)

        // значки
        page.section(h, "ЗНАЧКИ ПРИЛОЖЕНИЙ")
        val alpha = SeekRow(h)
        alpha.set("Прозрачность значков")
        alpha.minValue = 20
        alpha.value = p.iconAlpha
        alpha.onChanged = { v -> p.iconAlpha = v; Store.save(); h.applyIcons() }
        page.add(alpha)
        page.add(PanelRow(h).set("Приложения", "Показ, скрытие и свои значки").click {
            h.panel.push(apps(h, p))
        })
        val hideSelf = SwitchRow(h)
        hideSelf.set("Скрыть значок лаунчера", "В списке приложений")
        hideSelf.checked = p.hideSelf
        hideSelf.onChanged = { v -> p.hideSelf = v; Store.save(); h.refreshLists() }
        page.add(hideSelf)

        // удаление
        if (Store.profiles.size > 1) {
            val del = PanelRow(h)
            var armed = false
            del.set("Удалить профиль")
            del.click {
                if (!armed) {
                    armed = true
                    del.set("Нажмите ещё раз для удаления")
                } else {
                    Store.delete(p)
                    h.switchProfile(Store.current)
                    h.panel.popTo(1)
                    h.panel.replaceCurrent(profiles(h))
                }
            }
            page.add(del)
        }
        return page
    }

    // ---------- Приложения профиля ----------
    fun apps(h: HomeActivity, p: Profile): Page {
        val page = Page(h, "Приложения")
        for (app in Store.sortApps(p, h.allApps)) {
            val row = PanelRow(h)
            val iv = ImageView(h)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(0xFF2B3036.toInt())
            row.setLeadingSized(iv, 55, 31)
            thumb(h, app, iv)
            fun label() = row.set(app.label, null, if (p.hidden.contains(app.key)) "Скрыто" else "Показано")
            label()
            row.click { h.panel.push(appOptions(h, p, app)) }
            page.add(row)
        }
        return page
    }

    fun appOptions(h: HomeActivity, p: Profile, app: AppEntry): Page {
        val page = Page(h, app.label)
        val show = SwitchRow(h)
        show.set("Показывать в лаунчере")
        show.checked = !p.hidden.contains(app.key)
        show.onChanged = { v ->
            if (v) p.hidden.remove(app.key) else p.hidden.add(app.key)
            Store.save()
            h.refreshLists()
        }
        page.add(show)
        page.add(PanelRow(h).set("Своя картинка значка…", "Лучше формат 16:9").click {
            h.pickMedia(p, Kind.IMAGE, "icon") { name ->
                Store.deleteMedia(p, p.customIcons[app.key])
                p.customIcons[app.key] = name
                Store.save()
                h.refreshLists()
            }
        })
        page.add(PanelRow(h).set("Сбросить значок").click {
            Store.deleteMedia(p, p.customIcons[app.key])
            p.customIcons.remove(app.key)
            Store.save()
            h.refreshLists()
        })
        return page
    }
}
