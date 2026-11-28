package tv.launcher.priv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

/** Звук профиля: отдельный аудиофайл или звук фонового видео. Один плеер на весь процесс. */
object SoundPlayer {
    private var mp: MediaPlayer? = null
    private var curPath: String? = null

    fun pathFor(p: Profile): String? {
        if (!p.soundEnabled) return null
        return when (p.soundSource) {
            SoundSource.FILE -> Store.mediaFile(p, p.soundFile)?.absolutePath
            SoundSource.VIDEO -> if (p.bgType == BgType.VIDEO) Store.mediaFile(p, p.bgFile)?.absolutePath else null
            else -> null
        }
    }

    /** play=false — остановить (например, лаунчер ушёл на второй план без «играть везде»). */
    fun apply(p: Profile, play: Boolean) {
        val path = if (play) pathFor(p) else null
        if (path == null) { release(); return }
        if (path != curPath || mp == null) {
            release()
            try {
                val m = MediaPlayer()
                m.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                m.setDataSource(path)
                m.isLooping = true
                m.prepare()
                mp = m
                curPath = path
            } catch (e: Exception) {
                release()
                return
            }
        }
        setVolume(p)
        try { if (mp?.isPlaying == false) mp?.start() } catch (e: Exception) { }
    }

    fun setVolume(p: Profile) {
        val v = (p.volume.coerceIn(0, 100)) / 100f
        try { mp?.setVolume(v, v) } catch (e: Exception) { }
    }

    fun release() {
        try { mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        curPath = null
    }
}

/** Служба переднего плана: не даёт системе убить процесс, пока звук играет в других приложениях. */
class SoundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("sound", "Звук профиля", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "sound") else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_customize)
        b.setContentTitle("TvLauncher")
        b.setContentText("Звучит фон профиля «" + Store.current.name + "»")
        b.setOngoing(true)
        val n = b.build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(7, n)
            }
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, SoundService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, SoundService::class.java)) } catch (e: Exception) { }
        }
    }
}
