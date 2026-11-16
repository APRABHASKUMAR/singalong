package com.personal.concertlyrics

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Works out which tour you're seeing and predicts its setlist:
 * 1. find the artist on setlist.fm,
 * 2. if you typed a tour name, use that tour; otherwise find the artist's nearest upcoming
 *    (or recent) show in your country and take its tour name,
 * 3. collect the newest setlists from that tour,
 * 4. if there's no tour to go on (no location, no local show) or the tour has no setlists yet,
 *    use the artist's most recent shows instead.
 */
class Finder(private val api: SetlistFm, private val progress: (String) -> Unit) {

    private val pretty = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private fun loose(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun find(query: String, tourQuery: String?, loc: UserLocation?, today: LocalDate): Plan {
        val q = query.trim()
        progress("Looking up \"$q\" on setlist.fm…")
        val artists = api.searchArtists(q)
        if (artists.isEmpty()) throw UserError("setlist.fm doesn't know an artist called \"$q\". Check the spelling.")
        val artist = artists.firstOrNull { it.name.equals(q, ignoreCase = true) } ?: artists.first()

        val reason = mutableListOf<String>()
        var tourName: String? = null
        var tourShows: List<Show>? = null

        if (!tourQuery.isNullOrBlank()) {
            val (name, shows) = findTour(artist, tourQuery.trim())
            tourName = name
            tourShows = shows
            reason += "Using the tour you entered: \"$name\"."
        } else if (loc?.countryCode != null) {
            progress("Looking for ${artist.name} shows near ${loc.label}…")
            val countryShows = api.searchSetlists(artist.mbid, countryCode = loc.countryCode)
            val localShow = Planner.pickLocalShow(countryShows, today, loc.lat, loc.lon, loc.city)
            if (localShow == null) {
                reason += "No ${artist.name} show found near ${loc.label}."
            } else {
                val prefix = if (localShow.date.isBefore(today)) "Recent show near you" else "Your show"
                reason += "$prefix: ${localShow.venue}, ${localShow.city} · ${localShow.date.format(pretty)}."
                tourName = localShow.tour
            }
        } else {
            reason += "Location not shared."
        }

        var sample: List<Show> = emptyList()
        if (tourName != null) {
            val shows = tourShows ?: run {
                progress("Getting setlists from the \"$tourName\" tour…")
                api.searchSetlists(artist.mbid, tourName = tourName).filter { it.tour.equals(tourName, ignoreCase = true) }
            }
            sample = Planner.recentWithSongs(shows, today)
            reason += if (sample.isEmpty()) {
                "That tour has no setlists yet, so these come from ${artist.name}'s most recent shows."
            } else {
                "Predicted from the latest shows of that tour."
            }
        }

        if (sample.isEmpty()) {
            progress("Getting ${artist.name}'s most recent setlists…")
            sample = Planner.recentWithSongs(api.artistSetlists(artist.mbid, 1), today)
            if (sample.isEmpty()) sample = Planner.recentWithSongs(api.artistSetlists(artist.mbid, 2), today)
            if (sample.isEmpty()) throw UserError("setlist.fm has no recent setlists with songs for ${artist.name} yet.")
            if (tourName == null) reason += "Predicted from ${artist.name}'s latest shows."
        }

        val songs = Planner.buildSetlist(sample, artist.name)
        val tour = sample.first().tour ?: tourName
        return Plan(artist.name, tour, reason.joinToString(" "), sample, songs)
    }

    /** Finds the artist's tour whose name contains [q] (ignoring case and punctuation). */
    private fun findTour(artist: Artist, q: String): Pair<String, List<Show>> {
        progress("Looking for the tour \"$q\"…")
        val want = loose(q)
        fun matches(s: Show) = s.tour?.let { loose(it).contains(want) } == true

        var shows = api.searchSetlists(artist.mbid, tourName = q).filter(::matches)
        val fromTourSearch = shows.isNotEmpty()
        if (shows.isEmpty()) {
            // setlist.fm's tour search wants the exact name; look through recent shows for a partial match.
            for (page in 1..3) {
                val found = api.artistSetlists(artist.mbid, page)
                shows = found.filter(::matches)
                if (shows.isNotEmpty() || found.isEmpty()) break
            }
        }
        if (shows.isEmpty()) {
            throw UserError(
                "Couldn't find a ${artist.name} tour matching \"$q\" on setlist.fm. " +
                    "Check the name, or leave the tour empty and the app will work it out.",
            )
        }
        val name = shows.maxBy { it.date }.tour!!
        if (!fromTourSearch || !name.equals(q, ignoreCase = true)) {
            progress("Getting setlists from the \"$name\" tour…")
            val all = api.searchSetlists(artist.mbid, tourName = name).filter { it.tour.equals(name, ignoreCase = true) }
            if (all.isNotEmpty()) shows = all
        }
        return name to shows.filter { it.tour.equals(name, ignoreCase = true) }
    }
}
