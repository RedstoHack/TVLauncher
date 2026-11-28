package tv.launcher.priv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import java.io.File

enum class Kind(val title: String, val mime: String, val exts: Set<String>) {
    IMAGE("Картинка", "image/*", setOf("jpg", "jpeg", "png", "webp", "bmp")),
    GIF("GIF-анимация", "image/gif", setOf("gif")),
    VIDEO("Видео", "video/*", setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")),
    AUDIO("Звук", "audio/*", setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus"))
}

/** Результат выбора: как открыть файл и расширение. */
class Picked(val ext: String, val file: File?, val uri: Uri?)

/**
 * Выбор файла. Сначала системный выбор (SAF); если на ТВ его нет — встроенный
 * файловый менеджер с управлением пультом.
 */
object FilePicker {
    const val REQ_PICK = 4711
    private var pending: ((Picked) -> Unit)? = null
    private var pendingKind: Kind = Kind.IMAGE

    fun pick(host: HomeActivity, kind: Kind, cb: (Picked) -> Unit) {
        pending = cb
        pendingKind = kind
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = kind.mime
            host.startActivityForResult(i, REQ_PICK)
        } catch (e: Exception) {
            browse(host, kind)
        }
    }

    fun onResult(host: HomeActivity, code: Int, ok: Boolean, data: Intent?) {
        if (code != REQ_PICK) return
        val cb = pending
        val uri = data?.data
        if (ok && uri != null && cb != null) {
            var ext = uriExt(host, uri)
            if (ext.isEmpty()) ext = pendingKind.exts.first()
            cb(Picked(ext, null, uri))
        } else if (cb != null && !ok && data == null) {
            // системный выбор отменён — ничего не делаем
        }
    }

    private fun uriExt(host: HomeActivity, uri: Uri): String {
        var name: String? = null
        try {
            val c = host.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            c?.use { if (it.moveToFirst()) name = it.getString(0) }
        } catch (e: Exception) { }
        val n = name ?: uri.lastPathSegment ?: ""
        val dot = n.lastIndexOf('.')
        return if (dot >= 0) n.substring(dot + 1).lowercase() else ""
    }

    fun hasStorageAccess(host: HomeActivity): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else androidx.core.content.ContextCompat.checkSelfPermission(host, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStorageAccess(host: HomeActivity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.parse("package:" + host.packageName)
                host.startActivity(i)
            } catch (e: Exception) {
                try {
                    host.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    Toast.makeText(host, "Выдайте доступ к файлам в настройках приложения", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(host, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 4712)
        }
    }

    fun deliver(f: File, ext: String) {
        pending?.invoke(Picked(ext, f, null))
    }

    fun browse(host: HomeActivity, kind: Kind) {
        pendingKind = kind
        host.showFileBrowser(kind)
    }
}

/** Страница встроенного файлового менеджера. */
object FileBrowser {
    fun roots(): List<File> {
        val out = ArrayList<File>()
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) out.add(ext)
        try {
            val st = File("/storage")
            st.listFiles()?.forEach {
                if (it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead()) out.add(it)
            }
        } catch (e: Exception) { }
        return out
    }

    fun build(host: HomeActivity, kind: Kind, dir: File?, panel: SidePanel): Page {
        val ctx: android.content.Context = host
        val title = if (dir == null) "Выберите файл" else dir.name.ifEmpty { dir.absolutePath }
        val page = Page(ctx, title)

        if (!FilePicker.hasStorageAccess(host)) {
            page.add(PanelRow(ctx).set("Нет доступа к файлам", "Нажмите, чтобы выдать доступ, затем вернитесь сюда").click {
                FilePicker.requestStorageAccess(host)
            })
            page.add(PanelRow(ctx).set("Обновить").click {
                panel.replaceCurrent(build(host, kind, dir, panel))
            })
            return page
        }

        if (dir == null) {
            for (r in roots()) {
                val label = if (r == Environment.getExternalStorageDirectory()) "Память устройства" else "Накопитель " + r.name
                page.add(PanelRow(ctx).set(label, r.absolutePath).click {
                    panel.push(build(host, kind, r, panel))
                })
            }
            val ext = Environment.getExternalStorageDirectory()
            for (n in listOf("Download", "Movies", "Pictures", "Music", "DCIM")) {
                val d = File(ext, n)
                if (d.isDirectory) page.add(PanelRow(ctx).set(n, d.absolutePath).click {
                    panel.push(build(host, kind, d, panel))
                })
            }
            return page
        }

        val files = try { dir.listFiles() } catch (e: Exception) { null }
        if (files == null) {
            page.add(PanelRow(ctx).set("Папка недоступна"))
            return page
        }
        val dirs = files.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
        val matched = files.filter {
            it.isFile && kind.exts.contains(it.extension.lowercase())
        }.sortedBy { it.name.lowercase() }
        for (d in dirs) {
            page.add(PanelRow(ctx).set("📁 " + d.name).click {
                panel.push(build(host, kind, d, panel))
            })
        }
        for (f in matched) {
            val mb = f.length() / (1024 * 1024)
            page.add(PanelRow(ctx).set(f.name, null, "$mb МБ").click {
                host.closeFileBrowser()
                FilePicker.deliver(f, f.extension.lowercase())
            })
        }
        if (dirs.isEmpty() && matched.isEmpty()) page.add(PanelRow(ctx).set("Нет подходящих файлов"))
        return page
    }
}
