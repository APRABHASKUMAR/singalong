package com.personal.concertlyrics

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SetlistKeyException : Exception("setlist.fm rejected the API key")

/** Minimal client for the setlist.fm REST API (https://api.setlist.fm/docs/1.0/). */
class SetlistFm(private val apiKey: String) {
    private val base = "https://api.setlist.fm/rest/1.0"
    private var lastCall = 0L
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private fun call(path: String): JSONObject? {
        for (attempt in 0..2) {
            // Standard keys allow 2 requests per second.
            val wait = 600 - (System.currentTimeMillis() - lastCall)
            if (wait > 0) Thread.sleep(wait)
            lastCall = System.currentTimeMillis()
            try {
                val body = Http.get(
                    base + path,
                    mapOf("x-api-key" to apiKey, "Accept" to "application/json", "Accept-Language" to "en"),
                ) ?: return null
                return JSONObject(body)
            } catch (e: Http.HttpException) {
                when {
                    e.code == 401 || e.code == 403 -> throw SetlistKeyException()
                    e.code == 429 && attempt < 2 -> Thread.sleep(2000L * (attempt + 1))
                    else -> throw e
                }
            }
        }
        return null
    }

    fun searchArtists(name: String): List<Artist> {
        val o = call("/search/artists?artistName=${Http.enc(name.trim())}&sort=relevance&p=1") ?: return emptyList()
        val arr = o.optJSONArray("artist") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.getJSONObject(i)
            val mbid = a.str("mbid") ?: return@mapNotNull null
            Artist(mbid, a.str("name") ?: name, a.str("disambiguation").orEmpty())
        }
    }

    fun artistSetlists(mbid: String, page: Int = 1): List<Show> =
        parseShows(call("/artist/$mbid/setlists?p=$page"))

    fun searchSetlists(
        mbid: String,
        countryCode: String? = null,
        tourName: String? = null,
        page: Int = 1,
    ): List<Show> {
        val q = StringBuilder("/search/setlists?artistMbid=$mbid&p=$page")
        if (countryCode != null) q.append("&countryCode=${Http.enc(countryCode)}")
        if (tourName != null) q.append("&tourName=${Http.enc(tourName)}")
        return parseShows(call(q.toString()))
    }

    private fun parseShows(o: JSONObject?): List<Show> {
        val arr = o?.optJSONArray("setlist") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseShow(arr.getJSONObject(it)) }
    }

    private fun parseShow(o: JSONObject): Show? {
        val date = runCatching { LocalDate.parse(o.str("eventDate"), dateFormat) }.getOrNull() ?: return null
        val venue = o.optJSONObject("venue")
        val city = venue?.optJSONObject("city")
        val country = city?.optJSONObject("country")
        val coords = city?.optJSONObject("coords")

        val songs = mutableListOf<SongEntry>()
        val sets = o.optJSONObject("sets")?.optJSONArray("set")
        if (sets != null) {
            for (i in 0 until sets.length()) {
                val list = sets.getJSONObject(i).optJSONArray("song") ?: continue
                for (j in 0 until list.length()) {
                    val s = list.getJSONObject(j)
                    val name = s.str("name") ?: continue
                    songs += SongEntry(
                        name = name,
                        coverArtist = s.optJSONObject("cover")?.str("name"),
                        tape = s.optBoolean("tape", false),
                        info = s.str("info"),
                    )
                }
            }
        }
        return Show(
            id = o.str("id") ?: return null,
            date = date,
            venue = venue?.str("name").orEmpty(),
            city = city?.str("name").orEmpty(),
            countryCode = country?.str("code").orEmpty(),
            countryName = country?.str("name").orEmpty(),
            lat = coords?.dbl("lat"),
            lon = coords?.dbl("long"),
            tour = o.optJSONObject("tour")?.str("name"),
            songs = songs,
            url = o.str("url").orEmpty(),
        )
    }
}
