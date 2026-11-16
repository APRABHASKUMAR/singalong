package com.personal.concertlyrics

import java.time.LocalDate

/** An artist as setlist.fm knows it. */
data class Artist(val mbid: String, val name: String, val disambiguation: String)

/** One entry in a setlist. "Tape" entries are recorded intros/outros, not songs sung live. */
data class SongEntry(
    val name: String,
    val coverArtist: String?,
    val tape: Boolean,
    val info: String?,
)

/** One concert (past or upcoming) from setlist.fm. Upcoming shows have no songs yet. */
data class Show(
    val id: String,
    val date: LocalDate,
    val venue: String,
    val city: String,
    val countryCode: String,
    val countryName: String,
    val lat: Double?,
    val lon: Double?,
    val tour: String?,
    val songs: List<SongEntry>,
    val url: String,
) {
    val liveSongs: List<SongEntry> get() = songs.filter { !it.tape }
}

/** Where the user is. Coordinates are optional (missing when the user typed a city by hand). */
data class UserLocation(
    val city: String,
    val countryCode: String?,
    val countryName: String?,
    val lat: Double?,
    val lon: Double?,
    val source: String,
) {
    val label: String
        get() = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            (countryName ?: countryCode)?.takeIf { it.isNotBlank() },
        ).joinToString(", ").ifBlank { "unknown location" }
}

/** A song in the predicted setlist. */
data class PlannedSong(
    val title: String,
    /** Whose lyrics to look up (the original artist for covers). */
    val lyricsArtist: String,
    val coverOf: String?,
    val playedAt: Int,
    val outOf: Int,
    /** True for songs played at some recent shows but not in the main predicted set. */
    val sometimes: Boolean,
    val note: String?,
)

data class Plan(
    val artist: String,
    val tour: String?,
    val reason: String,
    val shows: List<Show>,
    val songs: List<PlannedSong>,
)

/** An error with a message that is fine to show the user as-is. */
class UserError(message: String) : Exception(message)
