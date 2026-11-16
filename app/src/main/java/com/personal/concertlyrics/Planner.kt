package com.personal.concertlyrics

import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure decision logic (no network), so it can be unit tested. */
object Planner {

    /** How many recent shows the predicted setlist is based on. */
    const val SAMPLE_SIZE = 6

    /** Shows this long ago still count as "the tour that came to your area". */
    const val RECENT_DAYS = 180L

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    private val cityAliases = listOf(
        setOf("bengaluru", "bangalore"),
        setOf("mumbai", "bombay"),
        setOf("gurugram", "gurgaon"),
        setOf("kolkata", "calcutta"),
        setOf("chennai", "madras"),
        setOf("delhi"),
        setOf("pune", "poona"),
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetter() }

    fun cityMatches(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y || x.contains(y) || y.contains(x)) return true
        return cityAliases.any { group -> group.any { x.contains(it) } && group.any { y.contains(it) } }
    }

    /**
     * Picks the show that is "yours": the nearest upcoming show, otherwise the nearest show
     * from the last [RECENT_DAYS] days. Returns null when nothing qualifies.
     */
    fun pickLocalShow(
        shows: List<Show>,
        today: LocalDate,
        userLat: Double?,
        userLon: Double?,
        userCity: String,
    ): Show? {
        fun closeness(s: Show): Double = when {
            userLat != null && userLon != null && s.lat != null && s.lon != null ->
                distanceKm(userLat, userLon, s.lat, s.lon)
            cityMatches(s.city, userCity) -> 0.0
            else -> 5000.0
        }

        val upcoming = shows.filter { !it.date.isBefore(today) }
        upcoming.minWithOrNull(compareBy<Show>({ closeness(it) }, { it.date }))?.let { return it }

        val recent = shows.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(RECENT_DAYS)) }
        return recent.minWithOrNull(compareBy<Show>({ closeness(it) }, { -it.date.toEpochDay() }))
    }

    /** Past shows that have a real setlist, newest first. */
    fun recentWithSongs(shows: List<Show>, today: LocalDate, limit: Int = SAMPLE_SIZE): List<Show> =
        shows.filter { !it.date.isAfter(today) && it.liveSongs.size >= 3 }
            .distinctBy { it.id }
            .sortedByDescending { it.date }
            .take(limit)

    private val spaces = Regex("\\s+")

    private fun key(name: String) = name.lowercase().trim().replace(spaces, " ")

    /**
     * Builds the predicted setlist.
     * The order comes from the newest show with a (nearly) full setlist; each song is labelled
     * with how many of the sampled shows it was played at. Songs played at other shows but not
     * that one go at the end as "sometimes played".
     */
    fun buildSetlist(sample: List<Show>, artistName: String): List<PlannedSong> {
        if (sample.isEmpty()) return emptyList()
        val n = sample.size
        val maxCount = sample.maxOf { it.liveSongs.size }
        val threshold = ceil(maxCount * 0.8).toInt()
        val base = sample.first { it.liveSongs.size >= threshold }

        val freq = HashMap<String, Int>()
        for (show in sample) {
            for (k in show.liveSongs.map { key(it.name) }.toSet()) freq[k] = (freq[k] ?: 0) + 1
        }

        // Notes like "on B-stage" repeat every night; one-off trivia ("birthday flowers") doesn't.
        // Keep a note only when it was written for at least half the shows the song was played at.
        val notes = HashMap<String, MutableMap<String, Int>>()
        for (show in sample) {
            for (e in show.liveSongs) {
                val info = e.info ?: continue
                val m = notes.getOrPut(key(e.name)) { HashMap() }
                m[info] = (m[info] ?: 0) + 1
            }
        }
        fun commonNote(name: String): String? {
            val played = freq[key(name)] ?: return null
            val best = notes[key(name)]?.maxByOrNull { it.value } ?: return null
            return best.key.takeIf { played == 1 && n == 1 || best.value >= 2 && best.value * 2 >= played }
        }

        fun planned(e: SongEntry, sometimes: Boolean) = PlannedSong(
            title = e.name,
            lyricsArtist = e.coverArtist ?: artistName,
            coverOf = e.coverArtist,
            playedAt = freq[key(e.name)] ?: 1,
            outOf = n,
            sometimes = sometimes,
            note = commonNote(e.name),
        )

        val main = base.liveSongs.distinctBy { key(it.name) }.map { planned(it, false) }
        val baseKeys = main.map { key(it.title) }.toSet()
        val extras = sample.flatMap { it.liveSongs }
            .filter { key(it.name) !in baseKeys }
            .distinctBy { key(it.name) }
            .map { planned(it, true) }
            .sortedByDescending { it.playedAt }
        return main + extras
    }
}
