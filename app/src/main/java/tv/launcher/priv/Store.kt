package tv.launcher.priv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Хранилище профилей. Всё сохраняется автоматически, без кнопки «Сохранить». */
object Store {
    val AVATAR_COLORS = intArrayOf(
        0xFF5F6A72.toInt(), 0xFFE53935.toInt(), 0xFFF4511E.toInt(), 0xFFF9A825.toInt(),
        0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF1E88E5.toInt(), 0xFF5E35B1.toInt(),
        0xFFD81B60.toInt(), 0xFF6D4C41.toInt()
    )

    private lateinit var ctx: Context
    private lateinit var file: File
    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    val profiles = ArrayList<Profile>()
    var currentId: String = ""

    val current: Profile
        get() = profiles.firstOrNull { it.id == currentId } ?: profiles[0]

    fun init(c: Context) {
        ctx = c.applicationContext
        file = File(ctx.filesDir, "profiles.json")
        load()
        if (profiles.isEmpty()) {
            val p = Profile(UUID.randomUUID().toString())
            p.name = "Профиль 1"
            profiles.add(p)
            currentId = p.id
            saveNow()
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            currentId = o.optString("current", "")
            val arr = o.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until arr.length()) profiles.add(Profile.fromJson(arr.getJSONObject(i)))
            if (profiles.none { it.id == currentId } && profiles.isNotEmpty()) currentId = profiles[0].id
        } catch (e: Exception) {
            profiles.clear()
        }
    }

    /** Отложенное сохранение (чтобы не писать файл на каждый шаг ползунка). */
    fun save() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 250)
    }

    fun saveNow() {
        handler.removeCallbacks(saveRunnable)
        try {
            val o = JSONObject()
            o.put("current", currentId)
            val arr = JSONArray()
            for (p in profiles) arr.put(p.toJson())
            o.put("profiles", arr)
            val tmp = File(file.parentFile, "profiles.json.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(o.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun create(name: String): Profile {
        val p = Profile(UUID.randomUUID().toString())
        p.name = name
        p.avatarColor = AVATAR_COLORS[profiles.size % AVATAR_COLORS.size]
        profiles.add(p)
        save()
        return p
    }

    fun delete(p: Profile) {
        if (profiles.size <= 1) return
        profiles.remove(p)
        mediaDir(p).deleteRecursively()
        if (currentId == p.id) currentId = profiles[0].id
        save()
    }

    fun select(p: Profile) {
        currentId = p.id
        save()
    }

    fun mediaDir(p: Profile): File {
        val d = File(ctx.filesDir, "media/" + p.id)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun mediaFile(p: Profile, name: String?): File? {
        if (name == null) return null
        val f = File(mediaDir(p), name)
        return if (f.exists()) f else null
    }

    /** Копирует выбранный файл в личное хранилище профиля, возвращает имя файла. */
    fun copyIn(p: Profile, prefix: String, ext: String, input: InputStream): String? {
        return try {
            val name = prefix + "_" + System.currentTimeMillis() + "." + ext
            val out = File(mediaDir(p), name)
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun copyInUri(p: Profile, prefix: String, uri: Uri, ext: String): String? {
        val s = try { ctx.contentResolver.openInputStream(uri) } catch (e: Exception) { null } ?: return null
        return copyIn(p, prefix, ext, s)
    }

    fun deleteMedia(p: Profile, name: String?) {
        if (name == null) return
        try { File(mediaDir(p), name).delete() } catch (e: Exception) { }
    }

    /** Применяет сохранённый порядок профиля; новые приложения — в конец (по алфавиту). */
    fun sortApps(p: Profile, apps: List<AppEntry>): List<AppEntry> {
        val byKey = HashMap<String, AppEntry>()
        for (a in apps) byKey[a.key] = a
        val result = ArrayList<AppEntry>()
        val used = HashSet<String>()
        for (k in p.order) {
            val a = byKey[k]
            if (a != null && used.add(k)) result.add(a)
        }
        val rest = apps.filter { !used.contains(it.key) }
            .sortedBy { it.label.lowercase() }
        result.addAll(rest)
        return result
    }
}
