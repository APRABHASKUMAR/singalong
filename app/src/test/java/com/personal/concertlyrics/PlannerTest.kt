package com.personal.concertlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Made-up artists, venues and songs only. */
class PlannerTest {
    private val today = LocalDate.of(2026, 11, 16)

    private fun show(
        id: String,
        date: LocalDate,
        city: String = "Testville",
        lat: Double? = null,
        lon: Double? = null,
        tour: String? = "Test Tour",
        songs: List<String> = emptyList(),
        tapes: List<String> = emptyList(),
    ) = Show(
        id, date, "Venue $id", city, "IN", "India", lat, lon, tour,
        tapes.map { SongEntry(it, null, true, null) } + songs.map { SongEntry(it, null, false, null) },
        "",
    )

    @Test fun picksNearestUpcomingShowOverRecentOnes() {
        val shows = listOf(
            show("past-here", today.minusDays(10), lat = 12.97, lon = 77.59),
            show("future-far", today.plusDays(20), lat = 19.07, lon = 72.87, tour = "Far Leg"),
            show("future-near", today.plusDays(40), lat = 12.95, lon = 77.60, tour = "Near Leg"),
        )
        val pick = Planner.pickLocalShow(shows, today, 12.97, 77.59, "Bengaluru")
        assertEquals("future-near", pick?.id)
    }

    @Test fun fallsBackToRecentShowWhenNoUpcoming() {
        val shows = listOf(
            show("old", today.minusDays(400)),
            show("recent", today.minusDays(30)),
        )
        assertEquals("recent", Planner.pickLocalShow(shows, today, null, null, "Testville")?.id)
    }

    @Test fun ignoresShowsOlderThanSixMonths() {
        assertNull(Planner.pickLocalShow(listOf(show("old", today.minusDays(400))), today, null, null, "x"))
    }

    @Test fun cityMatchingHandlesAliases() {
        assertTrue(Planner.cityMatches("Bangalore", "Bengaluru"))
        assertTrue(Planner.cityMatches("Navi Mumbai", "Mumbai"))
        assertFalse(Planner.cityMatches("Pune", "Chennai"))
    }

    @Test fun recentWithSongsSkipsFutureAndEmptyShows() {
        val shows = listOf(
            show("future", today.plusDays(5), songs = listOf("A", "B", "C")),
            show("empty", today.minusDays(1)),
            show("ok2", today.minusDays(3), songs = listOf("A", "B", "C")),
            show("ok1", today.minusDays(2), songs = listOf("A", "B", "C")),
        )
        assertEquals(listOf("ok1", "ok2"), Planner.recentWithSongs(shows, today).map { it.id })
    }

    @Test fun setlistUsesNewestFullShowOrderAndCountsPlays() {
        val sample = listOf(
            // Newest show was cut short: should not be the base.
            show("short", today.minusDays(1), songs = listOf("Opener", "Hit")),
            show("full", today.minusDays(3), songs = listOf("Opener", "Ballad", "Hit", "Closer"), tapes = listOf("Intro Tape")),
            show("older", today.minusDays(5), songs = listOf("Opener", "Rarity", "Hit", "Closer")),
        )
        val list = Planner.buildSetlist(sample, "Test Band")
        assertEquals(listOf("Opener", "Ballad", "Hit", "Closer", "Rarity"), list.map { it.title })
        assertEquals(3, list.first { it.title == "Opener" }.playedAt)
        assertEquals(1, list.first { it.title == "Ballad" }.playedAt)
        assertTrue(list.first { it.title == "Rarity" }.sometimes)
        assertFalse(list.any { it.title == "Intro Tape" })
        assertTrue(list.all { it.outOf == 3 })
    }

    @Test fun keepsRepeatedNotesButDropsOneNightTrivia() {
        fun s(id: String, days: Long, stageNote: String?, trivia: String?) = Show(
            id, today.minusDays(days), "V", "C", "IN", "India", null, null, "T",
            listOf(
                SongEntry("Stage Song", null, false, stageNote),
                SongEntry("Big Hit", null, false, trivia),
                SongEntry("Other", null, false, null),
            ),
            "",
        )
        val list = Planner.buildSetlist(
            listOf(s("a", 1, "on B-stage", "band gave a cake to the drummer"), s("b", 2, "on B-stage", null), s("c", 3, null, null)),
            "Test Band",
        )
        assertEquals("on B-stage", list.first { it.title == "Stage Song" }.note)
        assertNull(list.first { it.title == "Big Hit" }.note)
    }

    @Test fun coversLookUpTheOriginalArtistsLyrics() {
        val s = Show(
            "c", today.minusDays(1), "V", "C", "IN", "India", null, null, null,
            listOf(SongEntry("Old Song", "Original Group", false, "acoustic"), SongEntry("A", null, false, null), SongEntry("B", null, false, null)),
            "",
        )
        val first = Planner.buildSetlist(listOf(s), "Test Band").first()
        assertEquals("Original Group", first.lyricsArtist)
        assertEquals("Original Group", first.coverOf)
        assertEquals("acoustic", first.note)
    }
}
