package io.github.namjh654.papy.app

import android.app.Application
import io.github.namjh654.papy.core.data.settings.AppSettingsRepository
import io.github.namjh654.papy.di.initAndroidKoin
import io.github.namjh654.papy.di.registerDocumentEngine
import io.github.namjh654.papy.engine.AndroidDocumentEngine
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class PapyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Activity 재생성(회전 등)과 무관하게 Koin 은 프로세스당 한 번만 시작한다.
        initAndroidKoin(context = this)
        // 문서 엔진: Rust(HWP·HWPX·DOCX) + PdfRenderer(PDF)
        registerDocumentEngine(AndroidDocumentEngine(this))

        // Android 12+ 시작 화면(스플래시 창)을 저장된 테마에 맞춘다 — 저장값이 없으면 시스템(AUTO)
        val settings = GlobalContext.get().get<AppSettingsRepository>()
        runBlocking { settings.ensureLoaded() }
        syncAppNightMode(settings.theme.value)
    }
}
