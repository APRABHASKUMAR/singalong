package com.personal.concertlyrics

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    private const val USER_AGENT = "Singalong/1.0 (Android app)"

    class HttpException(val code: Int, message: String) : Exception(message)

    /** Returns the response body, or null for 404 (setlist.fm and LRCLIB use 404 for "no results"). */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw HttpException(code, "HTTP $code ${body?.take(200).orEmpty()}")
            }
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** org.json's optString returns the text "null" for JSON nulls; this returns a real null. */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

fun JSONObject.dbl(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
