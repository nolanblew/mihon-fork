package eu.kanade.domain.track.store

import android.content.Context

class TwoWayTrackingStore(context: Context) {

    private val preferences = context.getSharedPreferences("two_way_tracking_auto", Context.MODE_PRIVATE)

    fun shouldSync(mangaId: Long, intervalMillis: Long, now: Long = System.currentTimeMillis()): Boolean {
        val lastSync = preferences.getLong(mangaId.toString(), 0L)
        return now - lastSync >= intervalMillis
    }

    fun setLastSync(mangaId: Long, timestamp: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(mangaId.toString(), timestamp).apply()
    }
}
