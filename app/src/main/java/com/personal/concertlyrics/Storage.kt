package com.personal.concertlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedSong(
    val title: String,
    val lyricsArtist: String,
    val coverOf: String?,
    val playedAt: Int,
    val outOf: Int,
    val sometimes: Boolean,
    val note: String?,
    val lyrics: String? = null,
    val instrumental: Boolean = false,
    /** True once we've tried to download lyrics (so "not found" can be told apart from "still loading"). */
    val looked: Boolean = false,
)

data class Concert(
    val id: String,
    val artist: String,
    val tour: String?,
    val reason: String,
    val shows: List<String>,
    val createdAt: Long,
    val songs: List<SavedSong>,
) {
    val mainSongs get() = songs.count { !it.sometimes }
    val withLyrics get() = songs.count { it.lyrics != null }
}

/** Saves each concert as a JSON file inside the app, so it can be read with no internet. */
object Storage {
    private fun dir(ctx: Context) = File(ctx.filesDir, "concerts").apply { mkdirs() }

    fun save(ctx: Context, c: Concert) {
        val songs = JSONArray()
        c.songs.forEach { s ->
            songs.put(
                JSONObject()
                    .put("title", s.title)
                    .put("lyricsArtist", s.lyricsArtist)
                    .put("coverOf", s.coverOf ?: JSONObject.NULL)
                    .put("playedAt", s.playedAt)
                    .put("outOf", s.outOf)
                    .put("sometimes", s.sometimes)
                    .put("note", s.note ?: JSONObject.NULL)
                    .put("lyrics", s.lyrics ?: JSONObject.NULL)
                    .put("instrumental", s.instrumental)
                    .put("looked", s.looked),
            )
        }
        val o = JSONObject()
            .put("id", c.id)
            .put("artist", c.artist)
            .put("tour", c.tour ?: JSONObject.NULL)
            .put("reason", c.reason)
            .put("shows", JSONArray(c.shows))
            .put("createdAt", c.createdAt)
            .put("songs", songs)
        val tmp = File(dir(ctx), "${c.id}.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(dir(ctx), "${c.id}.json"))
    }

    fun loadAll(ctx: Context): List<Concert> =
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { runCatching { parse(JSONObject(it.readText())) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
    }

    private fun parse(o: JSONObject): Concert {
        val arr = o.getJSONArray("songs")
        val songs = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            SavedSong(
                title = s.getString("title"),
                lyricsArtist = s.getString("lyricsArtist"),
                coverOf = s.str("coverOf"),
                playedAt = s.optInt("playedAt", 1),
                outOf = s.optInt("outOf", 1),
                sometimes = s.optBoolean("sometimes", false),
                note = s.str("note"),
                lyrics = if (s.isNull("lyrics")) null else s.getString("lyrics"),
                instrumental = s.optBoolean("instrumental", false),
                looked = s.optBoolean("looked", true),
            )
        }
        val shows = o.optJSONArray("shows") ?: JSONArray()
        return Concert(
            id = o.getString("id"),
            artist = o.getString("artist"),
            tour = o.str("tour"),
            reason = o.optString("reason"),
            shows = (0 until shows.length()).map { shows.getString(it) },
            createdAt = o.optLong("createdAt"),
            songs = songs,
        )
    }
}
