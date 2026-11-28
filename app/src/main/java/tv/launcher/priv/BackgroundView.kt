package tv.launcher.priv

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File

/** Фон профиля: картинка, GIF или видео (всегда без звука — звук ведёт SoundPlayer). */
class BackgroundView(ctx: Context) : FrameLayout(ctx) {
    private val image = ImageView(ctx)
    private val scrim = View(ctx)
    private var texture: TextureView? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private val main = Handler(Looper.getMainLooper())

    private var curType = BgType.NONE
    private var curPath: String? = null
    private var token = 0
    private var resumed = false
    private var vw = 0
    private var vh = 0

    init {
        setBackgroundColor(0xFF000000.toInt())
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(image, LayoutParams(-1, -1))
        scrim.setBackgroundColor(0x59000000)
        scrim.visibility = View.GONE
        addView(scrim, LayoutParams(-1, -1))
    }

    fun set(p: Profile) {
        val file: File? = Store.mediaFile(p, p.bgFile)
        val type = if (file == null) BgType.NONE else p.bgType
        val path = file?.absolutePath
        if (type == curType && path == curPath) return
        clear()
        curType = type
        curPath = path
        token++
        val my = token
        scrim.visibility = if (type == BgType.NONE) View.GONE else View.VISIBLE
        if (file == null) return
        when (type) {
            BgType.IMAGE -> Thread {
                val b = Images.decodeFile(file, 1920, 1080)
                main.post { if (my == token) image.setImageBitmap(b) }
            }.start()
            BgType.GIF -> loadGif(file, my)
            BgType.VIDEO -> setupVideo(file)
        }
    }

    private fun loadGif(file: File, my: Int) {
        if (Build.VERSION.SDK_INT >= 28) {
            Thread {
                var d: Drawable? = null
                try {
                    val src = android.graphics.ImageDecoder.createSource(file)
                    d = android.graphics.ImageDecoder.decodeDrawable(src)
                } catch (e: Throwable) { d = null }
                val res: Drawable? = d
                main.post {
                    if (my == token && res != null) {
                        image.setImageDrawable(res)
                        if (Build.VERSION.SDK_INT >= 28 && res is AnimatedImageDrawable) {
                            res.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            if (resumed) res.start()
                        }
                    }
                }
            }.start()
        } else {
            Thread {
                val b = Images.decodeFile(file, 1920, 1080)
                main.post { if (my == token) image.setImageBitmap(b) }
            }.start()
        }
    }

    private fun setupVideo(file: File) {
        val tv = TextureView(context)
        texture = tv
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surface = Surface(st)
                if (resumed) startVideo()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { fit() }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                stopVideo()
                surface?.release()
                surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        addView(tv, 1, LayoutParams(-1, -1))
    }

    private fun startVideo() {
        val path = curPath ?: return
        val s = surface ?: return
        if (curType != BgType.VIDEO || player != null) return
        try {
            val m = MediaPlayer()
            m.setSurface(s)
            m.setDataSource(path)
            m.isLooping = true
            m.setVolume(0f, 0f)
            m.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; fit() }
            m.setOnPreparedListener { it.start() }
            m.setOnErrorListener { _, _, _ -> stopVideo(); true }
            m.prepareAsync()
            player = m
        } catch (e: Exception) {
            stopVideo()
        }
    }

    private fun stopVideo() {
        val m = player ?: return
        player = null
        try { m.stop() } catch (e: Exception) { }
        try { m.release() } catch (e: Exception) { }
    }

    /** Центрирование с обрезкой (как center-crop). */
    private fun fit() {
        val tv = texture ?: return
        if (vw <= 0 || vh <= 0 || tv.width <= 0 || tv.height <= 0) return
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        val scale = maxOf(viewW / vw, viewH / vh)
        val m = Matrix()
        m.setScale(scale * vw / viewW, scale * vh / viewH, viewW / 2f, viewH / 2f)
        tv.setTransform(m)
    }

    private fun clear() {
        stopVideo()
        texture?.let { removeView(it) }
        texture = null
        surface?.release()
        surface = null
        animStop()
        image.setImageDrawable(null)
        vw = 0; vh = 0
    }

    fun resume() {
        resumed = true
        animStart()
        if (curType == BgType.VIDEO && surface != null) startVideo()
    }

    fun pause() {
        resumed = false
        animStop()
        stopVideo()
    }

    private fun animStart() {
        if (Build.VERSION.SDK_INT >= 28) {
            val d = image.drawable
            if (d is AnimatedImageDrawable) d.start()
        }
    }

    private fun animStop() {
        if (Build.VERSION.SDK_INT >= 28) {
            val d = image.drawable
            if (d is AnimatedImageDrawable) d.stop()
        }
    }
}
