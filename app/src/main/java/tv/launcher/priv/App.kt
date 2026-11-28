package tv.launcher.priv

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
