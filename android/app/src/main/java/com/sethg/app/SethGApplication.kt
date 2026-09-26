package com.sethg.app

import android.app.Application
import com.sethg.app.data.repository.LotRepository
import com.sethg.app.work.LotAlertsWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SethGApplication : Application() {

    @Inject lateinit var lotRepository: LotRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { lotRepository.deleteOrphanPhotos() } }
        LotAlertsWorker.schedule(this)
        LotAlertsWorker.runNow(this)
    }
}
