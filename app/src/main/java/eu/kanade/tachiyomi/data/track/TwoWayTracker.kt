package eu.kanade.tachiyomi.data.track

/**
 * Marker interface for trackers that support syncing progress from tracker to app.
 */
interface TwoWayTracker {

    /**
     * Minimum time between automatic two-way syncs for a given manga.
     */
    val twoWaySyncIntervalMillis: Long
        get() = DEFAULT_AUTO_SYNC_INTERVAL_MS

    companion object {
        const val SIMPLE_AUTO_SYNC_INTERVAL_MS = 60_000L
        const val DEFAULT_AUTO_SYNC_INTERVAL_MS = 5 * 60_000L
    }
}
