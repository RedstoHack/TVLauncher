package tv.launcher.priv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Список приложений. Кроме приложений для ТВ (LEANBACK_LAUNCHER) добавляем и обычные
 * (LAUNCHER) — те, что "не адаптированы" под ТВ и в других лаунчерах не показываются.
 */
object AppRepo {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val map = LinkedHashMap<String, AppEntry>()
        query(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, map)
        query(ctx, pm, Intent.CATEGORY_LAUNCHER, false, map)
        return ArrayList(map.values)
    }

    private fun query(
        ctx: Context, pm: PackageManager, category: String, leanback: Boolean,
        out: MutableMap<String, AppEntry>
    ) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val list = try { pm.queryIntentActivities(intent, 0) } catch (e: Exception) { emptyList() }
        for (ri in list) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (out.containsKey(pkg)) continue
            val self = pkg == ctx.packageName
            val comp = ComponentName(pkg, ai.name)
            val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { pkg }
            out[pkg] = AppEntry(pkg, label, comp, leanback, self)
        }
    }

    fun launch(ctx: Context, app: AppEntry): Boolean {
        return try {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            i.component = app.component
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
