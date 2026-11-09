package com.swordfish.lemuroid.app.shared.metadata

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Plex-like metadata enrichment after the ROM importer finishes.
 * Online requests send normalized game titles, never ROM bytes or save files.
 */
object NesAutoMetadataScheduler {
    private const val PREFS = "opennes_metadata"
    private const val AUTO_ENABLED = "automatic_online_details"
    private const val WORK_ID = "opennes_nes_metadata_enrichment"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_ENABLED, true)

    fun setEnabled(
        context: Context,
        enable: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_ENABLED, enable).apply()
        if (enable) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_ID)
    }

    fun schedule(context: Context) {
        if (!enabled(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_ID,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(),
        )
    }

    private fun request() =
        OneTimeWorkRequestBuilder<NesAutoMetadataWork>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
}
