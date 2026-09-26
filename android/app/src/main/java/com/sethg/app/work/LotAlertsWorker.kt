package com.sethg.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sethg.app.MainActivity
import com.sethg.app.R
import com.sethg.app.data.local.AppPreferences
import com.sethg.app.data.local.SecureTokenStore
import com.sethg.app.data.local.db.UserDao
import com.sethg.app.data.repository.LotRepository
import com.sethg.app.data.repository.RecyclerRepository
import com.sethg.app.domain.model.Result as AppResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 min (Android's minimum) when online, and right after a lot is saved.
 *  • collector: uploads offline lots, alerts on new offers and on pickup slots
 *  • recycler:  alerts on new lots within 3–5 km
 */
class LotAlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun lotRepository(): LotRepository
        fun recyclerRepository(): RecyclerRepository
        fun preferences(): AppPreferences
        fun userDao(): UserDao
        fun tokenStore(): SecureTokenStore
    }

    companion object {
        private const val PERIODIC = "lot_alerts"
        private const val NOW = "lot_alerts_now"
        private const val CHANNEL = "lots"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LotAlertsWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            )
        }

        /** Sync straight away (e.g. a lot was just saved); waits for network if offline. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LotAlertsWorker>().setConstraints(online).build()
            )
        }
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        if (deps.tokenStore().accessToken == null) return Result.success()

        when (deps.userDao().getUser()?.role) {
            "recycler" -> recyclerAlerts(deps)
            "user", null -> collectorAlerts(deps)
        }
        return Result.success()
    }

    private suspend fun collectorAlerts(deps: Deps) {
        deps.lotRepository().syncPending()
        val lots = (deps.lotRepository().refreshFromServer() as? AppResult.Success)?.data ?: return
        val prefs = deps.preferences()

        val offers = lots.flatMap { lot -> lot.offers.filter { it.status == "PENDING" }.map { lot to it } }
        val newOffers = prefs.takeUnnotified(offers.map { "offer:${it.second.id}" }).toSet()
        offers.filter { "offer:${it.second.id}" in newOffers }.forEach { (lot, offer) ->
            notify(
                "offer:${offer.id}".hashCode(),
                applicationContext.getString(R.string.alert_new_offer_title, offer.recyclerName),
                applicationContext.getString(
                    R.string.alert_new_offer_body, offer.ratePerKg.toInt(), offer.offerTotal.toInt(), lot.id
                )
            )
        }

        val scheduled = lots.filter { it.status == "SCHEDULED" && it.slotStart != null }
        val newSlots = prefs.takeUnnotified(scheduled.map { "slot:${it.id}" }).toSet()
        scheduled.filter { "slot:${it.id}" in newSlots }.forEach { lot ->
            notify(
                "slot:${lot.id}".hashCode(),
                applicationContext.getString(R.string.alert_pickup_title),
                applicationContext.getString(R.string.alert_pickup_body, lot.id, formatSlot(lot.slotStart, lot.slotEnd))
            )
        }
    }

    private suspend fun recyclerAlerts(deps: Deps) {
        val prefs = deps.preferences()
        val since = prefs.nearbySince()
        val response = (deps.recyclerRepository().nearbyLots(since) as? AppResult.Success)?.data ?: return
        prefs.setNearbySince(response.serverTime)
        if (since == null) return                       // first run: start from now, don't replay history

        val fresh = prefs.takeUnnotified(response.lots.map { "lot:${it.id}" }).toSet()
        response.lots.filter { "lot:${it.id}" in fresh }.forEach { lot ->
            notify(
                "lot:${lot.id}".hashCode(),
                applicationContext.getString(R.string.alert_new_lot_title, lot.distanceKm),
                applicationContext.getString(
                    R.string.alert_new_lot_body, lot.category, lot.weightKg, lot.estimateLow, lot.estimateHigh,
                    lot.collectorFirstName
                )
            )
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}

/** "Thu 27 Sep, 10:00–12:00" in the phone's time zone (server sends UTC ISO-8601). */
fun formatSlot(start: String?, end: String?): String {
    val utc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val s = start?.let { runCatching { utc.parse(it) }.getOrNull() } ?: return ""
    val e = end?.let { runCatching { utc.parse(it) }.getOrNull() }
    val day = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault())
    val hour = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return day.format(s) + (e?.let { "–" + hour.format(it) } ?: "")
}
