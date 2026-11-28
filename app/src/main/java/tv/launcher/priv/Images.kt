package tv.launcher.priv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/** Загрузка карточек приложений (баннер, либо иконка на цветном фоне, либо своя картинка). */
object Images {
    const val CARD_W = 320
    const val CARD_H = 180

    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clear() { cache.evictAll() }

    fun cardKey(app: AppEntry, custom: File?): String =
        app.key + "|" + (custom?.name ?: "") + "|" + (custom?.length() ?: 0L)

    fun cached(app: AppEntry, custom: File?): Bitmap? = cache.get(cardKey(app, custom))

    fun loadCard(ctx: Context, app: AppEntry, custom: File?, done: (Bitmap?) -> Unit) {
        val key = cardKey(app, custom)
        val hit = cache.get(key)
        if (hit != null) { done(hit); return }
        val appCtx = ctx.applicationContext
        pool.execute {
            var bmp: Bitmap? = null
            try {
                bmp = if (custom != null) decodeFile(custom, 640, 360) else null
                if (bmp == null) bmp = buildCard(appCtx, app)
            } catch (e: Throwable) {
                bmp = null
            }
            if (bmp != null) cache.put(key, bmp)
            main.post { done(bmp) }
        }
    }

    private fun buildCard(ctx: Context, app: AppEntry): Bitmap? {
        val pm = ctx.packageManager
        // 1. баннер приложения
        val banner: Drawable? = try { pm.getActivityBanner(app.component) } catch (e: Exception) { null }
            ?: try { pm.getApplicationBanner(app.key) } catch (e: Exception) { null }
        if (banner != null) return drawableToBitmap(banner, CARD_W, CARD_H)
        // 2. иконка на цветной плашке с названием
        val icon: Drawable = try { pm.getActivityIcon(app.component) } catch (e: Exception) { return null }
        val iconBmp = drawableToBitmap(icon, 128, 128) ?: return null
        val out = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(dominantDark(iconBmp))
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(iconBmp, null, android.graphics.Rect(116, 20, 204, 108), p)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.WHITE
        tp.textSize = 24f
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
        val text = TextUtils.ellipsize(app.label, tp, CARD_W - 24f, TextUtils.TruncateAt.END).toString()
        c.drawText(text, CARD_W / 2f, 148f, tp)
        return out
    }

    private fun dominantDark(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 8, 8, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (x in 0 until 8) for (y in 0 until 8) {
            val px = s.getPixel(x, y)
            if (Color.alpha(px) < 128) continue
            r += Color.red(px); g += Color.green(px); bl += Color.blue(px); n++
        }
        if (n == 0) return 0xFF2B3036.toInt()
        val f = 0.45f
        return Color.rgb((r / n * f).toInt() + 18, (g / n * f).toInt() + 20, (bl / n * f).toInt() + 24)
    }

    fun drawableToBitmap(d: Drawable, w: Int, h: Int): Bitmap? {
        return try {
            val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else w
            val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else h
            val scale = minOf(w.toFloat() / iw, h.toFloat() / ih, 1f).let { if (it <= 0f) 1f else it }
            val bw = maxOf(1, (iw * scale).toInt())
            val bh = maxOf(1, (ih * scale).toInt())
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, bw, bh)
            d.draw(c)
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    fun decodeFile(f: File, maxW: Int, maxH: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, o)
            var s = 1
            while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= maxH) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            BitmapFactory.decodeFile(f.absolutePath, o2)
        } catch (e: Throwable) {
            null
        }
    }
}
