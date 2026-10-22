package com.screenlate.app

import android.app.Application
import android.content.Context
import com.screenlate.app.cache.AppDatabase
import com.screenlate.app.cache.TranslationRepository
import com.screenlate.app.model.ModelManager
import com.screenlate.app.translate.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScreenLateApplication : Application() {
    val services by lazy { AppServices(this) }
}

/** Process-wide objects shared by the accessibility service, the tile and the UI. */
class AppServices(context: Context) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database: AppDatabase = AppDatabase.create(context)
    val engine: TranslationEngine = TranslationEngine(modelDir = { modelManager.readyDir() })
    val repository: TranslationRepository = TranslationRepository(database.translations(), engine)

    // A new or removed model invalidates the loaded session and every cached translation.
    val modelManager: ModelManager = ModelManager(context, scope) {
        engine.unload()
        repository.clear()
    }
}

val Context.services: AppServices
    get() = (applicationContext as ScreenLateApplication).services
