package io.github.hdlee73.insu

import android.app.Application
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger

class InsuApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 가장 먼저 설치해야 이후 초기화 중의 크래시도 기록된다.
        CrashLogger.install(this)
        container = AppContainer(this)
    }
}
