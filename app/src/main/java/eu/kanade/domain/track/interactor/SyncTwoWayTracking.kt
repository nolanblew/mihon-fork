package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TwoWayTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import eu.kanade.domain.chapter.interactor.SetReadStatus
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import kotlin.math.abs

class SyncTwoWayTracking(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val setReadStatus: SetReadStatus,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackPreferences: TrackPreferences,
) {

    /**
     * Syncs read chapters from supported trackers into the app.
     *
     * @return Failed updates.
     */
    suspend fun await(mangaId: Long, trackerId: Long? = null): List<Pair<Tracker?, Throwable>> {
        if (!trackPreferences.twoWayTrackingEnabled().get()) return emptyList()

        return supervisorScope {
            getTracks.await(mangaId)
                .filter { trackerId == null || it.trackerId == trackerId }
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true && service is TwoWayTracker }
                .map { (track, service) ->
                    async {
                        return@async try {
                            val refreshedTrack = service!!.refresh(track.toDbTrack()).toDomainTrack()!!
                            insertTrack.await(refreshedTrack)
                            syncLocalChapters(mangaId, refreshedTrack.lastChapterRead.toInt())
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    suspend fun syncLocalChapters(mangaId: Long, remoteLastRead: Int) {
        if (remoteLastRead <= 0) return

        val maxRead = remoteLastRead.toDouble()
        val chaptersToMark = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isTwoWaySyncCandidate() }
            .filter { chapter -> chapter.chapterNumber <= maxRead && !chapter.read }
            .toTypedArray()

        if (chaptersToMark.isNotEmpty()) {
            setReadStatus.await(read = true, chapters = chaptersToMark)
        }
    }

    private fun Chapter.isTwoWaySyncCandidate(): Boolean {
        if (!isRecognizedNumber) return false
        if (chapterNumber < 1.0) return false
        if (!chapterNumber.isWholeNumber()) return false

        val lowerName = name.lowercase()
        if (excludedKeywords.any { it in lowerName }) return false

        return chapterTokenPattern.containsMatchIn(lowerName) || leadingNumberPattern.matches(lowerName)
    }

    private fun Double.isWholeNumber(): Boolean {
        return abs(this - toInt().toDouble()) < 0.0001
    }

    private companion object {
        val excludedKeywords = listOf(
            "special",
            "extra",
            "omake",
            "one-shot",
            "oneshot",
            "pilot",
            "prologue",
            "epilogue",
        )

        val chapterTokenPattern = Regex("""\bch(?:apter|ap)?\.?\s*\d""")
        val leadingNumberPattern = Regex("""^\s*\d+\s*(?:[\p{Pd}:.\-]\s*.*)?$""")
    }
}
