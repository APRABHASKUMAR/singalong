package com.personal.concertlyrics

import org.json.JSONArray
import org.json.JSONObject

/** Looks up lyrics on LRCLIB (https://lrclib.net), a free, open lyrics database. */
object Lyrics {
    private const val BASE = "https://lrclib.net/api"

    data class Result(val text: String?, val instrumental: Boolean)

    private val brackets = Regex("\\s*[(\\[].*?[)\\]]")

    private fun titleVariants(title: String): List<String> {
        val noBrackets = title.replace(brackets, "").trim()
        val firstOfMedley = title.split(" / ").first().trim()
        return listOf(title.trim(), noBrackets, firstOfMedley).filter { it.isNotBlank() }.distinct()
    }

    private fun usable(o: JSONObject): Result? = when {
        o.optBoolean("instrumental", false) -> Result(null, true)
        else -> o.str("plainLyrics")?.let { Result(it, false) }
    }

    private fun searchArray(url: String): JSONArray? = Http.get(url)?.let { JSONArray(it) }

    private fun loose(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun find(artist: String, title: String): Result {
        for (t in titleVariants(title)) {
            // 1. Exact match.
            Http.get("$BASE/get?artist_name=${Http.enc(artist)}&track_name=${Http.enc(t)}")
                ?.let { usable(JSONObject(it)) }
                ?.let { return it }

            // 2. Search by artist + title.
            searchArray("$BASE/search?track_name=${Http.enc(t)}&artist_name=${Http.enc(artist)}")?.let { arr ->
                for (i in 0 until arr.length()) usable(arr.getJSONObject(i))?.let { return it }
            }

            // 3. Free-text search, only accepting results by the same artist.
            searchArray("$BASE/search?q=${Http.enc("$artist $t")}")?.let { arr ->
                val want = loose(artist)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val a = loose(o.str("artistName").orEmpty())
                    if (a.isNotEmpty() && (a.contains(want) || want.contains(a))) {
                        usable(o)?.let { return it }
                    }
                }
            }
        }
        return Result(null, false)
    }
}
