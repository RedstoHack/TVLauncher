package tv.launcher.priv

import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject

/** Одно приложение в списке. key = имя пакета. */
class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val leanback: Boolean,
    val isSelf: Boolean
)

object BgType {
    const val NONE = "none"
    const val IMAGE = "image"
    const val GIF = "gif"
    const val VIDEO = "video"
}

object SoundSource {
    const val NONE = "none"
    const val VIDEO = "video"   // звук фонового видео
    const val FILE = "file"     // отдельный аудиофайл
}

class Profile(val id: String) {
    var name: String = "Профиль"
    var avatarColor: Int = 0xFF5F6A72.toInt()
    var avatarImage: String? = null

    var bgType: String = BgType.NONE
    var bgFile: String? = null

    var soundSource: String = SoundSource.NONE
    var soundFile: String? = null
    var soundEnabled: Boolean = true
    var volume: Int = 70
    var soundEverywhere: Boolean = false

    var iconAlpha: Int = 100
    var hideSelf: Boolean = false

    val order = ArrayList<String>()
    val favorites = ArrayList<String>()
    val hidden = HashSet<String>()
    val customIcons = HashMap<String, String>()

    fun letter(): String {
        val t = name.trim()
        return if (t.isEmpty()) "?" else t.substring(0, t.offsetByCodePoints(0, 1)).uppercase()
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("avatarColor", avatarColor)
        o.put("avatarImage", avatarImage ?: JSONObject.NULL)
        o.put("bgType", bgType)
        o.put("bgFile", bgFile ?: JSONObject.NULL)
        o.put("soundSource", soundSource)
        o.put("soundFile", soundFile ?: JSONObject.NULL)
        o.put("soundEnabled", soundEnabled)
        o.put("volume", volume)
        o.put("soundEverywhere", soundEverywhere)
        o.put("iconAlpha", iconAlpha)
        o.put("hideSelf", hideSelf)
        o.put("order", JSONArray(order))
        o.put("favorites", JSONArray(favorites))
        o.put("hidden", JSONArray(hidden.toList()))
        val ci = JSONObject()
        for ((k, v) in customIcons) ci.put(k, v)
        o.put("customIcons", ci)
        return o
    }

    companion object {
        private fun JSONObject.str(k: String): String? =
            if (has(k) && !isNull(k)) getString(k) else null

        private fun JSONArray?.strings(): List<String> {
            val r = ArrayList<String>()
            if (this == null) return r
            for (i in 0 until length()) r.add(getString(i))
            return r
        }

        fun fromJson(o: JSONObject): Profile {
            val p = Profile(o.getString("id"))
            p.name = o.optString("name", "Профиль")
            p.avatarColor = o.optInt("avatarColor", p.avatarColor)
            p.avatarImage = o.str("avatarImage")
            p.bgType = o.optString("bgType", BgType.NONE)
            p.bgFile = o.str("bgFile")
            p.soundSource = o.optString("soundSource", SoundSource.NONE)
            p.soundFile = o.str("soundFile")
            p.soundEnabled = o.optBoolean("soundEnabled", true)
            p.volume = o.optInt("volume", 70)
            p.soundEverywhere = o.optBoolean("soundEverywhere", false)
            p.iconAlpha = o.optInt("iconAlpha", 100)
            p.hideSelf = o.optBoolean("hideSelf", false)
            p.order.addAll(o.optJSONArray("order").strings())
            p.favorites.addAll(o.optJSONArray("favorites").strings())
            p.hidden.addAll(o.optJSONArray("hidden").strings())
            val ci = o.optJSONObject("customIcons")
            if (ci != null) {
                val it = ci.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    p.customIcons[k] = ci.getString(k)
                }
            }
            return p
        }
    }
}
