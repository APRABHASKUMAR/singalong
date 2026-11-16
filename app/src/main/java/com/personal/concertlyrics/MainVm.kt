package com.personal.concertlyrics

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class ConcertView(val id: String) : Screen
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey by mutableStateOf(prefs.getString("apiKey", "").orEmpty()); private set
    var location by mutableStateOf(loadManualLocation()); private set
    var locating by mutableStateOf(false); private set
    var concerts by mutableStateOf(Storage.loadAll(app)); private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1f)); private set

    private var job: Job? = null
    private val showDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    val isManualLocation get() = location?.source == "manual"

    fun saveKey(key: String) {
        apiKey = key.trim()
        prefs.edit().putString("apiKey", apiKey).apply()
    }

    fun changeTextScale(delta: Float) {
        textScale = (textScale + delta).coerceIn(0.8f, 2f)
        prefs.edit().putFloat("textScale", textScale).apply()
    }

    private fun loadManualLocation(): UserLocation? {
        val city = prefs.getString("manualCity", null) ?: return null
        val cc = prefs.getString("manualCountry", null)
        return UserLocation(city, cc, null, null, null, "manual")
    }

    fun setManualLocation(city: String, countryCode: String) {
        val cc = countryCode.trim().uppercase().takeIf { it.length == 2 }
        prefs.edit().putString("manualCity", city.trim()).putString("manualCountry", cc).apply()
        location = UserLocation(city.trim(), cc, null, null, null, "manual")
        locationUnavailable = false
    }

    /** Forget a hand-typed location and detect it again. */
    fun autoDetectLocation() {
        prefs.edit().remove("manualCity").remove("manualCountry").apply()
        location = null
        detectLocation()
    }

    /** True when the user refused location permission (or the phone couldn't find a location). */
    var locationUnavailable by mutableStateOf(false); private set

    fun locationRefused() {
        if (!isManualLocation) locationUnavailable = true
    }

    fun detectLocation() {
        if (isManualLocation || locating) return
        locating = true
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Locator.detect(ctx) }
            if (found != null) location = found
            locationUnavailable = found == null
            locating = false
        }
    }

    fun search(query: String, tour: String = "") {
        if (query.isBlank() || busy) return
        if (apiKey.isBlank()) {
            error = "Add your free setlist.fm API key first (tap the gear icon)."
            return
        }
        error = null
        busy = true
        progress = "Starting…"
        val loc = location
        job = viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) {
                    Finder(SetlistFm(apiKey)) { msg -> viewModelScope.launch { progress = msg } }
                        .find(query, tour, loc, LocalDate.now())
                }
                val concert = Concert(
                    id = System.currentTimeMillis().toString(),
                    artist = plan.artist,
                    tour = plan.tour,
                    reason = plan.reason,
                    shows = plan.shows.map { "${it.date.format(showDate)} · ${it.venue}, ${it.city}" },
                    createdAt = System.currentTimeMillis(),
                    songs = plan.songs.map {
                        SavedSong(it.title, it.lyricsArtist, it.coverOf, it.playedAt, it.outOf, it.sometimes, it.note)
                    },
                )
                persist(concert)
                concerts = listOf(concert) + concerts
                screen = Screen.ConcertView(concert.id)
                downloadLyrics(concert.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                busy = false
                progress = ""
            }
        }
    }

    /** Re-try songs whose lyrics weren't found. */
    fun retryMissing(id: String) {
        if (busy) return
        busy = true
        error = null
        job = viewModelScope.launch {
            try {
                downloadLyrics(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                busy = false
                progress = ""
            }
        }
    }

    private suspend fun downloadLyrics(id: String) {
        val todo = concert(id)?.songs?.withIndex()?.filter { it.value.lyrics == null && !it.value.instrumental }
            ?.map { it.index } ?: return
        var done = 0
        for (chunk in todo.chunked(3)) {
            progress = "Downloading lyrics ${done + 1}–${minOf(done + chunk.size, todo.size)} of ${todo.size}…"
            val results = coroutineScope {
                chunk.map { i ->
                    val s = concert(id)!!.songs[i]
                    async(Dispatchers.IO) { i to findWithRetry(s.lyricsArtist, s.title) }
                }.awaitAll()
            }
            val c = concert(id) ?: return
            val songs = c.songs.toMutableList()
            for ((i, r) in results) {
                songs[i] = songs[i].copy(lyrics = r?.text, instrumental = r?.instrumental == true, looked = true)
            }
            update(c.copy(songs = songs))
            done += chunk.size
        }
    }

    /** Network hiccups are common on mobile data: try up to 3 times before giving up on a song. */
    private suspend fun findWithRetry(artist: String, title: String): Lyrics.Result? {
        repeat(3) { attempt ->
            try {
                return Lyrics.find(artist, title)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                kotlinx.coroutines.delay(1500L * (attempt + 1))
            }
        }
        return null
    }

    fun setLyrics(id: String, index: Int, text: String) {
        val c = concert(id) ?: return
        val songs = c.songs.toMutableList()
        songs[index] = songs[index].copy(lyrics = text.trim().ifBlank { null }, instrumental = false, looked = true)
        viewModelScope.launch { update(c.copy(songs = songs)) }
    }

    fun cancel() {
        job?.cancel()
        busy = false
        progress = ""
    }

    fun delete(id: String) {
        Storage.delete(ctx, id)
        concerts = concerts.filterNot { it.id == id }
        if ((screen as? Screen.ConcertView)?.id == id) screen = Screen.Home
    }

    fun concert(id: String) = concerts.firstOrNull { it.id == id }

    private suspend fun update(c: Concert) {
        concerts = concerts.map { if (it.id == c.id) c else it }
        persist(c)
    }

    private suspend fun persist(c: Concert) = withContext(Dispatchers.IO) { Storage.save(ctx, c) }

    private fun friendly(e: Exception): String = when (e) {
        is UserError -> e.message.orEmpty()
        is SetlistKeyException -> "setlist.fm didn't accept your API key. Check it in Settings (gear icon)."
        is UnknownHostException -> "No internet connection. Your saved concerts still work offline."
        is java.net.SocketTimeoutException -> "The connection timed out. Try again."
        else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
    }
}
